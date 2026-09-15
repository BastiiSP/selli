package com.prehmus.selli.ui.ideen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.prehmus.selli.domain.model.NoteItem

/**
 * Ein einzelner Punkt innerhalb eines aufgeklappten Ordners.
 *
 * Bringt bewusst keine eigene [Surface] mit: Seit die Ordner direkt in der Ideen-Übersicht
 * aufklappen (statt auf einer eigenen Unterseite), sitzen die Punkte innerhalb der
 * Ordnerkarte — die trägt Rundung, Farbe und Schatten. Eine zweite Karte darin würde die
 * Liste optisch verschachteln.
 */
@Composable
internal fun NoteItemCard(
    item: NoteItem,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Ohne Beschriftung liest TalkBack bei mehreren Punkten hintereinander nur
            // „nicht aktiviert" vor, ohne Bezug zum danebenstehenden Text.
            Checkbox(
                checked = item.isChecked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.semantics { contentDescription = "Erledigt: ${item.text}" },
            )
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                // Erledigte Punkte bleiben lesbar, treten aber sichtbar zurück.
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                color = if (item.isChecked) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(vertical = 12.dp),
            )
        }
        item.url?.takeUnless(String::isBlank)?.let { url ->
            LinkPreviewCard(
                url = url,
                title = item.previewTitle,
                imageUrl = item.previewImageUrl,
                modifier = Modifier
                    .padding(start = 48.dp, end = 8.dp, bottom = 8.dp)
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        runCatching { context.startActivity(intent) }
                    },
            )
        }
    }
}

/**
 * Vorschau eines verlinkten Punkts. Das Bild kommt aus `note_items.preview_image_url`
 * (beim Anlegen einmalig extrahiert) — fehlt es, bleibt die Zeile einzeilig mit Titel
 * bzw. Hostname.
 */
@Composable
private fun LinkPreviewCard(
    url: String,
    title: String?,
    imageUrl: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface),
                )
            }
            Text(
                text = title ?: shortenUrlForDisplay(url),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private fun shortenUrlForDisplay(url: String): String =
    runCatching { java.net.URI(url).host ?: url }.getOrDefault(url)

/** Anlegen und Bearbeiten eines Punkts — [onDelete] nur beim Bearbeiten gesetzt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteItemSheet(
    title: String,
    initialText: String,
    initialUrl: String,
    isSaving: Boolean,
    onSave: (text: String, url: String?) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var text by remember { mutableStateOf(initialText) }
    var url by remember { mutableStateOf(initialUrl) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Text") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Link (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSave(text, url.takeUnless(String::isBlank)) },
                enabled = text.isNotBlank() && !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (isSaving) "Wird gespeichert …" else "Speichern") }
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
