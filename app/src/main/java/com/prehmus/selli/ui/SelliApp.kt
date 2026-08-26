package com.prehmus.selli.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prehmus.selli.AppDependencies
import com.prehmus.selli.domain.model.EventKey
import com.prehmus.selli.domain.model.Person
import java.time.LocalDate
import com.prehmus.selli.ui.auth.AuthUiState
import com.prehmus.selli.ui.auth.AuthViewModel
import com.prehmus.selli.ui.auth.SignInScreen
import com.prehmus.selli.ui.shell.SelliShell

/** Zieltermin einer angetippten Wir-Zeit-Benachrichtigung. */
data class SelliDeepLink(val day: LocalDate, val eventKey: EventKey)

/**
 * App-Wurzel: Anmeldegate vor dem App-Gerüst. Die Abhängigkeiten liefert die
 * MainActivity über [AppDependencies].
 *
 * [deepLink] kommt aus den Intent-Extras einer Benachrichtigung und wird angewandt, sobald
 * das Gerüst hinter dem Anmeldegate steht — ohne Anmeldung gibt es keinen Termin zu zeigen.
 */
@Composable
fun SelliApp(
    dependencies: AppDependencies,
    rememberOwnPerson: (Person) -> Unit = {},
    deepLink: SelliDeepLink? = null,
    onDeepLinkHandled: () -> Unit = {},
) {
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.factory(
            dependencies.googleCalendarRepository,
            dependencies.sessionRepository,
            rememberOwnPerson,
        ),
    )
    val authState by authViewModel.uiState.collectAsState()

    // Surface als Wurzel setzt LocalContentColor auf onBackground — ohne sie bleibt
    // Text ohne explizite Farbe schwarz und ist im Dark Mode unlesbar.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (val state = authState) {
            is AuthUiState.Ready -> SelliShell(
                dependencies = dependencies,
                ownPerson = state.account.person,
                onSwitchAccount = authViewModel::switchAccount,
                deepLink = deepLink,
                onDeepLinkHandled = onDeepLinkHandled,
            )
            else -> SignInScreen(
                state = state,
                onSignIn = authViewModel::signIn,
                onConnectPartner = authViewModel::connectPartner,
                onConsentResult = authViewModel::onConsentResult,
                onDismissError = authViewModel::dismissError,
            )
        }
    }
}
