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

    override val sizeMode: SizeMode = SizeMode.Single

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

@Composable
private fun WidgetCard(snapshot: WidgetSnapshot?, today: LocalDate) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        when {
            snapshot == null -> EmptyHint(
                mascot = R.drawable.mascot_idle,
                title = "Selli ist noch nicht verbunden",
                subtitle = "Öffne die App und melde dich an.",
            )
            snapshot.nextEvent == null -> EmptyHint(
                mascot = R.drawable.mascot_empty_state,
                title = "${snapshot.partnerDisplayName} hat nichts Anstehendes",
                subtitle = "Vielleicht Zeit für euch beide?",
            )
            else -> NextEventContent(
                partnerPerson = snapshot.partnerPerson,
                partnerName = snapshot.partnerDisplayName,
                event = snapshot.nextEvent,
                today = today,
            )
        }
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
        modifier = GlanceModifier.fillMaxSize(),
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
private fun EmptyHint(mascot: Int, title: String, subtitle: String) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
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

/** "Heute 18:00–20:00", "Morgen ganztägig", "Mi, 22.07. 14:00–15:00". */
internal fun formatEventTime(event: CalendarEvent, today: LocalDate): String {
    val startDay = event.start.toLocalDate()
    val dayLabel = when (startDay) {
        today -> "Heute"
        today.plusDays(1) -> "Morgen"
        else -> startDay.format(dayFormatter)
    }
    return if (event.isAllDay) {
        "$dayLabel ganztägig"
    } else {
        "$dayLabel ${event.start.format(timeFormatter)}–${event.end.format(timeFormatter)}"
    }
}
