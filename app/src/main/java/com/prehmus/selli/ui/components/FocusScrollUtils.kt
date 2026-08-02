package com.prehmus.selli.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusEvent
import kotlinx.coroutines.launch

/**
 * Scrollt das Element automatisch in den sichtbaren Bereich, sobald es den Fokus bekommt.
 *
 * In Bottom Sheets mit `verticalScroll` + `imePadding` reserviert die Tastatur zwar Platz,
 * ein einmal fokussiertes Feld springt dadurch aber nicht von selbst ins Bild — tiefer
 * liegende Felder (Ort, Beschreibung) landen sonst hinter der Tastatur und man muss von
 * Hand nachscrollen, um die eigene Eingabe zu sehen. Für Text-/Autocomplete-Felder in den
 * Termin-Sheets gedacht, bei einfachen Buttons/Zeilen ohne Tastatur-Interaktion unnötig.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.scrollIntoViewOnFocus(): Modifier = composed {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onFocusEvent { state ->
            if (state.isFocused) {
                scope.launch { bringIntoViewRequester.bringIntoView() }
            }
        }
}
