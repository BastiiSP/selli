# App-Grundgerüst Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Vor dem Start jeder Aufgabe:** Lies `CLAUDE.md` im Projekt-Root und nutze das LSP-Tool, um die betroffenen Dateien zu verstehen, bevor du sie änderst — die unten zitierten Codeausschnitte sind der Stand zum Planungszeitpunkt, keine Kopiervorlage ohne eigene Prüfung.

**Goal:** Selli bekommt statt eines einzelnen Hauptbildschirms ein erweiterbares
Grundgerüst: ein schmaler, überall sichtbarer Header mit Zugang zu einem neuen
Profil-/Einstellungsbereich, eine schmale Bottom-Navigation mit drei gleichwertigen
Zielen (Kalender / Wir / Standort) und ein eigener Homescreen, der die bisherige
Header-Szene samt kommenden gemeinsamen Terminen aufnimmt.

**Architecture:** `navigation-compose` mit vier Routen. Eine neue `SelliShell`
umschließt den `NavHost` mit dem globalen Header und der Bottom-Navigation. Der
heutige `MascotHeader` wird zerlegt: Wortmarke/Avatare wandern in den globalen
Header, die Menüeinträge in die Einstellungen, Zeitraumtitel und Pfeile in eine neue
schmale Leiste im Kalender-Tab, die Wanderweg-Szene auf den Homescreen. Die
Kalenderlogik selbst bleibt unangetastet.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2024.09.02), Material3,
androidx.navigation:navigation-compose, JUnit4.

**Spec:** `docs/superpowers/specs/2026-08-26-grundgeruest-standort-design.md`

## Global Constraints

- **Owner aller Tasks außer Task 4a: Claude.** Alles liegt unter `ui/` — laut
  `CLAUDE.md` Claude-Territorium. Task 4a berührt eine Datei in `domain/`
  (Codex-Territorium); Begründung und Abweichung stehen dort.
- **Designsprache fortführen:** warme Neutraltöne aus `ui/theme/Color.kt`, runde
  Formen, Nunito über `MaterialTheme.typography`, `selliGradient()` als Branding.
- **Merkregel Kontrast:** Text/Icons auf `selliGradient()`- oder
  `personColor()`-Flächen **nie** hart `Color.White`, immer `onAccentColor()`.
- **Keine Secrets im Code**, keine Änderung an `local.properties`.
- Deutsche UI-Texte, deutsche KDoc-Kommentare — wie im Bestand.
- Nach jeder Aufgabe: `./gradlew testDebugUnitTest assembleDebug` muss grün sein.
  Falls `Unable to locate a Java Runtime`:
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- Bestehende 264 Unit-Tests dürfen nicht rot werden.

---

## Task 1: Navigationsgerüst mit drei Platzhalter-Zielen

**Owner:** Claude

**Files:**
- Modify: `gradle/libs.versions.toml` (neue Version + Library)
- Modify: `app/build.gradle.kts` (Dependency)
- Create: `app/src/main/java/com/prehmus/selli/ui/shell/SelliDestination.kt`
- Create: `app/src/main/java/com/prehmus/selli/ui/shell/SelliShell.kt`
- Create: `app/src/main/java/com/prehmus/selli/ui/shell/SelliBottomBar.kt`
- Modify: `app/src/main/java/com/prehmus/selli/ui/SelliApp.kt`
- Test: `app/src/test/java/com/prehmus/selli/ui/shell/SelliDestinationTest.kt`

**Interfaces:**
- Produces: `enum class SelliDestination { CALENDAR, HOME, LOCATION }` mit
  `val route: String`, `val label: String`; `const val SETTINGS_ROUTE = "settings"`;
  `fun SelliDestination.Companion.fromRoute(route: String?): SelliDestination?`
- Produces: `@Composable fun SelliShell(dependencies: AppDependencies, ownAccountPerson: Person, onSwitchAccount: () -> Unit, deepLink: SelliDeepLink?, onDeepLinkHandled: () -> Unit)`
- Produces: `@Composable fun SelliBottomBar(current: SelliDestination, ownPerson: Person, onSelect: (SelliDestination) -> Unit, modifier: Modifier)`

- [ ] **Step 1: Version-Catalog-Eintrag ergänzen**

In `gradle/libs.versions.toml` unter `[versions]` ergänzen:

```toml
navigationCompose = "2.8.3"
```

Unter `[libraries]` ergänzen:

```toml
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
```

- [ ] **Step 2: Dependency in `app/build.gradle.kts` ergänzen**

Direkt nach `implementation(libs.androidx.material3)` einfügen:

```kotlin
    implementation(libs.androidx.navigation.compose)
```

- [ ] **Step 3: Failing test für die Routen-Abbildung schreiben**

`app/src/test/java/com/prehmus/selli/ui/shell/SelliDestinationTest.kt`:

```kotlin
package com.prehmus.selli.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelliDestinationTest {

    @Test
    fun `routes are unique and stable`() {
        val routes = SelliDestination.entries.map { it.route }
        assertEquals(listOf("calendar", "home", "location"), routes)
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `fromRoute maps every known route back`() {
        SelliDestination.entries.forEach { destination ->
            assertEquals(destination, SelliDestination.fromRoute(destination.route))
        }
    }

    @Test
    fun `fromRoute returns null for the settings route and for unknown input`() {
        assertNull(SelliDestination.fromRoute(SETTINGS_ROUTE))
        assertNull(SelliDestination.fromRoute(null))
        assertNull(SelliDestination.fromRoute("nope"))
    }

    @Test
    fun `bottom navigation order puts the shared home in the middle`() {
        assertEquals(SelliDestination.HOME, SelliDestination.entries[1])
    }
}
```

