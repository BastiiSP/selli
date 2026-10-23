package com.prehmus.selli.ui.location

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.prehmus.selli.R
import com.prehmus.selli.domain.model.LocationFreshness
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.PersonLocation
import com.prehmus.selli.domain.model.freshness
import com.prehmus.selli.domain.model.isImprecise
import com.prehmus.selli.domain.model.isLiveSharing
import com.prehmus.selli.ui.theme.onAccentColor
import com.prehmus.selli.ui.theme.personColor
import java.time.Instant

/**
 * Die Karte im Selli-Look: warme, gedämpfte Kartenstile (hell/dunkel folgen dem System-Theme)
 * und je Person ein Marker mit dem gezeichneten Avatar-Gesicht in der Personenfarbe. Unter dem
 * Marker stehen Modus (LIVE) und das echte Messalter; veraltete Positionen werden entsättigt
 * beschriftet, ungenaue bekommen einen Genauigkeitskreis.
 *
 * Bewusst nicht enthalten: kein Schalter für die Standortfreigabe, keine Entfernungsanzeige.
 */
@Composable
fun LocationMap(
    locations: List<PersonLocation>,
    now: Instant,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val styleOptions = remember(darkTheme) {
        MapStyleOptions.loadRawResourceStyle(
            context,
            if (darkTheme) R.raw.map_style_selli_night else R.raw.map_style_selli,
        )
    }
    val cameraPositionState = rememberCameraPositionState {
        // Startausschnitt zwischen Kassel und Osnabrück – wird beim ersten Datensatz ersetzt.
        position = CameraPosition.fromLatLngZoom(LatLng(51.7, 9.2), 7f)
    }
    var hasFramedOnce by remember { mutableStateOf(false) }

    // Nur EINMAL automatisch einpassen. Danach nie mehr: sonst reißt es dem Nutzer die Karte
    // bei jedem Positionsupdate unter den Fingern weg.
    LaunchedEffect(locations.isNotEmpty()) {
        if (hasFramedOnce || locations.isEmpty()) return@LaunchedEffect
        hasFramedOnce = true
        val points = locations.map { LatLng(it.latitude, it.longitude) }
        runCatching {
            if (points.size == 1) {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(points.first(), 12f))
            } else {
                val bounds = LatLngBounds.builder().apply { points.forEach(::include) }.build()
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120))
            }
        }
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        contentPadding = PaddingValues(bottom = bottomPadding),
        properties = MapProperties(mapStyleOptions = styleOptions),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
    ) {
        locations.forEach { location ->
            PersonMarker(location = location, now = now)
        }
    }
}

/**
 * Marker als Compose-Inhalt (`MarkerComposable`) statt als vorgerendertes Bitmap — so lässt
 * sich das fertige Pin-Asset direkt zeichnen, ohne BitmapDescriptor-Umweg.
 *
 * Der Anker sitzt bei 0,92 statt 1,0: die Pin-Spitze der Illustration liegt knapp über dem
 * unteren Bildrand (darunter ist der Schlagschatten).
 */
@Composable
private fun PersonMarker(location: PersonLocation, now: Instant) {
    val target = LatLng(location.latitude, location.longitude)
    val animated = animatedLatLng(target)
    // rememberUpdatedMarkerState zieht die Position selbst nach, sobald sich `animated` ändert.
    val markerState = rememberUpdatedMarkerState(position = animated)

    val freshness = location.freshness(now)
    val label = location.markerLabel(now)
    val imprecision = location.imprecisionLabel()
    val live = location.isLiveSharing(now)
    val color = personColor(location.person)

    // Genauigkeitskreis nur bei ungenauen Messungen: dort ist der Pin allein irreführend genau.
    val accuracy = location.accuracyMeters
    if (accuracy != null && location.isImprecise()) {
        Circle(
            center = animated,
            radius = accuracy,
            fillColor = color.copy(alpha = 0.12f),
            strokeColor = color.copy(alpha = 0.45f),
            strokeWidth = 2f,
        )
    }

    MarkerComposable(
        // MarkerComposable rendert nur bei geänderten Keys neu — die Beschriftung gehört dazu.
        keys = arrayOf(location.person, freshness, label, imprecision.orEmpty(), live),
        state = markerState,
        anchor = Offset(0.5f, 0.92f),
        title = location.person.displayName(),
        // Modus/Alter stehen sonst nur im Marker-Bitmap und wären für TalkBack unerreichbar.
        snippet = listOfNotNull(label, imprecision).joinToString(" · "),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(
                    if (location.person == Person.MELLI) {
                        R.drawable.location_pin_melli
                    } else {
                        R.drawable.location_pin_basti
                    },
                ),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
            FreshnessBadge(
                person = location.person,
                stale = freshness == LocationFreshness.STALE,
                label = label,
                imprecision = imprecision,
            )
        }
    }
}

/**
 * „LIVE · vor 10 s“ / „vor 8 Min.“ / „veraltet · vor 2 Std.“ — Alter statt Entfernung.
 * Veraltete Angaben stehen bewusst nicht in der kräftigen Personenfarbe, damit eine alte
 * Position nicht wie eine aktuelle wirkt.
 */
@Composable
private fun FreshnessBadge(
    person: Person,
    stale: Boolean,
    label: String,
    imprecision: String?,
) {
    Surface(
        color = if (stale) MaterialTheme.colorScheme.surfaceVariant else personColor(person),
        shape = CircleShape,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val textColor = if (stale) MaterialTheme.colorScheme.onSurface else onAccentColor()
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = textColor)
            if (imprecision != null) {
                Text(text = imprecision, style = MaterialTheme.typography.labelSmall, color = textColor)
            }
        }
    }
}

/**
 * Weicher Positionswechsel über 1,2 s. **Linear** statt mit Easing: eine Autofahrt verläuft
 * gleichmäßig, ein Ease-in-out würde an jedem Datenpunkt ein Bremsen und Anfahren
 * vortäuschen, das es nicht gibt.
 *
 * Interpoliert in `Double` (nicht über einen `TwoWayConverter` auf `AnimationVector2D`), weil
 * `Float` bei Geokoordinaten nur ~1 m Auflösung hat. Auch im Live-Modus liegen Uploads
 * mindestens 10 s auseinander, die vorige Animation ist also immer beendet — der alte Zielpunkt
 * ist damit der korrekte Startpunkt.
 */
@Composable
private fun animatedLatLng(target: LatLng): LatLng {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        from = to
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 1_200, easing = LinearEasing))
    }

    val fraction = progress.value
    return LatLng(
        from.latitude + (to.latitude - from.latitude) * fraction,
        from.longitude + (to.longitude - from.longitude) * fraction,
    )
}

/** Ruhiger Hinweis, wenn noch keine Position vorliegt — statt einer leeren grauen Fläche. */
@Composable
fun LocationEmptyHint(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.mascot_pondering),
                contentDescription = null,
                modifier = Modifier.size(112.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
