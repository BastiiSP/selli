package com.prehmus.selli.ui.location

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.prehmus.selli.data.location.SelliLocationService
import com.prehmus.selli.ui.components.CloudInitialLoading
import com.prehmus.selli.ui.components.CloudIssueBanner
import com.prehmus.selli.ui.components.CloudLoadFailedState
import com.prehmus.selli.ui.components.CloudReconfirmUi
import java.time.Instant
import kotlinx.coroutines.delay

/**
 * Der Standort-Tab: Karte mit beiden Positionen, darüber die Berechtigungsführung.
 *
 * Die eigene Position erfasst der Foreground-Service; dieser Screen startet ihn, sobald die
 * Vordergrund-Berechtigung vorliegt. Es gibt bewusst keinen Ein/Aus-Schalter — die Freigabe
 * ist Teil des Funktionsversprechens der App, nicht eine Einstellung.
 */
@Composable
fun LocationScreen(
    viewModel: LocationViewModel,
    reconfirm: CloudReconfirmUi,
    onReconfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Der Systemdialog und die Systemeinstellungen laufen außerhalb der Komposition — der
    // Berechtigungsstand wird deshalb bei jedem Zurückkommen in den Vordergrund neu gelesen,
    // nicht nur einmal beim Aufbau.
    var permissionStep by remember { mutableStateOf(context.locationPermissionStep()) }
    LifecycleResumeEffect(Unit) {
        permissionStep = context.locationPermissionStep()
        onPauseOrDispose { }
    }

    // Tracking läuft, sobald die Vordergrund-Freigabe da ist. Die Hintergrund-Freigabe macht es
    // nur zuverlässiger, ist aber keine Voraussetzung.
    LaunchedEffect(permissionStep, uiState.isConfigured) {
        if (uiState.isConfigured && context.hasForegroundLocationPermission()) {
            SelliLocationService.start(context)
        }
    }

    // Minütlicher Tick, damit die Frische-Beschriftung („vor 8 Min.") mitläuft.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = Instant.now()
        }
    }

    val loadIssue = uiState.loadIssue
    Box(modifier = modifier.fillMaxSize()) {
        when {
            !uiState.isConfigured -> LocationEmptyHint(
                message = "Standortfreigabe ist noch nicht eingerichtet",
                modifier = Modifier.fillMaxSize(),
            )
            // Noch nie erfolgreich geladen: Fehler bzw. Laden zeigen, nicht „keine Position".
            !uiState.hasLoaded && loadIssue != null -> CloudLoadFailedState(
                issue = loadIssue,
                subject = "Standorte",
                reconfirm = reconfirm,
                onRetry = viewModel::retry,
                onReconfirm = onReconfirm,
                modifier = Modifier.fillMaxSize(),
            )
            !uiState.hasLoaded -> CloudInitialLoading(
                subject = "Standorte",
                modifier = Modifier.fillMaxSize(),
            )
            uiState.locations.isEmpty() -> LocationEmptyHint(
                message = "Noch keine Position bekannt",
                modifier = Modifier.fillMaxSize(),
            )
            else -> LocationMap(
                locations = uiState.locations,
                now = now,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Bereits bekannte Positionen bleiben auf der Karte; die Karte oben erklärt nur, warum
        // sie gerade nicht frisch sind.
        if (uiState.hasLoaded && loadIssue != null) {
            CloudIssueBanner(
                issue = loadIssue,
                subject = "Standorte",
                reconfirm = reconfirm,
                onRetry = viewModel::retry,
                onReconfirm = onReconfirm,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
            )
        }

        if (uiState.isConfigured && permissionStep != LocationPermissionStep.GRANTED) {
            LocationPermissionCard(
                step = permissionStep,
                onGranted = { permissionStep = context.locationPermissionStep() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }
}