- [ ] **Step 4: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew testDebugUnitTest --tests "*SelliDestinationTest*"`
Expected: FAIL — `Unresolved reference: SelliDestination`.

- [ ] **Step 5: `SelliDestination.kt` schreiben**

```kotlin
package com.prehmus.selli.ui.shell

/**
 * Die drei gleichwertigen Ziele der Bottom-Navigation. Die Reihenfolge der Einträge ist
 * zugleich die Reihenfolge in der Leiste — „Wir" sitzt bewusst in der Mitte, weil es das
 * zentrale „auf einen Blick"-Ziel ist.
 */
enum class SelliDestination(val route: String, val label: String) {
    CALENDAR(route = "calendar", label = "Kalender"),
    HOME(route = "home", label = "Wir"),
    LOCATION(route = "location", label = "Standort"),
    ;

    companion object {
        /** Route der Vollbild-Einstellungen — bewusst kein Bottom-Navigation-Ziel. */
        fun fromRoute(route: String?): SelliDestination? =
            entries.firstOrNull { destination -> destination.route == route }
    }
}

/** Profil-/Einstellungsbereich: eigener Vollbild-Screen über dem Bottom-Navigation-Gerüst. */
const val SETTINGS_ROUTE = "settings"

/** Ziel, mit dem die App startet (siehe Spec: „Wir" ist das zentrale Ziel). */
val SelliStartDestination: SelliDestination = SelliDestination.HOME
```

- [ ] **Step 6: Test laufen lassen und Erfolg bestätigen**

Run: `./gradlew testDebugUnitTest --tests "*SelliDestinationTest*"`
Expected: PASS (4 Tests).

- [ ] **Step 7: `SelliBottomBar.kt` schreiben**

```kotlin
package com.prehmus.selli.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.ui.theme.personColor

/**
 * Schmale Bottom-Navigation mit den drei gleichwertigen Zielen. Der Indikator trägt die
 * Personenfarbe der angemeldeten Person — kleine Personalisierung ohne neues Farbsystem.
 */
@Composable
fun SelliBottomBar(
    current: SelliDestination,
    ownPerson: Person,
    onSelect: (SelliDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = personColor(ownPerson)
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        SelliDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == current,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon(),
                        contentDescription = destination.label,
                        modifier = Modifier.height(24.dp),
                    )
                },
                label = {
                    Text(text = destination.label, style = MaterialTheme.typography.labelMedium)
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = accent,
                    selectedTextColor = accent,
                    indicatorColor = accent.copy(alpha = 0.14f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private fun SelliDestination.icon(): ImageVector = when (this) {
    SelliDestination.CALENDAR -> Icons.Default.CalendarMonth
    SelliDestination.HOME -> Icons.Default.Favorite
    SelliDestination.LOCATION -> Icons.Default.Place
}
```

Hinweis: `Column` und `height` sind im Entwurf oben importiert, aber nur `height`
wird benutzt — unbenutzte Importe vor dem Commit entfernen.

- [ ] **Step 8: `SelliShell.kt` mit Platzhalter-Screens schreiben**

Vorläufige Fassung — Header und echte Screens kommen in Task 2/3/5/6. Wichtig ist
hier nur, dass die Navigation trägt.

```kotlin
package com.prehmus.selli.ui.shell

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.prehmus.selli.domain.model.Person

/**
 * App-Gerüst hinter dem Anmeldegate: globaler Header, drei gleichwertige Ziele in der
 * Bottom-Navigation, Vollbild-Einstellungen darüber. Ersetzt den früheren Zustand, in dem
 * die Kalenderansicht der einzige Bildschirm war.
 */
@Composable
fun SelliShell(
    ownPerson: Person,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentDestination = SelliDestination.fromRoute(currentRoute) ?: SelliStartDestination

    Column(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = SelliStartDestination.route,
            modifier = Modifier.weight(1f),
            // Gleichwertige Ziele: sanftes Ein-/Ausblenden statt Richtungs-Slide, der eine
            // Hierarchie behaupten würde, die es zwischen den drei Zielen nicht gibt.
            enterTransition = { fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.96f) },
            exitTransition = { fadeOut(tween(180)) },
        ) {
            SelliDestination.entries.forEach { destination ->
                composable(destination.route) {
                    PlaceholderScreen(destination.label)
                }
            }
            composable(SETTINGS_ROUTE) { PlaceholderScreen("Einstellungen") }
        }
        SelliBottomBar(
            current = currentDestination,
            ownPerson = ownPerson,
            onSelect = { destination ->
                if (destination.route == currentRoute) return@SelliBottomBar
                navController.navigate(destination.route) {
                    // Kein wachsender Back-Stack beim Hin- und Herwechseln zwischen den Zielen.
                    popUpTo(SelliStartDestination.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )
    }
}

@Composable
private fun PlaceholderScreen(label: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.headlineSmall)
    }
}
```

Hinweis: `24.dp` braucht `import androidx.compose.ui.unit.dp` — beim Schreiben ergänzen.

- [ ] **Step 9: `SelliApp.kt` auf die Shell umstellen**

Im `AuthUiState.Ready`-Zweig das direkte `CalendarScreen(...)` vorläufig durch
`SelliShell(ownPerson = (authState as AuthUiState.Ready).account.person)` ersetzen.
`CalendarViewModel`, `deepLink` und `suggestionRepository` bleiben zunächst
ungenutzt — sie werden in Task 3 wieder verdrahtet. Damit der Compiler nicht über
ungenutzte Variablen stolpert, den `CalendarViewModel`-Block in diesem Schritt
auskommentiert **nicht** stehen lassen, sondern entfernen; Task 3 baut ihn in der
Shell neu auf.

- [ ] **Step 10: Build und Tests laufen lassen**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, alle Tests grün.

- [ ] **Step 11: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
        app/src/main/java/com/prehmus/selli/ui/shell app/src/main/java/com/prehmus/selli/ui/SelliApp.kt \
        app/src/test/java/com/prehmus/selli/ui/shell
git commit -m "Navigationsgeruest mit drei gleichwertigen Zielen einfuehren"
```

---

## Task 2: Globaler Header mit Zugang zum Profilbereich

**Owner:** Claude

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/ui/shell/SelliTopBar.kt`
- Modify: `app/src/main/java/com/prehmus/selli/ui/shell/SelliShell.kt`

**Interfaces:**
- Consumes: `SelliDestination`, `SETTINGS_ROUTE` (Task 1)
- Produces: `@Composable fun SelliTopBar(ownPerson: Person, onOpenSettings: () -> Unit, modifier: Modifier)`

- [ ] **Step 1: `SelliTopBar.kt` schreiben**

```kotlin
package com.prehmus.selli.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.ui.components.PersonAvatar
import com.prehmus.selli.ui.theme.onAccentColor
import com.prehmus.selli.ui.theme.selliGradient

/**
 * Schmaler, auf jedem Bildschirm sichtbarer Header: Lila-Grün-Verlauf als Branding,
 * Wortmarke links, eigener Avatar rechts als Zugang zum Profil-/Einstellungsbereich.
 * Bewusst flach — der frühere Header trug Zeitraumnavigation und Countdown mit, die
 * jetzt im Kalender-Tab bzw. auf dem Homescreen sitzen.
 */
@Composable
fun SelliTopBar(
    ownPerson: Person,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = selliGradient(),
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            )
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Selli",
            style = MaterialTheme.typography.titleLarge,
            color = onAccentColor(),
            modifier = Modifier.weight(1f),
        )
        PersonAvatar(
            person = ownPerson,
            size = 36.dp,
            modifier = Modifier
                .clip(androidx.compose.foundation.shape.CircleShape)
                .clickable(onClick = onOpenSettings)
                .semantics { contentDescription = "Profil und Einstellungen" },
        )
    }
}
```

Hinweis: `CircleShape` sauber oben importieren
(`import androidx.compose.foundation.shape.CircleShape`) statt vollqualifiziert zu
schreiben.

- [ ] **Step 2: Header in die Shell einhängen**

In `SelliShell.kt` oberhalb des `NavHost` einfügen und die Einstellungsroute
verdrahten. Der Header wird auf der Einstellungsroute **nicht** gezeigt (dort trägt
der Screen selbst eine Zurück-Leiste), die Bottom-Navigation ebenfalls nicht:

```kotlin
    val isSettings = currentRoute == SETTINGS_ROUTE

    Column(modifier = modifier.fillMaxSize()) {
        if (!isSettings) {
            SelliTopBar(
                ownPerson = ownPerson,
                onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
            )
        }
        NavHost(/* … unverändert … */)
        if (!isSettings) {
            SelliBottomBar(/* … unverändert … */)
        }
    }
