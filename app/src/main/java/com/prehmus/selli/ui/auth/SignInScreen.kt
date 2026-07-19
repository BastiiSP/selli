package com.prehmus.selli.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.ui.components.MascotMood
import com.prehmus.selli.ui.components.PersonAvatar
import com.prehmus.selli.ui.components.SelliMascot
import com.prehmus.selli.ui.theme.personColor
import com.prehmus.selli.ui.theme.selliGradient

/**
 * Einstieg: "Wer bist du?" + Google-Anmeldung, danach die einmalige
 * Partner-Verknüpfung (gegenseitige Kalender-Freigabe ohne manuelles Teilen).
 */
@Composable
fun SignInScreen(
    state: AuthUiState,
    onSignIn: (Person) -> Unit,
    onConnectPartner: (String) -> Unit,
    onConsentResult: (granted: Boolean, partnerEmail: String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SelliMascot(
            mood = if (state is AuthUiState.Ready) MascotMood.HAPPY else MascotMood.NEUTRAL,
            modifier = Modifier.size(96.dp),
        )
        Text(
            text = "Selli",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Euer gemeinsamer Kalender",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp),
        )

        when (state) {
            is AuthUiState.SignedOut, is AuthUiState.SignInError -> SignInStep(
                errorMessage = (state as? AuthUiState.SignInError)?.message,
                onSignIn = onSignIn,
                onDismissError = onDismissError,
            )
            AuthUiState.SigningIn -> {
                CircularProgressIndicator()
                Text(
                    text = "Anmeldung läuft …",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            is AuthUiState.ConnectPartner -> ConnectPartnerStep(
                state = state,
                onConnectPartner = onConnectPartner,
                onConsentResult = onConsentResult,
            )
            is AuthUiState.Ready -> Unit // SelliApp wechselt zur Kalenderansicht.
        }
    }
}

@Composable
private fun SignInStep(
    errorMessage: String?,
    onSignIn: (Person) -> Unit,
    onDismissError: () -> Unit,
) {
    var chosen by rememberSaveable { mutableStateOf<Person?>(null) }

    Text(
        text = "Wer bist du?",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PersonChoice(person = Person.MELLI, selected = chosen == Person.MELLI) { chosen = Person.MELLI }
        PersonChoice(person = Person.BASTI, selected = chosen == Person.BASTI) { chosen = Person.BASTI }
    }

    Button(
        onClick = { chosen?.let(onSignIn) },
        enabled = chosen != null,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(selliGradient(), MaterialTheme.shapes.large)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Mit Google anmelden",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
    }

    if (errorMessage != null) {
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 12.dp)
                .clickable(onClick = onDismissError),
        )
    }
}

@Composable
private fun PersonChoice(person: Person, selected: Boolean, onClick: () -> Unit) {
    val label = if (person == Person.MELLI) "Melli" else "Basti"
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = if (selected) 4.dp else 1.dp,
        modifier = Modifier
            .then(
                if (selected) {
                    Modifier.border(2.dp, personColor(person), MaterialTheme.shapes.large)
                } else Modifier
            )
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PersonAvatar(person = person, size = 48.dp)
            Text(text = label, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun ConnectPartnerStep(
    state: AuthUiState.ConnectPartner,
    onConnectPartner: (String) -> Unit,
    onConsentResult: (granted: Boolean, partnerEmail: String) -> Unit,
) {
    var partnerEmail by rememberSaveable { mutableStateOf("") }

    // Erstzugriffs-Consent der Google Calendar API (taucht typischerweise beim
    // allerersten Freigabe-Call auf): Dialog öffnen, danach automatisch weitermachen.
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        onConsentResult(result.resultCode == Activity.RESULT_OK, partnerEmail)
    }
    LaunchedEffect(state.pendingConsent) {
        state.pendingConsent?.let(consentLauncher::launch)
    }

    Text(
        text = "Hallo ${state.account.displayName}! Wie lautet die Google-E-Mail deines Lieblingsmenschen?",
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    Text(
        text = "Selli gibt eure Kalender automatisch gegenseitig frei — niemand muss in den Google-Einstellungen teilen.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 16.dp),
    )
    OutlinedTextField(
        value = partnerEmail,
        onValueChange = { partnerEmail = it },
        label = { Text("Partner-E-Mail") },
        singleLine = true,
        isError = state.errorMessage != null,
        supportingText = state.errorMessage?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { onConnectPartner(partnerEmail) },
        enabled = !state.isConnecting,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Text(if (state.isConnecting) "Verbindet …" else "Kalender verbinden")
    }
}
