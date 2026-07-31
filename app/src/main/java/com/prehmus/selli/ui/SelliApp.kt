package com.prehmus.selli.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.prehmus.selli.ui.calendar.CalendarScreen
import com.prehmus.selli.ui.calendar.CalendarViewModel

/** Zieltermin einer angetippten Wir-Zeit-Benachrichtigung. */
data class SelliDeepLink(val day: LocalDate, val eventKey: EventKey)

/**
 * App-Wurzel: Anmeldegate vor der Kalenderansicht. Die Abhängigkeiten liefert
 * die MainActivity über [AppDependencies].
 *
 * [deepLink] kommt aus den Intent-Extras einer Benachrichtigung und wird angewandt, sobald der
 * Kalender hinter dem Anmeldegate steht — ohne Anmeldung gibt es keinen Termin zu zeigen.
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
        when (authState) {
            is AuthUiState.Ready -> {
                val calendarViewModel: CalendarViewModel = viewModel(
                    factory = CalendarViewModel.factory(
                        dependencies.calendarMergeService,
                        dependencies.calendarRepository,
                        dependencies.eventCustomizationRepository,
                    ),
                )
                LaunchedEffect(deepLink) {
                    val target = deepLink ?: return@LaunchedEffect
                    calendarViewModel.openDeepLinkedEvent(target.day, target.eventKey)
                    onDeepLinkHandled()
                }
                CalendarScreen(
                    viewModel = calendarViewModel,
                    onSwitchAccount = authViewModel::switchAccount,
                )
            }
            else -> SignInScreen(
                state = authState,
                onSignIn = authViewModel::signIn,
                onConnectPartner = authViewModel::connectPartner,
                onConsentResult = authViewModel::onConsentResult,
                onDismissError = authViewModel::dismissError,
            )
        }
    }
}
