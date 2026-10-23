package com.prehmus.selli.data.supabase

import com.prehmus.selli.domain.logging.CalendarLogger
import com.prehmus.selli.domain.model.CloudSyncException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException

fun classifyCloudError(error: Throwable): CloudSyncException {
    if (error is CancellationException) throw error
    if (error is CloudSyncException) return error
    return when {
        error is IOException || error is HttpRequestException ||
            error is HttpRequestTimeoutException || error is TimeoutException ->
            CloudSyncException.Offline(error)
        error is RestException && isCloudAuthenticationError(error) ->
            CloudSyncException.ReconfirmationRequired(error)
        else -> CloudSyncException.Failed(error)
    }
}

private fun isCloudAuthenticationError(error: RestException): Boolean {
    if (error.statusCode !in 400..499) return false
    if (error.statusCode == 401) return true
    // RLS/Grants-403 und gewöhnliche PostgREST-400 sind keine verlorene Anmeldung.
    val description = "${error.error} ${error.description.orEmpty()}"
    return error.response.call.request.url.encodedPath.startsWith("/auth/v1/") ||
        description.contains("Bad ID token", ignoreCase = true) ||
        description.contains("Invalid Refresh Token", ignoreCase = true) ||
        description.contains("JWT expired", ignoreCase = true)
}

internal fun needsSessionRetry(error: Throwable): Boolean =
    error is RestException && (
        error.statusCode == 401 ||
            (error.statusCode in 400..499 &&
                "${error.error} ${error.description.orEmpty()}".contains("JWT expired", ignoreCase = true))
        )

/** RestException.message enthält Request-Header. Deshalb niemals Original/Cause in Logs schreiben. */
internal fun logCloudError(logger: CalendarLogger, source: String, error: Throwable) {
    val classified = classifyCloudError(error)
    val safeError = when (classified) {
        is CloudSyncException.ReconfirmationRequired -> CloudSyncException.ReconfirmationRequired()
        is CloudSyncException.Offline -> CloudSyncException.Offline()
        is CloudSyncException.NotConfigured -> CloudSyncException.NotConfigured()
        is CloudSyncException.Failed -> CloudSyncException.Failed()
    }
    runCatching { logger.error(source, safeError) }
}
