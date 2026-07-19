package com.prehmus.selli.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prehmus.selli.AppDependencies
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.ui.auth.AuthUiState
import com.prehmus.selli.ui.auth.AuthViewModel
import com.prehmus.selli.ui.auth.SignInScreen
import com.prehmus.selli.ui.calendar.CalendarScreen
import com.prehmus.selli.ui.calendar.CalendarViewModel

/**
 * App-Wurzel: Anmeldegate vor der Kalenderansicht. Die Abhängigkeiten liefert
 * die MainActivity über [AppDependencies].
 */
@Composable
fun SelliApp(
    dependencies: AppDependencies,
    rememberOwnPerson: (Person) -> Unit = {},
) {
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.factory(dependencies.googleCalendarRepository, rememberOwnPerson),
    )
    val authState by authViewModel.uiState.collectAsState()

    when (authState) {
        is AuthUiState.Ready -> {
            val calendarViewModel: CalendarViewModel = viewModel(
                factory = CalendarViewModel.factory(
                    dependencies.calendarMergeService,
                    dependencies.calendarRepository,
                ),
            )
            CalendarScreen(viewModel = calendarViewModel)
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