```

- [ ] **Step 3: Build laufen lassen**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Kontrast prüfen**

Den `ui-reviewer`-Subagent auf `app/src/main/java/com/prehmus/selli/ui/shell/`
laufen lassen. Erwartung: kein hartes `Color.White` auf Gradient-Flächen — der
Header nutzt `onAccentColor()`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/ui/shell
git commit -m "Globalen Selli-Header mit Zugang zum Profilbereich ergaenzen"
```

---

## Task 3: Kalenderansicht in den Kalender-Tab umziehen

**Owner:** Claude

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/ui/calendar/CalendarPeriodBar.kt`
- Modify: `app/src/main/java/com/prehmus/selli/ui/calendar/CalendarScreen.kt`
- Modify: `app/src/main/java/com/prehmus/selli/ui/shell/SelliShell.kt`
- Modify: `app/src/main/java/com/prehmus/selli/ui/SelliApp.kt`

**Interfaces:**
- Consumes: `SelliDestination` (Task 1), bestehende `CalendarViewModel`-API
- Produces: `@Composable fun CalendarPeriodBar(title: String, onPrevious: () -> Unit, onNext: () -> Unit, onRefresh: () -> Unit, modifier: Modifier)`
- Produces: geänderte `CalendarScreen`-Signatur — ohne `onSwitchAccount`, dafür
  `onOpenCustomizationManager` entfällt (zieht in die Einstellungen):
  `@Composable fun CalendarScreen(viewModel: CalendarViewModel, modifier: Modifier = Modifier, suggestionRepository: PlaceSuggestionRepository? = null)`

- [ ] **Step 1: `CalendarPeriodBar.kt` schreiben**

Ersetzt die Zeitraumzeile des alten `MascotHeader` — jetzt auf warmem Untergrund
statt auf dem Gradient, also mit normalen Vordergrundfarben.

```kotlin
package com.prehmus.selli.ui.calendar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Schmale Zeitraumzeile über dem Kalender: Blättern nach hinten/vorn, aktueller Zeitraum,
 * manuelles Aktualisieren. Saß früher im Gradient-Header, der jetzt global und flach ist.
 */
@Composable
fun CalendarPeriodBar(
    title: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Zurück",
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Weiter",
            )
        }
        IconButton(onClick = onRefresh) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Aktualisieren")
        }
    }
}
```

- [ ] **Step 2: `CalendarScreen` anpassen**

Drei Änderungen, alles andere bleibt:

1. Signatur: `onSwitchAccount: () -> Unit` entfernen, `suggestionRepository` bleibt.
2. Den `MascotHeader(...)`-Aufruf ersetzen durch:

```kotlin
            CalendarPeriodBar(
                title = calendarHeaderTitle(uiState.viewMode, uiState.visibleMonth, uiState.selectedDay),
                onPrevious = goPrevious,
                onNext = goNext,
                onRefresh = { viewModel.refresh(forceNetwork = true) },
            )
