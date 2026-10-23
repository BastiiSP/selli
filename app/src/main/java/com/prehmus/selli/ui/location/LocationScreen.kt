package com.prehmus.selli.ui.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.prehmus.selli.data.location.SelliLocationService
import com.prehmus.selli.domain.location.validAt
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.isLiveSharing
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
 * Vordergrund-Berechtigung vorliegt. Es gibt bewusst keinen Ein/Aus-Schalter für den sparsamen
 * Normalbetrieb — die Freigabe ist Teil des Funktionsversprechens der App. Zusätzlich lässt
 * sich unten eine befristete Live-Freigabe starten und vorzeitig beenden.
 */
@Composable
fun LocationScreen(
    viewModel: LocationViewModel,
    ownPerson: Person,
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

    // Tick für Messalter und Restzeit: während irgendeiner Live-Freigabe alle 5 s (Sekunden-
    // Angaben, Ablauf ohne Verzögerung), sonst alle 30 s für „vor 8 Min.“ und „veraltet“.
    var now by remember { mutableStateOf(Instant.now()) }
    val anyOwnShare = uiState.ownLiveShare.share.validAt(now) != null
    val anyLive = anyOwnShare || uiState.locations.any { it.isLiveSharing(now) }
    LaunchedEffect(anyLive) {
        while (true) {
            now = Instant.now()
            delay(if (anyLive) 5_000 else 30_000)
        }
    }
    var showDurationSheet by remember { mutableStateOf(false) }
    // Höhe der unteren Karten, damit Google-Logo und Kartenmitte nicht darunter verschwinden.
    var bottomOverlayPx by remember { mutableIntStateOf(0) }
    val bottomOverlay = with(LocalDensity.current) { bottomOverlayPx.toDp() }

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
                bottomPadding = bottomOverlay,
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomOverlayPx = it.height }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.isConfigured && permissionStep != LocationPermissionStep.GRANTED) {
                LocationPermissionCard(
                    step = permissionStep,
                    onGranted = { permissionStep = context.locationPermissionStep() },
                )
            }
            // Eine laufende eigene Freigabe bleibt beendbar, auch wenn die Karte gerade nicht lädt.
            if (uiState.isConfigured && (uiState.hasLoaded || anyOwnShare)) {
                LiveSharePanel(
                    ownPerson = ownPerson,
                    ownState = uiState.ownLiveShare,
                    locations = uiState.locations,
                    now = now,
                    startProblem = uiState.liveStartProblem,
                    showBackgroundHint = permissionStep == LocationPermissionStep.BACKGROUND,
                    onStartRequest = { showDurationSheet = true },
                    onStop = viewModel::stopLiveShare,
                    onDismissProblem = viewModel::dismissLiveStartProblem,
                    onOpenSettings = context::openAppSettings,
                )
            }
        }
    }

    if (showDurationSheet) {
        LiveShareDurationSheet(
            ownPerson = ownPerson,
            onSelect = { duration ->
                showDurationSheet = false
                viewModel.startLiveShare(duration)
            },
            onDismiss = { showDurationSheet = false },
        )
    }
}
