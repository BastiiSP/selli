package com.prehmus.selli.ui.shell

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.prehmus.selli.AppDependencies
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.key
import com.prehmus.selli.ui.SelliDeepLink
import com.prehmus.selli.ui.calendar.CalendarScreen
import com.prehmus.selli.ui.calendar.CalendarViewModel
import com.prehmus.selli.ui.home.HomeScreen
import com.prehmus.selli.ui.location.LocationScreen
import com.prehmus.selli.ui.location.LocationViewModel
import com.prehmus.selli.ui.settings.SettingsScreen
import com.prehmus.selli.ui.settings.SettingsViewModel

/**
 * App-Gerüst hinter dem Anmeldegate: globaler Header, drei gleichwertige Ziele in der
 * Bottom-Navigation, Profil-/Einstellungsbereich als Vollbild darüber. Ersetzt den
 * früheren Zustand, in dem die Kalenderansicht der einzige Bildschirm war.
 *
 * Das [CalendarViewModel] wird hier einmal für das ganze Gerüst erzeugt und nicht pro
 * Route — Kalender und Homescreen arbeiten bewusst auf demselben Datenstand.
 */
@Composable
fun SelliShell(
    dependencies: AppDependencies,
    ownPerson: Person,
    onSwitchAccount: () -> Unit,
    deepLink: SelliDeepLink? = null,
    onDeepLinkHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentDestination = SelliDestination.fromRoute(currentRoute) ?: SelliStartDestination
    val isSettings = currentRoute == SETTINGS_ROUTE

    val calendarViewModel: CalendarViewModel = viewModel(
        factory = CalendarViewModel.factory(
            dependencies.calendarMergeService,
            dependencies.calendarRepository,
            dependencies.eventCustomizationRepository,
        ),
    )

    // Antippen einer Wir-Zeit-Benachrichtigung soll nicht nur den Termin öffnen, sondern
    // auch im Kalender-Tab landen — sonst öffnet sich das Sheet über dem Homescreen.
    LaunchedEffect(deepLink) {
        val target = deepLink ?: return@LaunchedEffect
        navController.switchTo(SelliDestination.CALENDAR)
        calendarViewModel.openDeepLinkedEvent(target.day, target.eventKey)
        onDeepLinkHandled()
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (!isSettings) {
            SelliTopBar(
                ownPerson = ownPerson,
                onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
            )
        }
        NavHost(
            navController = navController,
            startDestination = SelliStartDestination.route,
            modifier = Modifier.weight(1f),
            // Gleichwertige Ziele: sanftes Ein-/Ausblenden statt Richtungs-Slide, der eine
            // Hierarchie behaupten würde, die es zwischen den drei Zielen nicht gibt.
            enterTransition = { fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.96f) },
            exitTransition = { fadeOut(tween(180)) },
        ) {
            composable(SelliDestination.CALENDAR.route) {
                CalendarScreen(
                    viewModel = calendarViewModel,
                    suggestionRepository = dependencies.placeSuggestionRepository,
                )
            }
            composable(SelliDestination.HOME.route) {
                val uiState by calendarViewModel.uiState.collectAsState()
                HomeScreen(
                    uiState = uiState,
                    // Die Szene führt immer zum nächsten Wir-Zeit-Termin — dieselbe
                    // ViewModel-Aktion, die früher der Header-Countdown ausgelöst hat.
                    onOpenNextWirZeit = {
                        navController.switchTo(SelliDestination.CALENDAR)
                        calendarViewModel.onWirZeitCountdownClick()
                    },
                    onEventClick = { event ->
                        // Tippen führt in den Kalender-Tab und öffnet dort das Aktionen-Sheet —
                        // dieselbe Wirkung wie ein Tap auf eine Wir-Zeit-Benachrichtigung.
                        navController.switchTo(SelliDestination.CALENDAR)
                        calendarViewModel.openDeepLinkedEvent(event.start.toLocalDate(), event.key())
                    },
                )
            }
            composable(SelliDestination.LOCATION.route) {
                val locationViewModel: LocationViewModel = viewModel(
                    factory = LocationViewModel.factory(
                        repository = dependencies.locationRepository,
                        isConfigured = dependencies.isLocationSharingConfigured,
                    ),
                )
                LocationScreen(viewModel = locationViewModel)
            }
            composable(SETTINGS_ROUTE) {
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.factory(dependencies.sessionRepository),
                )
                SettingsScreen(
                    viewModel = settingsViewModel,
                    calendarViewModel = calendarViewModel,
                    onBack = { navController.popBackStack() },
                    onSwitchAccount = onSwitchAccount,
                )
            }
        }
        if (!isSettings) {
            SelliBottomBar(
                current = currentDestination,
                ownPerson = ownPerson,
                onSelect = { destination ->
                    if (destination.route != currentRoute) navController.switchTo(destination)
                },
            )
        }
    }
}

/**
 * Wechsel zwischen den gleichwertigen Zielen: kein wachsender Back-Stack, und der
 * Zustand des verlassenen Ziels (Scrollposition, ausgewählter Tag) bleibt erhalten.
 */
internal fun NavHostController.switchTo(destination: SelliDestination) {
    navigate(destination.route) {
        popUpTo(SelliStartDestination.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