```

3. Den Block `if (showSwitchAccountDialog) { AlertDialog(...) }` samt
   `var showSwitchAccountDialog by remember { mutableStateOf(false) }` entfernen —
   „Konto wechseln" lebt ab Task 6 in den Einstellungen. Ebenso den
   `CustomizationManagerSheet`-Block **hier entfernen**; er zieht in Task 6 mit um.
   `viewModel.openCustomizationManager` bleibt in der ViewModel-API bestehen und
   wird ab Task 6 von den Einstellungen aufgerufen.

Der `Scaffold` in `CalendarScreen` bleibt (er trägt Snackbar + FAB), verliert aber
seine Statusleisten-Verantwortung an den globalen Header.

- [ ] **Step 3: Kalender-Tab in der Shell verdrahten**

`SelliShell` braucht die Abhängigkeiten und den Deep-Link. Signatur erweitern:

```kotlin
@Composable
fun SelliShell(
    dependencies: AppDependencies,
    ownPerson: Person,
    onSwitchAccount: () -> Unit,
    deepLink: SelliDeepLink? = null,
    onDeepLinkHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
)
```

Im Rumpf das `CalendarViewModel` einmal für die ganze Shell erzeugen (nicht pro
Route — der Homescreen braucht dieselben Daten):

```kotlin
    val calendarViewModel: CalendarViewModel = viewModel(
        factory = CalendarViewModel.factory(
            dependencies.calendarMergeService,
            dependencies.calendarRepository,
            dependencies.eventCustomizationRepository,
        ),
    )
