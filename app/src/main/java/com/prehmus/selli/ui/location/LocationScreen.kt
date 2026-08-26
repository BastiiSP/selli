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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.prehmus.selli.data.location.SelliLocationService
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
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Der Systemdialog läuft außerhalb der Komposition — der Berechtigungsstand wird deshalb
    // bei jedem Zurückkommen in den Vordergrund neu gelesen, nicht nur einmal beim Aufbau.
    var permissionStep by remember { mutableStateOf(context.locationPermissionStep()) }
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionStep = context.locationPermissionStep()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
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

    Box(modifier = modifier.fillMaxSize()) {
        when {
            !uiState.isConfigured -> LocationEmptyHint(
                message = "Standortfreigabe ist noch nicht eingerichtet",
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
