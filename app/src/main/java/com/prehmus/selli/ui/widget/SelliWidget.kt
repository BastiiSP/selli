package com.prehmus.selli.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.prehmus.selli.MainActivity
import com.prehmus.selli.R
import com.prehmus.selli.data.widget.WidgetSnapshotStore
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.FreeSlot
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.WidgetSnapshot
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Homescreen-Widget: zeigt ohne App-Öffnen, was der/die Partner:in als
 * Nächstes vorhat. Datenversorgung und Hintergrundaktualisierung liefert
 * data/widget (Owner Codex); hier lebt nur die Glance-Darstellung im
 * Selli-Look (warme Karte, Personenfarben-Punkt, Maskottchen-Leerzustand).
 *
 * Bewusste Einschränkung: Glance/RemoteViews können die Nunito-Schrift nicht
 * laden — das Widget nutzt die Systemschrift.
 */
class SelliWidget : GlanceAppWidget() {

    // Exact statt Responsive: Responsive rundet nur auf eine von fest deklarierten Größen —
    // trifft ein Launcher beim Ziehen nie genau eine davon, bleibt die Anzeige auf der falschen
    // Stufe hängen (live bei Basti beobachtet: reale Größe war deutlich über "Mittel", zeigte
    // aber weiterhin nur zwei Zeilen mit viel Leerraum darunter). Exact liefert die echte
    // aktuelle Höhe, wir entscheiden selbst per Schwellenwert — funktioniert unabhängig davon,
    // in welchen Schritten ein Launcher tatsächlich rastert.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotStore(context).load()
        val today = LocalDate.now()
        provideContent {
            WidgetCard(snapshot = snapshot, today = today)
        }
    }
}

// Pendants zu ui/theme/Color.kt als Tag/Nacht-Farbpaare für Glance.
private val TextPrimary = ColorProvider(day = Color(0xFF3B3439), night = Color(0xFFF1EBE7))
private val TextSoft = ColorProvider(day = Color(0xFF776E74), night = Color(0xFFB5ABB1))

// Schwellenwerte statt fester Ziel-Größen: Mit SizeMode.Exact bekommen wir die echte aktuelle
// Höhe, egal in welchen Schritten ein Launcher rastert. Grober Bedarf pro Zeile: eine Zeile
// (Avatar-Layout) ~55dp, jede weitere Zeile (Icon-Layout + 8dp Abstand) ~53dp, plus 24dp
// vertikales Innenpolster der Karte. Schwellen bewusst mit Puffer über dem reinen Minimum,
// damit eine Zeile nicht schon bei einem winzigen Größenzuwachs unschön abgeschnitten reinpasst.
private val MEDIUM_HEIGHT_THRESHOLD = 130.dp
private val LARGE_HEIGHT_THRESHOLD = 190.dp

private enum class WidgetTier { SMALL, MEDIUM, LARGE }

@Composable
private fun WidgetCard(snapshot: WidgetSnapshot?, today: LocalDate) {
    val currentSize = LocalSize.current
    val widgetTier = when {
        currentSize.height >= LARGE_HEIGHT_THRESHOLD -> WidgetTier.LARGE
        currentSize.height >= MEDIUM_HEIGHT_THRESHOLD -> WidgetTier.MEDIUM
        else -> WidgetTier.SMALL
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        if (snapshot == null) {
            FullCardEmptyHint(
                mascot = R.drawable.mascot_idle,
                title = "Selli ist noch nicht verbunden",
                subtitle = "Öffne die App und melde dich an.",
            )
            return@Box
        }

        val allIntendedRowsEmpty = when (widgetTier) {
            WidgetTier.SMALL -> snapshot.nextEvent == null
            WidgetTier.MEDIUM -> snapshot.nextEvent == null && snapshot.nextSharedEvent == null
            WidgetTier.LARGE ->
                snapshot.nextEvent == null &&
                    snapshot.nextSharedEvent == null &&
                    snapshot.nextFreeSlot == null
        }
        if (allIntendedRowsEmpty) {
            FullCardEmptyHint(
                mascot = R.drawable.mascot_empty_state,
                title = "${snapshot.partnerDisplayName} hat nichts Anstehendes",
                subtitle = "Vielleicht Zeit für euch beide?",
            )
            return@Box
        }

        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment =
                if (widgetTier == WidgetTier.SMALL) {
                    Alignment.CenterVertically
                } else {
                    Alignment.Top
                },
        ) {
            if (snapshot.nextEvent == null) {
                EmptyHint(
                    mascot = R.drawable.mascot_empty_state,
                    title = "${snapshot.partnerDisplayName} hat nichts Anstehendes",
                    subtitle = "Vielleicht Zeit für euch beide?",
                )
            } else {
                NextEventContent(
                    partnerPerson = snapshot.partnerPerson,
                    partnerName = snapshot.partnerDisplayName,
                    event = snapshot.nextEvent,
                    today = today,
                )
            }

            if (widgetTier != WidgetTier.SMALL) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                NextSharedEventRow(event = snapshot.nextSharedEvent, today = today)
            }

            if (widgetTier == WidgetTier.LARGE) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                NextFreeSlotRow(slot = snapshot.nextFreeSlot, today = today)
            }
        }
    }
}