```

Und den Deep-Link so behandeln, dass er zusätzlich auf den Kalender-Tab springt:

```kotlin
    LaunchedEffect(deepLink) {
        val target = deepLink ?: return@LaunchedEffect
        navController.navigate(SelliDestination.CALENDAR.route) {
            popUpTo(SelliStartDestination.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        calendarViewModel.openDeepLinkedEvent(target.day, target.eventKey)
        onDeepLinkHandled()
    }
```

Die `composable`-Blöcke einzeln ausschreiben statt über `entries` zu iterieren:

```kotlin
            composable(SelliDestination.CALENDAR.route) {
                CalendarScreen(
                    viewModel = calendarViewModel,
                    suggestionRepository = dependencies.placeSuggestionRepository,
                )
            }
            composable(SelliDestination.HOME.route) { PlaceholderScreen("Wir") }
            composable(SelliDestination.LOCATION.route) { PlaceholderScreen("Standort") }
            composable(SETTINGS_ROUTE) { PlaceholderScreen("Einstellungen") }
```

- [ ] **Step 4: `SelliApp.kt` vollständig verdrahten**

```kotlin
            is AuthUiState.Ready -> {
                val ready = authState as AuthUiState.Ready
                SelliShell(
                    dependencies = dependencies,
                    ownPerson = ready.account.person,
                    onSwitchAccount = authViewModel::switchAccount,
                    deepLink = deepLink,
                    onDeepLinkHandled = onDeepLinkHandled,
                )
            }
```

- [ ] **Step 5: Build und Tests laufen lassen**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, alle Tests grün.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/ui
git commit -m "Kalenderansicht in den Kalender-Tab umziehen"
```

---

## Task 4a: Liste der kommenden Wir-Zeit-Termine im Selector

**Owner:** Codex-Territorium (`domain/`) — **hier bewusst von Claude umgesetzt.**
Begründung: sechs Zeilen in einer bestehenden Datei plus ein Test. Einen eigenen
Codex-Job dafür aufzusetzen (Worktree, Prompt, Verifikation, Squash-Merge) kostet
mehr als die Änderung. Abweichung wird im Claudian-Update offengelegt.

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/domain/widget/NextSharedEventSelector.kt`
- Test: `app/src/test/java/com/prehmus/selli/domain/widget/NextSharedEventSelectorTest.kt`

**Interfaces:**
- Produces: `fun NextSharedEventSelector.selectUpcoming(events: List<CalendarEvent>, now: LocalDateTime, limit: Int): List<CalendarEvent>`
- Bestehendes `select(events, now): CalendarEvent?` bleibt verhaltensgleich.

- [ ] **Step 1: Failing test schreiben**

An `NextSharedEventSelectorTest.kt` anfügen (Datei zuvor mit `ls`/LSP prüfen; falls
nicht vorhanden, nach dem Muster der bestehenden `domain/widget`-Tests neu anlegen):

```kotlin
    @Test
    fun `selectUpcoming returns future shared events in order and respects the limit`() {
        val now = LocalDateTime.of(2026, 8, 26, 12, 0)
        val events = listOf(
            sharedEvent(id = "c", start = now.plusDays(3)),
            sharedEvent(id = "a", start = now.plusDays(1)),
            sharedEvent(id = "b", start = now.plusDays(2)),
            sharedEvent(id = "past", start = now.minusDays(1)),
        )

        val result = NextSharedEventSelector().selectUpcoming(events, now, limit = 2)

        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    @Test
    fun `selectUpcoming skips non-blocking all-day markers`() {
        val now = LocalDateTime.of(2026, 8, 26, 12, 0)
        val marker = sharedEvent(id = "marker", start = now.plusDays(1))
            .copy(isAllDay = true, blocksSharedFreeTime = false)

        val result = NextSharedEventSelector().selectUpcoming(listOf(marker), now, limit = 5)

        assertEquals(emptyList<String>(), result.map { it.id })
    }

    @Test
    fun `select returns the first element of selectUpcoming`() {
        val now = LocalDateTime.of(2026, 8, 26, 12, 0)
        val events = listOf(
            sharedEvent(id = "later", start = now.plusDays(2)),
            sharedEvent(id = "soon", start = now.plusDays(1)),
        )
        val selector = NextSharedEventSelector()

        assertEquals(
            selector.selectUpcoming(events, now, limit = 5).first(),
            selector.select(events, now),
        )
    }
```

`sharedEvent(...)` ist eine Test-Hilfsfunktion. Existiert sie in der Datei nicht,
ergänzen:

```kotlin
    private fun sharedEvent(id: String, start: LocalDateTime) = CalendarEvent(
        id = id,
        title = "Wir-Zeit $id",
        start = start,
        end = start.plusHours(2),
        isAllDay = false,
        source = CalendarSource.GOOGLE_OWN,
        owner = Person.BASTI,
        isSharedEvent = true,
        category = EventCategory.TOGETHER,
    )
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew testDebugUnitTest --tests "*NextSharedEventSelectorTest*"`
Expected: FAIL — `Unresolved reference: selectUpcoming`.

- [ ] **Step 3: `selectUpcoming` implementieren, `select` darauf zurückführen**

```kotlin
class NextSharedEventSelector {
    /**
     * Frühester noch nicht beendeter Termin mit `category == TOGETHER`, unabhängig vom Owner.
     * Nicht blockierende Ganztags-Marker bleiben außen vor — dieselbe Regel wie in der
     * Frei-Zeit-Berechnung.
     */
    fun select(events: List<CalendarEvent>, now: LocalDateTime): CalendarEvent? =
        selectUpcoming(events = events, now = now, limit = 1).firstOrNull()

    /**
     * Die nächsten [limit] noch nicht beendeten Wir-Zeit-Termine in chronologischer
     * Reihenfolge — Datenquelle für die Liste „Nächste gemeinsame Termine" auf dem Homescreen.
     */
    fun selectUpcoming(
        events: List<CalendarEvent>,
        now: LocalDateTime,
        limit: Int,
    ): List<CalendarEvent> = events
        .asSequence()
        .filter { event -> event.category == EventCategory.TOGETHER }
        .filter { event -> event.countsAsBusy }
        .filter { event -> event.end > now }
        .sortedWith(
            compareBy<CalendarEvent> { event -> event.start }
                .thenBy { event -> event.title }
                .thenBy { event -> event.id },
        )
        .take(limit)
        .toList()
}
```

- [ ] **Step 4: Tests laufen lassen**

Run: `./gradlew testDebugUnitTest --tests "*NextSharedEventSelectorTest*"`
Expected: PASS. Danach die volle Suite: `./gradlew testDebugUnitTest` — der
bestehende Widget-Test darf nicht rot werden, weil `select` verhaltensgleich bleibt.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/domain/widget/NextSharedEventSelector.kt \
        app/src/test/java/com/prehmus/selli/domain/widget/NextSharedEventSelectorTest.kt
git commit -m "Selector um Liste der kommenden Wir-Zeit-Termine erweitern"
```

---

## Task 4b: `upcomingWirZeitEvents` im CalendarUiState

**Owner:** Claude

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/ui/calendar/CalendarViewModel.kt`
- Test: `app/src/test/java/com/prehmus/selli/ui/calendar/CalendarViewModelTest.kt`

**Interfaces:**
- Consumes: `NextSharedEventSelector.selectUpcoming` (Task 4a)
- Produces: `CalendarUiState.upcomingWirZeitEvents: List<CalendarEvent>` (Default `emptyList()`)

- [ ] **Step 1: Failing test schreiben**

An `CalendarViewModelTest.kt` anfügen. Das bestehende Test-Setup dieser Datei
(Fake-Merge-Service, `MainDispatcherRule`) wiederverwenden — vor dem Schreiben mit
LSP ansehen, wie die vorhandenen Tests den ViewModel bauen, und demselben Muster
folgen:

```kotlin
    @Test
    fun `upcoming wir zeit events expose the next shared events`() = runTest {
        val today = LocalDate.now()
        val first = sharedEvent(id = "first", start = today.atTime(10, 0).plusDays(1))
        val second = sharedEvent(id = "second", start = today.atTime(10, 0).plusDays(2))
        val viewModel = createViewModel(events = listOf(second, first))

        advanceUntilIdle()

        assertEquals(
            listOf("first", "second"),
            viewModel.uiState.value.upcomingWirZeitEvents.map { it.id },
        )
        assertEquals("first", viewModel.uiState.value.nextWirZeitEvent?.id)
    }
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew testDebugUnitTest --tests "*CalendarViewModelTest*"`
Expected: FAIL — `Unresolved reference: upcomingWirZeitEvents`.

- [ ] **Step 3: Feld ergänzen**

In `CalendarUiState` direkt hinter `nextWirZeitEvent` einfügen:

```kotlin
    /**
     * Die nächsten Wir-Zeit-Termine (max. 5) aus demselben 30-Tage-Fetch wie
     * [nextWirZeitEvent] — Datenquelle der Liste „Nächste gemeinsame Termine"
     * auf dem Homescreen.
     */
    val upcomingWirZeitEvents: List<CalendarEvent> = emptyList(),
```

- [ ] **Step 4: `refreshNextWirZeitEvent` erweitern**

```kotlin
    private fun refreshNextWirZeitEvent() {
        viewModelScope.launch {
            val range = DateRange(start = LocalDate.now(), endInclusive = LocalDate.now().plusDays(30))
            val events = runCatching { mergeService.mergedEvents(range) }.getOrDefault(emptyList())
            val upcoming = NextSharedEventSelector()
                .selectUpcoming(events = events, now = LocalDateTime.now(), limit = 5)
            _uiState.update {
                it.copy(nextWirZeitEvent = upcoming.firstOrNull(), upcomingWirZeitEvents = upcoming)
            }
        }
    }
```

- [ ] **Step 5: Tests laufen lassen**

Run: `./gradlew testDebugUnitTest`
Expected: PASS, alle Tests grün.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/ui/calendar/CalendarViewModel.kt \
        app/src/test/java/com/prehmus/selli/ui/calendar/CalendarViewModelTest.kt
git commit -m "Kommende Wir-Zeit-Termine in den Kalender-UiState aufnehmen"
```

---

## Task 5: Homescreen mit Wanderweg-Szene, River Spirit und Terminliste

**Owner:** Claude

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/ui/home/WirZeitJourneyScene.kt` (verschoben aus `MascotHeader.kt`)
- Create: `app/src/main/java/com/prehmus/selli/ui/home/RiverSpirit.kt`
- Create: `app/src/main/java/com/prehmus/selli/ui/home/HomeScreen.kt`
- Delete: `app/src/main/java/com/prehmus/selli/ui/calendar/MascotHeader.kt`
- Modify: `app/src/main/java/com/prehmus/selli/ui/shell/SelliShell.kt`

**Interfaces:**
- Consumes: `CalendarUiState.upcomingWirZeitEvents`, `nextWirZeitEvent`, `isSyncing` (Task 4b)
- Produces: `@Composable fun WirZeitJourneyScene(countdown: WirZeitCountdown, modifier: Modifier)` — aus `MascotHeader.kt` unverändert übernommen, nur `private` entfernt
- Produces: `@Composable fun RiverSpirit(modifier: Modifier)`
- Produces: `@Composable fun HomeScreen(uiState: CalendarUiState, onEventClick: (CalendarEvent) -> Unit, modifier: Modifier)`

- [ ] **Step 1: Szene aus `MascotHeader.kt` nach `ui/home/WirZeitJourneyScene.kt` verschieben**

Übernommen werden — **unverändert im Verhalten**, nur Paket und Sichtbarkeit
angepasst: `WirZeitCountdownScene`, `WirZeitJourneyScene`, `createJourneyPath`,
`pathPosition`, `pathPositionAtDistance`, `loopingCloudX`,
`DrawScope.drawSoftCloud`, `countdownLabel`. Paketzeile auf
`package com.prehmus.selli.ui.home` ändern, `WirZeitJourneyScene` und
`countdownLabel` von `private` auf `internal` heben, die Hilfsfunktionen bleiben
`private`.

**Nicht** übernommen (fallen weg): `MascotHeader`, `CollapseToggle`, `HeaderMenu`,
`WirZeitCountdownSection` (der eingeklappte Textzustand ist ohne Header sinnlos —
die Szene ist auf dem Homescreen immer ausgeklappt).

Das Minuten-Ticken aus `WirZeitCountdownSection` wird gebraucht und zieht in
`HomeScreen` (Step 3).

- [ ] **Step 2: `RiverSpirit.kt` schreiben**

```kotlin
package com.prehmus.selli.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.prehmus.selli.R
import kotlin.math.roundToInt

/**
 * Der Flussgeist als Begleitfigur des Homescreens: driftet langsam und schwebend, ohne je
 * anzukommen. Die beiden Achsen laufen mit teilerfremden Dauern, damit die Bewegung nicht
 * sichtbar taktet, und werden im Layout-Block gelesen statt in der Komposition — so löst
 * jeder Frame nur ein Re-Layout aus, keine Recomposition.
 */
@Composable
fun RiverSpirit(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "river-spirit")
    val drift by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "vertical-drift",
    )
    val sway by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "horizontal-sway",
    )
    val density = LocalDensity.current
    val verticalRange = with(density) { 10.dp.toPx() }
    val horizontalRange = with(density) { 14.dp.toPx() }

    Image(
        painter = painterResource(R.drawable.spirit_river),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(96.dp)
            .offset {
                IntOffset(
                    x = (sway * horizontalRange).roundToInt(),
                    y = (drift * verticalRange).roundToInt(),
                )
            },
    )
}
```

Hinweis: `Modifier.offset { }` braucht
`import androidx.compose.foundation.layout.offset`.

- [ ] **Step 3: `HomeScreen.kt` schreiben**

```kotlin
package com.prehmus.selli.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.countdown.calculateWirZeitCountdown
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.ui.calendar.CalendarUiState
import java.time.LocalDateTime
import kotlinx.coroutines.delay

