package com.prehmus.selli.data.places

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.prehmus.selli.domain.logging.CalendarLogger
import com.prehmus.selli.domain.places.LocationSuggestion
import com.prehmus.selli.domain.repository.PlaceSuggestionRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Adressvorschläge über die Google Places API (New).
 *
 * Der Schlüssel ist in der Cloud Console auf diese Android-App beschränkt. Google erkennt
 * sie nur, wenn jede Anfrage Paketname und Signatur-Fingerabdruck mitschickt — fehlen
 * [androidPackageName] oder [androidCertSha1], antwortet die API mit 403 und die
 * Vorschlagsliste bleibt leer. Das Places-SDK setzt diese Header sonst selbst; hier wird
 * bewusst direkt per OkHttp gesprochen, um die zusätzliche Abhängigkeit zu sparen.
 */
class PlacesAutocompleteRepository(
    private val apiKey: String,
    private val androidPackageName: String? = null,
    private val androidCertSha1: String? = null,
    private val callFactory: okhttp3.Call.Factory = okhttp3.OkHttpClient(),
    private val logger: CalendarLogger,
    private val endpoint: okhttp3.HttpUrl =
        "https://places.googleapis.com/v1/places:autocomplete".toHttpUrl(),
    private val detailsEndpoint: okhttp3.HttpUrl =
        "https://places.googleapis.com/v1/places".toHttpUrl(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PlaceSuggestionRepository {

    override suspend fun suggest(query: String, sessionToken: String?): List<LocationSuggestion> {
        val trimmedQuery = query.trim()
        if (apiKey.isBlank() || trimmedQuery.length < MINIMUM_QUERY_LENGTH) {
            return emptyList()
        }

        return withContext(ioDispatcher) {
            try {
                val requestJson = gson.toJson(
                    AutocompleteRequest(
                        input = trimmedQuery,
                        languageCode = LANGUAGE_CODE,
                        regionCode = REGION_CODE,
                        sessionToken = sessionToken?.takeIf { token -> token.isNotBlank() },
                    ),
                )
                val request = Request.Builder()
                    .url(endpoint)
                    .withIdentityHeaders()
                    .post(requestJson.toByteArray().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                callFactory.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw PlacesHttpException("autocomplete", response.code)
                    }

                    val responseJson = response.body?.string()
                        ?: throw IOException("Places autocomplete returned an empty body")
                    val autocompleteResponse = gson.fromJson(
                        responseJson,
                        AutocompleteResponse::class.java,
                    ) ?: throw JsonParseException("Places autocomplete returned invalid JSON")

                    autocompleteResponse.suggestions.orEmpty()
                        .asSequence()
                        .mapNotNull { suggestion -> suggestion.toLocationSuggestionOrNull() }
                        .take(MAX_SUGGESTIONS)
                        .toList()
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                logger.error(LOG_SOURCE, exception)
                emptyList()
            }
        }
    }

    override suspend fun resolveFullAddress(placeId: String, sessionToken: String?): String? {
        val trimmedPlaceId = placeId.trim()
        if (apiKey.isBlank() || trimmedPlaceId.isEmpty()) {
            return null
        }

        return withContext(ioDispatcher) {
            try {
                val url = detailsEndpoint.newBuilder()
                    .addPathSegment(trimmedPlaceId)
                    .addQueryParameter("languageCode", LANGUAGE_CODE)
                    .apply {
                        sessionToken?.takeIf { token -> token.isNotBlank() }?.let { token ->
                            addQueryParameter("sessionToken", token)
                        }
                    }
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .withIdentityHeaders()
                    // Ohne Feldmaske liefert die Details-API alle (teureren) Felder —
                    // gebraucht wird hier nur die fertige Anschrift.
                    .header(FIELD_MASK_HEADER, "formattedAddress")
                    .get()
                    .build()

                callFactory.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw PlacesHttpException("details", response.code)
                    }

                    val responseJson = response.body?.string()
                        ?: throw IOException("Places details returned an empty body")
                    val details = gson.fromJson(responseJson, PlaceDetailsResponse::class.java)
                        ?: throw JsonParseException("Places details returned invalid JSON")

                    details.formattedAddress?.takeIf { address -> address.isNotBlank() }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                logger.error(LOG_SOURCE, exception)
                null
            }
        }
    }

    /**
     * Schlüssel plus — falls bekannt — die App-Identität. Nur mit beiden Werten akzeptiert
     * Google einen auf Android beschränkten Schlüssel.
     */
    private fun Request.Builder.withIdentityHeaders(): Request.Builder {
        header(API_KEY_HEADER, apiKey)
        val packageName = androidPackageName?.takeIf { value -> value.isNotBlank() }
        val certSha1 = androidCertSha1?.takeIf { value -> value.isNotBlank() }
        if (packageName != null && certSha1 != null) {
            header(ANDROID_PACKAGE_HEADER, packageName)
            header(ANDROID_CERT_HEADER, certSha1)
        }
        return this
    }

    private fun Suggestion.toLocationSuggestionOrNull(): LocationSuggestion? {
        val prediction = placePrediction ?: return null
        val fullText = prediction.text?.text?.takeIf { text -> text.isNotBlank() }
            ?: return null
        val primaryText = prediction.structuredFormat?.mainText?.text
            ?.takeIf { text -> text.isNotBlank() }
            ?: fullText

        return LocationSuggestion(
            // Fehlt die ID ausnahmsweise, bleibt der Vorschlag trotzdem brauchbar —
            // die Detailabfrage entfällt dann und es zählt fullText.
            placeId = prediction.placeId.orEmpty(),
            primaryText = primaryText,
            secondaryText = prediction.structuredFormat?.secondaryText?.text,
            fullText = fullText,
        )
    }

    private data class AutocompleteRequest(
        val input: String,
        val languageCode: String,
        val regionCode: String,
        val sessionToken: String?,
    )

    private data class AutocompleteResponse(
        val suggestions: List<Suggestion>?,
    )

    private data class Suggestion(
        val placePrediction: PlacePrediction?,
    )

    private data class PlacePrediction(
        val placeId: String?,
        val text: TextValue?,
        val structuredFormat: StructuredFormat?,
    )

    private data class StructuredFormat(
        val mainText: TextValue?,
        val secondaryText: TextValue?,
    )

    private data class TextValue(
        val text: String?,
    )

    private data class PlaceDetailsResponse(
        val formattedAddress: String?,
    )

    private class PlacesHttpException(
        stage: String,
        statusCode: Int,
    ) : IOException("Places $stage failed: HTTP $statusCode")

    private companion object {
        const val MINIMUM_QUERY_LENGTH = 3
        const val MAX_SUGGESTIONS = 5
        const val API_KEY_HEADER = "X-Goog-Api-Key"
        const val ANDROID_PACKAGE_HEADER = "X-Android-Package"
        const val ANDROID_CERT_HEADER = "X-Android-Cert"
        const val FIELD_MASK_HEADER = "X-Goog-FieldMask"
        const val LANGUAGE_CODE = "de"
        const val REGION_CODE = "DE"
        const val LOG_SOURCE = "Places autocomplete"

        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val gson = Gson()
    }
}