@Composable
private fun FullCardEmptyHint(mascot: Int, title: String, subtitle: String) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmptyHint(
            mascot = mascot,
            title = title,
            subtitle = subtitle,
        )
    }
}

@Composable
private fun NextEventContent(
    partnerPerson: Person,
    partnerName: String,
    event: CalendarEvent,
    today: LocalDate,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(
                if (partnerPerson == Person.MELLI) R.drawable.avatar_melli else R.drawable.avatar_basti,
            ),
            contentDescription = partnerName,
            modifier = GlanceModifier.size(44.dp),
        )
        Spacer(modifier = GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(
                        if (partnerPerson == Person.MELLI) R.drawable.widget_dot_melli else R.drawable.widget_dot_basti,
                    ),
                    contentDescription = null,
                    modifier = GlanceModifier.size(8.dp),
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "$partnerName als Nächstes",
                    style = TextStyle(color = TextSoft, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = event.title,
                style = TextStyle(color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Text(
                text = formatEventTime(event, today),
                style = TextStyle(color = TextSoft, fontSize = 13.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NextSharedEventRow(event: CalendarEvent?, today: LocalDate) {
    if (event == null) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(R.drawable.mascot_empty_state),
                contentDescription = null,
                modifier = GlanceModifier.size(32.dp),
            )
            Spacer(modifier = GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "Nächste Wir-Zeit",
                    style = TextStyle(color = TextSoft, fontSize = 12.sp),
                    maxLines = 1,
                )
                Text(
                    text = "Nichts in Sicht",
                    style = TextStyle(color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
        }
    } else {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.widget_dot_together),
                    contentDescription = null,
                    modifier = GlanceModifier.size(8.dp),
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "Nächste Wir-Zeit",
                    style = TextStyle(color = TextSoft, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = event.title,
                style = TextStyle(color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Text(
                text = formatEventTime(event, today),
                style = TextStyle(color = TextSoft, fontSize = 13.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NextFreeSlotRow(slot: FreeSlot?, today: LocalDate) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(
                if (slot == null) R.drawable.mascot_empty_state else R.drawable.mascot_celebrating,
            ),
            contentDescription = null,
            modifier = GlanceModifier.size(32.dp),
        )
        Spacer(modifier = GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = "Nächster freier Slot",
                style = TextStyle(color = TextSoft, fontSize = 12.sp),
                maxLines = 1,
            )
            Text(
                text = if (slot == null) "Nichts in Sicht" else formatFreeSlot(slot, today),
                style = TextStyle(color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EmptyHint(mascot: Int, title: String, subtitle: String) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(mascot),
            contentDescription = null,
            modifier = GlanceModifier.size(44.dp),
        )
        Spacer(modifier = GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = title,
                style = TextStyle(color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 2,
            )
            Text(
                text = subtitle,
                style = TextStyle(color = TextSoft, fontSize = 12.sp),
                maxLines = 1,
            )
        }
    }
}

private val dayFormatter = DateTimeFormatter.ofPattern("EEE, dd.MM.", Locale.GERMAN)
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
private val freeSlotTimeFormatter = DateTimeFormatter.ofPattern("H:mm", Locale.GERMAN)

private fun dayLabel(day: LocalDate, today: LocalDate): String =
    when (day) {
        today -> "Heute"
        today.plusDays(1) -> "Morgen"
        else -> day.format(dayFormatter)
    }

/** "Heute 18:00–20:00", "Morgen ganztägig", "Mi, 22.07. 14:00–15:00". */
internal fun formatEventTime(event: CalendarEvent, today: LocalDate): String {
    val label = dayLabel(event.start.toLocalDate(), today)
    return if (event.isAllDay) {
        "$label ganztägig"
    } else {
        "$label ${event.start.format(timeFormatter)}–${event.end.format(timeFormatter)}"
    }
}

/** "Heute 14:00–18:00", "Morgen 9:00–13:00", "Mi, 05.08. 9:00–22:00". */
internal fun formatFreeSlot(slot: FreeSlot, today: LocalDate): String =
    "${dayLabel(slot.day, today)} " +
        "${slot.block.start.format(freeSlotTimeFormatter)}–${slot.block.end.format(freeSlotTimeFormatter)}"