/**
 * Das zentrale Ziel: die Wanderweg-Szene zur nächsten Wir-Zeit, der Flussgeist als
 * Begleitfigur und darunter die nächsten gemeinsamen Termine. Nimmt auf, was früher in den
 * Kalender-Header gequetscht war, und hat jetzt Platz dafür.
 *
 * Tickt minütlich, solange diese Composable in der Komposition ist — kein Hintergrundlauf.
 */
@Composable
fun HomeScreen(
    uiState: CalendarUiState,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = LocalDateTime.now()
        }
    }
    val countdown = calculateWirZeitCountdown(event = uiState.nextWirZeitEvent, now = now)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "scene") {
            Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                WirZeitJourneyScene(
                    countdown = countdown,
                    modifier = Modifier.fillMaxSize(),
                )
                RiverSpirit(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 4.dp),
                )
            }
        }
        item(key = "heading") {
            Text(
                text = "Nächste gemeinsame Termine",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (uiState.upcomingWirZeitEvents.isEmpty()) {
            item(key = "empty") { NoSharedEventsHint() }
        } else {
            items(uiState.upcomingWirZeitEvents, key = { event -> event.id }) { event ->
                SharedEventCard(event = event, onClick = { onEventClick(event) })
            }
        }
    }
}
```

`WirZeitJourneyScene` erwartet laut Bestand `countdown` und `modifier` — die exakte
Signatur beim Verschieben in Step 1 prüfen und den Aufruf daran anpassen, statt sie
zu erraten.

- [ ] **Step 4: `SharedEventCard` und `NoSharedEventsHint` in derselben Datei ergänzen**

```kotlin
/** Termin-Karte im Selli-Look: Gradient-Rand als „gemeinsam"-Kennung, weiche Ecken. */
@Composable
private fun SharedEventCard(
    event: CalendarEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, selliGradient()),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(text = event.title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = EventTimeFormatter.formatRange(event),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            event.location?.takeUnless(String::isBlank)?.let { location ->
                Text(
                    text = location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Leerzustand: das Maskottchen grübelt, wenn nichts Gemeinsames ansteht. */
@Composable
private fun NoSharedEventsHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.mascot_pondering),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        Text(
            text = "Noch keine Wir-Zeit geplant",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = "Legt im Kalender einen Termin an und wählt „Wir-Zeit“ als Kategorie.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
```

**Wichtig:** `EventTimeFormatter.formatRange(event)` ist geraten. Vor dem Schreiben
`app/src/main/java/com/prehmus/selli/domain/format/EventTimeFormatter.kt` mit dem
LSP-Tool ansehen und die tatsächlich vorhandene Formatierungsfunktion benutzen —
dieselbe, die `DayDetail.kt` für seine Termin-Karten verwendet, damit die Zeitangabe
auf Homescreen und Tagesdetail identisch aussieht.

- [ ] **Step 5: Homescreen in der Shell verdrahten**

In `SelliShell.kt`:

```kotlin
            composable(SelliDestination.HOME.route) {
                val uiState by calendarViewModel.uiState.collectAsState()
                HomeScreen(
                    uiState = uiState,
                    onEventClick = { event ->
                        navController.navigate(SelliDestination.CALENDAR.route) {
                            popUpTo(SelliStartDestination.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                        calendarViewModel.openDeepLinkedEvent(event.start.toLocalDate(), event.key())
                    },
                )
            }
```

`event.key()` ist die bestehende Extension aus `domain/model` — Import nicht
vergessen (bekannte Codex-Falle, siehe Projektgedächtnis).

- [ ] **Step 6: `MascotHeader.kt` löschen**

```bash
git rm app/src/main/java/com/prehmus/selli/ui/calendar/MascotHeader.kt
```

- [ ] **Step 7: Build und Tests laufen lassen**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL. Erwartbarer Stolperstein: der Kotlin-Compiler bricht
nach dem **ersten** Fehler ab — nach jedem Fix erneut bauen, nicht annehmen, es sei
der einzige.

- [ ] **Step 8: Kontrast prüfen**

`ui-reviewer`-Subagent auf `app/src/main/java/com/prehmus/selli/ui/home/` laufen
lassen.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/ui
git commit -m "Homescreen mit Wanderweg-Szene, Flussgeist und gemeinsamen Terminen"
```

---

## Task 6: Profil- und Einstellungsbereich

**Owner:** Claude

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/ui/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/prehmus/selli/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/prehmus/selli/ui/shell/SelliShell.kt`

**Interfaces:**
- Consumes: `SessionRepository.sessionState()`, `AppDependencies.sessionRepository`,
  `CalendarViewModel.openCustomizationManager/removeCustomization/deleteFromCustomizationManager/dismissCustomizationManager`,
  `CustomizationManagerSheet` (alle bestehend)
- Produces: `data class SettingsUiState(val ownAccount: Account?, val partnerAccount: Account?)`
- Produces: `class SettingsViewModel(sessionRepository: SessionRepository)` mit
  `val uiState: StateFlow<SettingsUiState>` und
  `companion object { fun factory(sessionRepository: SessionRepository): ViewModelProvider.Factory }`
- Produces: `@Composable fun SettingsScreen(viewModel: SettingsViewModel, calendarViewModel: CalendarViewModel, onBack: () -> Unit, onSwitchAccount: () -> Unit, modifier: Modifier)`

- [ ] **Step 1: `SettingsViewModel.kt` schreiben**

```kotlin
package com.prehmus.selli.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prehmus.selli.domain.model.Account
import com.prehmus.selli.domain.model.SessionState
import com.prehmus.selli.domain.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Was der Einstellungsbereich über die Verknüpfung weiß — mehr braucht er bewusst nicht. */
data class SettingsUiState(
    val ownAccount: Account? = null,
    val partnerAccount: Account? = null,
)

/**
 * Liest den gespeicherten Verknüpfungsstatus für die Profilanzeige. Bewusst schreibfrei:
 * „Konto wechseln" läuft weiterhin über den [com.prehmus.selli.ui.auth.AuthViewModel],
 * damit es genau eine Stelle gibt, die den Anmeldezustand verändert.
 */
class SettingsViewModel(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val session = runCatching { sessionRepository.sessionState() }
                .getOrDefault(SessionState.SignedOut)
            _uiState.value = when (session) {
                is SessionState.Linked -> SettingsUiState(session.ownAccount, session.partnerAccount)
                is SessionState.NeedsPartner -> SettingsUiState(session.ownAccount, null)
                SessionState.SignedOut -> SettingsUiState()
            }
        }
    }

    companion object {
        fun factory(sessionRepository: SessionRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(sessionRepository) as T
        }
    }
}
```

- [ ] **Step 2: `SettingsScreen.kt` schreiben — Profilkarte**

```kotlin
/**
 * Profil und Einstellungen: eigenes Profilbild, verknüpftes Google-Konto und der Zugang zur
 * Verwaltung der lokalen Ausblendungen/Anpassungen. Bewusst schlicht und erweiterbar —
 * mehr braucht Selli hier aktuell nicht.
 */
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
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileCard(account = state.ownAccount)
            LinkedAccountCard(
                ownAccount = state.ownAccount,
                partnerAccount = state.partnerAccount,
            )
            SettingsRow(
                icon = Icons.Default.VisibilityOff,
                title = "Ausgeblendet & angepasst",
                subtitle = "Lokale Ausblendungen und Änderungen verwalten",
                onClick = calendarViewModel::openCustomizationManager,
            )
            SettingsRow(
                icon = Icons.AutoMirrored.Filled.Logout,
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
```

Der Dialogtext ist wörtlich der bestehende aus `CalendarScreen.kt` — bewusst
identisch, damit sich für Basti/Melli nichts ändert außer dem Ort.

- [ ] **Step 3: Die vier Hilfs-Composables in derselben Datei ergänzen**

```kotlin
/** Zurück-Leiste des Vollbild-Einstellungsbereichs (der globale Header wird hier ausgeblendet). */
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

/** Großes Profilbild auf dem Lila-Grün-Verlauf — das Branding-Element der App. */
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
                    text = "Nicht verknüpft",
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
```

- [ ] **Step 4: Einstellungen in der Shell verdrahten**

```kotlin
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
```

- [ ] **Step 5: Build und Tests laufen lassen**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, alle Tests grün.

- [ ] **Step 6: Kontrast prüfen**

`ui-reviewer`-Subagent auf `app/src/main/java/com/prehmus/selli/ui/settings/`.
Besonders `ProfileCard` — Text auf `selliGradient()` muss `onAccentColor()` sein.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/ui
git commit -m "Profil- und Einstellungsbereich mit verknuepftem Google-Konto"
```

---

## Task 7: Aufräumen und weitergabefertig machen

**Owner:** Claude

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/ui/settings/LayoutPreferences.kt`
- Modify: `CLAUDE.md` (Abschnitt „Bekannter Stand")

- [ ] **Step 1: `headerCollapsed` als funktionslos markieren**

`LayoutPreferences.headerCollapsed` und `updateHeaderCollapsed` haben nach Task 5
keinen Aufrufer mehr. **Nicht löschen** (gespeicherte Werte auf den Geräten sollen
nicht kaputtgehen), sondern den KDoc ergänzen:

```kotlin
    /**
     * Historisch: Ein-/Ausklappen des früheren Kalender-Headers. Seit dem Umzug der
     * Countdown-Szene auf den Homescreen (26.08.2026) ohne Leser — bleibt nur erhalten,
     * damit gespeicherte Werte auf den Geräten nicht ins Leere laufen.
     */
```

- [ ] **Step 2: Prüfen, dass keine Aufrufer verwaist sind**

```bash
grep -rn "MascotHeader\|headerCollapsed\|onManageCustomizations\|onSwitchAccount" \
     app/src/main app/src/test
```

Erwartung: `MascotHeader` gar nicht mehr, `headerCollapsed` nur in
`LayoutPreferences.kt`, `onSwitchAccount` nur in `SelliShell`/`SettingsScreen`/`SelliApp`.

- [ ] **Step 3: Volle Verifikation**

```bash
./gradlew clean testDebugUnitTest assembleDebug
```
Expected: BUILD SUCCESSFUL, alle Tests grün (mindestens 264 + 4 neue aus Task 1
+ 3 aus Task 4a + 1 aus Task 4b).

- [ ] **Step 4: APK an den festen Ablageort kopieren**

```bash
cp app/build/outputs/apk/debug/app-debug.apk selli.apk
```

- [ ] **Step 5: `CLAUDE.md` fortschreiben**

Im Abschnitt „Bekannter Stand" einen Punkt ergänzen, der das neue Grundgerüst
beschreibt (Header + drei Bottom-Navigation-Ziele, Homescreen, Einstellungsbereich)
und auf Spec und Plan verweist.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "Grundgeruest abschliessen: Aufraeumen und Doku fortschreiben"
```

---

## Self-Review

**Spec-Abdeckung**

| Spec-Anforderung | Task |
| --- | --- |
| `navigation-compose`, vier Routen | 1 |
| Schmaler, überall sichtbarer Header mit Profilzugang | 2 |
| Bottom-Navigation, drei Ziele, „Wir" in der Mitte, Start = `home` | 1 |
| Zeitraumtitel + Pfeile + Aktualisieren in den Kalender-Tab | 3 |
| Kalenderansicht unverändert in ein Ziel umgezogen | 3 |
| `upcomingWirZeitEvents` (max. 5) | 4a, 4b |
| Homescreen: Szene, River Spirit, gemeinsame Termine, Leerzustand | 5 |
| Tab-Wechsel-Animation `fadeIn`/`scaleIn` 180 ms | 1 |
| River-Spirit-Drift, teilerfremde Dauern, Layout-Block | 5 |
| Profilkarte + verknüpftes Google-Konto | 6 |
| „Ausgeblendet & angepasst" zieht in die Einstellungen | 6 (entfernt in 3) |
| „Konto wechseln" zieht in die Einstellungen | 6 (entfernt in 3) |
| `headerCollapsed` bleibt bestehen, verliert Leser | 7 |
| APK nach `selli.apk` | 7 |

**Offene Ungenauigkeiten, die der Ausführende auflösen muss** (bewusst so markiert,
statt eine Signatur zu erfinden):

- Task 5 Step 3: exakte Signatur von `WirZeitJourneyScene` beim Verschieben prüfen.
- Task 5 Step 4: tatsächliche Funktion in `EventTimeFormatter` verwenden, dieselbe
  wie `DayDetail.kt`.
- Task 4a/4b Step 1: bestehende Test-Setups der jeweiligen Testdatei
  wiederverwenden, nicht neu erfinden.

**Typkonsistenz geprüft:** `SelliDestination`/`SETTINGS_ROUTE`/`SelliStartDestination`
(Task 1) werden in 2, 3, 5, 6 unter genau diesen Namen benutzt.
`upcomingWirZeitEvents` (4b) wird in 5 unter genau diesem Namen gelesen.
`selectUpcoming(events, now, limit)` (4a) wird in 4b mit genau dieser Signatur
aufgerufen. `SettingsUiState.ownAccount`/`partnerAccount` (6 Step 1) werden in 6
Step 2/3 unter genau diesen Namen gelesen.
