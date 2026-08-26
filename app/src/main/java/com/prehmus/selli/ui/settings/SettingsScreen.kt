package com.prehmus.selli.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.model.Account
import com.prehmus.selli.ui.calendar.CalendarViewModel
import com.prehmus.selli.ui.components.CoupleAvatars
import com.prehmus.selli.ui.components.PersonAvatar
import com.prehmus.selli.ui.event.CustomizationManagerSheet
import com.prehmus.selli.ui.theme.onAccentColor
import com.prehmus.selli.ui.theme.selliGradient

/**
 * Profil und Einstellungen: eigenes Profilbild, verknüpftes Google-Konto und der Zugang zur
 * Verwaltung der lokalen Ausblendungen/Anpassungen. Bewusst schlicht und erweiterbar —
 * mehr braucht Selli hier aktuell nicht.
 *
 * „Ausgeblendet & angepasst" und „Konto wechseln" saßen früher im Überlaufmenü des
 * Kalender-Headers; mit dessen Wegfall ist das hier ihr neuer und einziger Ort.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    calendarViewModel: CalendarViewModel,
    onBack: () -> Unit,
    onSwitchAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val calendarState by calendarViewModel.uiState.collectAsState()
    var showSwitchAccountDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        SettingsTopBar(onBack = onBack)
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileCard(account = state.ownAccount)
            LinkedAccountCard(
                ownAccount = state.ownAccount,
                partnerAccount = state.partnerAccount,
            )
            SettingsRow(
                icon = Icons.AutoMirrored.Filled.List,
                title = "Ausgeblendet & angepasst",
                subtitle = "Lokale Ausblendungen und Änderungen verwalten",
                onClick = calendarViewModel::openCustomizationManager,
            )
            SettingsRow(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                title = "Konto wechseln",
                subtitle = "Verknüpfung auf diesem Gerät zurücksetzen",
                onClick = { showSwitchAccountDialog = true },
            )
        }
    }

    if (calendarState.isCustomizationManagerOpen) {
        CustomizationManagerSheet(
            customizations = calendarState.storedCustomizations,
            onRemove = calendarViewModel::removeCustomization,
            onDelete = calendarViewModel::deleteFromCustomizationManager,
            onDismiss = calendarViewModel::dismissCustomizationManager,
        )
    }

    if (showSwitchAccountDialog) {
        AlertDialog(
            onDismissRequest = { showSwitchAccountDialog = false },
            title = { Text("Konto wechseln?") },
            text = {
                Text(
                    "Selli vergisst eure Verknüpfung auf diesem Gerät und startet wieder " +
                        "bei der Anmeldung. Dein Google-Konto und eure Termine bleiben unverändert.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSwitchAccountDialog = false
                    onSwitchAccount()
                }) { Text("Konto wechseln") }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchAccountDialog = false }) { Text("Abbrechen") }
            },
        )
    }
}

/** Zurück-Leiste des Vollbild-Bereichs — hier blendet die Shell ihren globalen Header aus. */
@Composable
private fun SettingsTopBar(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Zurück",
            )
        }
        Text(text = "Profil & Einstellungen", style = MaterialTheme.typography.titleMedium)
    }
}

/** Großes Profilbild auf dem Lila-Grün-Verlauf — dasselbe Branding-Element wie im Header. */
@Composable
private fun ProfileCard(account: Account?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(selliGradient())
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (account != null) {
                PersonAvatar(person = account.person, size = 96.dp)
                Text(
                    text = account.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = onAccentColor(),
                )
            } else {
                CoupleAvatars(size = 64.dp)
                Text(
                    text = "Noch nicht verknüpft",
                    style = MaterialTheme.typography.titleMedium,
                    color = onAccentColor(),
                )
            }
        }
    }
}

/** Verknüpftes Google-Konto: eigene Adresse, darunter die der Partnerin/des Partners. */
@Composable
private fun LinkedAccountCard(
    ownAccount: Account?,
    partnerAccount: Account?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Verknüpftes Google-Konto",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AccountLine(account = ownAccount, fallback = "Kein Konto verknüpft")
            AccountLine(account = partnerAccount, fallback = "Partner noch nicht verknüpft")
        }
    }
}

@Composable
private fun AccountLine(account: Account?, fallback: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (account != null) {
            PersonAvatar(person = account.person, size = 32.dp)
            Column {
                Text(text = account.displayName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = account.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                text = fallback,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Antippbare Einstellungszeile mit Icon, Titel und erklärender Unterzeile. */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
