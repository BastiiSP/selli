package com.prehmus.selli.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.places.LocationSuggestion
import com.prehmus.selli.domain.repository.PlaceSuggestionRepository
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Ab so vielen Zeichen lohnt sich eine Abfrage — darunter kommt ohnehin nur Rauschen zurück. */
private const val MinQueryLength = 3

/** Tipppause, bevor gesucht wird: schont Kontingent und lässt die Liste nicht flackern. */
private const val SuggestionDebounceMillis = 350L

/** Mehr Einträge lassen das Bottom Sheet springen — fünf reichen für eine Adresse. */
private const val MaxSuggestions = 5

/**
 * Nach dem Fokusverlust noch kurz stehen lassen: so kommt ein Tap auf einen
 * Vorschlag auch dann an, wenn das Textfeld den Fokus vorher abgibt.
 */
private const val DismissGraceMillis = 200L

/**
 * Ortsfeld mit weichen Live-Adressvorschlägen. Verhält sich ohne
 * [suggestionRepository] (oder wenn nichts zurückkommt) exakt wie ein normales
 * Textfeld — frei tippen geht immer, das Feld blockiert nie das Speichern.
 *
 * Die Vorschläge selbst holt die Logik-Seite (Owner Codex); [PlaceSuggestionRepository]
 * wirft nie und liefert bei fehlendem Schlüssel oder Netzfehler einfach eine leere Liste.
 */
@Composable
fun LocationAutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestionRepository: PlaceSuggestionRepository?,
    modifier: Modifier = Modifier,
    label: String = "Ort (optional)",
) {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val currentValue by rememberUpdatedState(value)
    var suggestions by remember { mutableStateOf<List<LocationSuggestion>>(emptyList()) }
    var isFocused by remember { mutableStateOf(false) }
    // Nach einer Auswahl steht der Vorschlag selbst im Feld — dafür nicht erneut suchen.
    var acceptedValue by remember { mutableStateOf<String?>(null) }
    // Alle Tastendrücke bis zur Auswahl gehören zu einer Sitzung — Google rechnet sie
    // dann als einen Vorgang ab. Nach jeder Auswahl beginnt eine neue Sitzung.
    var sessionToken by remember { mutableStateOf(UUID.randomUUID().toString()) }

    // Neuer Tastendruck bricht die vorherige Abfrage ab (LaunchedEffect startet neu).
    LaunchedEffect(value, suggestionRepository) {
        val query = value.trim()
        if (suggestionRepository == null || query.length < MinQueryLength || value == acceptedValue) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(SuggestionDebounceMillis)
        suggestions = suggestionRepository.suggest(query, sessionToken).take(MaxSuggestions)
    }

    LaunchedEffect(isFocused) {
        if (!isFocused) {
            delay(DismissGraceMillis)
            suggestions = emptyList()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { typed ->
                acceptedValue = null
                onValueChange(typed)
            },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .scrollIntoViewOnFocus()
                .onFocusChanged { state -> isFocused = state.isFocused },
        )

        AnimatedVisibility(
            visible = suggestions.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 3.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    suggestions.forEach { suggestion ->
                        SuggestionRow(
                            suggestion = suggestion,
                            onClick = {
                                // Sofort den Vorschlagstext übernehmen, damit sich nichts
                                // hakt. Die vollständige Anschrift kommt einen Moment
                                // später nach — schlägt das fehl, bleibt der Vorschlag.
                                val token = sessionToken
                                acceptedValue = suggestion.fullText
                                onValueChange(suggestion.fullText)
                                suggestions = emptyList()
                                focusManager.clearFocus()
                                sessionToken = UUID.randomUUID().toString()

                                val repository = suggestionRepository
                                if (repository != null) {
                                    scope.launch {
                                        val resolved =
                                            repository.resolveFullAddress(suggestion.placeId, token)
                                        // Nur übernehmen, wenn der Nutzer inzwischen nicht
                                        // selbst weitergetippt hat.
                                        if (!resolved.isNullOrBlank() &&
                                            currentValue == suggestion.fullText
                                        ) {
                                            acceptedValue = resolved
                                            onValueChange(resolved)
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Ein Vorschlag: Ortsnadel, fetter Hauptname, darunter die feinere Adresszeile. */
@Composable
private fun SuggestionRow(
    suggestion: LocationSuggestion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.primaryText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            suggestion.secondaryText?.takeIf { it.isNotBlank() }?.let { secondary ->
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
