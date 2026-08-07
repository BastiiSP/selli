# Wir-Zeit-Countdown Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Vor dem Start jeder Aufgabe:** Lies `CLAUDE.md` im Projekt-Root und nutze das LSP-Tool, um die betroffenen Dateien zu verstehen, bevor du sie änderst — die unten zitierten Codeausschnitte sind der Stand zum Planungszeitpunkt, keine Kopiervorlage ohne eigene Prüfung.

**Goal:** Ersetzt die kaum genutzte Freie-Zeit-Anzeige im Kalender-Header und die "nächster freier Slot"-Zeile im großen Widget durch einen Countdown zur nächsten Wir-Zeit, visualisiert im ausgeklappten Header als kleine, sanft bewegte Wanderweg-Szene (Maskottchen nähert sich einem Ziel-Symbol).

**Architecture:** Eine neue reine Domain-Funktion (`domain/countdown/WirZeitCountdown.kt`) berechnet Fortschritt (0–1) und Anzeige-Text aus dem bestehenden `NextSharedEventSelector`-Ergebnis plus einem neuen `CalendarEvent.created`-Feld. `CalendarViewModel` hält den nächsten Wir-Zeit-Termin unabhängig vom gerade angezeigten Kalendertag (eigener 30-Tage-Fetch wie im Widget). `MascotHeader` rendert daraus im eingeklappten Zustand nur Text, im ausgeklappten Zustand eine geschichtete Compose-Szene (statische Formen + `animateFloat`-Bewegung, kein neues Animations-Framework). Das große Widget nutzt dieselbe Domain-Funktion für eine vereinfachte Text+Icon-Zeile ohne Live-Ticken.

**Tech Stack:** Kotlin, Jetpack Compose (Header), Jetpack Glance (Widget), JUnit4 (bestehendes Test-Setup, kein neues Test-Framework).

## Global Constraints

- Countdown-Text im Header (minutengenau): `"noch 3 Tage, 4 Std., 22 Min."` — Format exakt wie in der Spec (`docs/superpowers/specs/2026-08-05-wir-zeit-countdown-design.md`).
- Countdown-Text im Widget (nur Tage/Stunden, kein Live-Ticken möglich): `"noch 3 Tage, 4 Std."`
- Header-Ticking: minütlich, nur während die Composable in der Komposition ist (kein Hintergrunddienst, keine Wakelocks).
- `selliGradient()` bleibt der Header-Hintergrund unverändert — keine neue/abgeschwächte Verlaufsfarbe.
- 0 %-Fortschritt = Termin-`created`, 100 % = Termin-Start, geklemmt auf `[0, 1]`; fehlendes `created` (ICS-Importe) → fest 0 %.
- Bestehender einklappbarer Header (`LayoutPreferencesState.headerCollapsed`) wird wiederverwendet, kein neuer Collapse-Mechanismus.
- Das runde, stimmungsreaktive Maskottchen-Icon oben im `MascotHeader` (aktuell an `freeBlocks.isNotEmpty()` gekoppelt) entfällt ersatzlos — nicht zu verwechseln mit `CalendarUiState.bothFreeOnSelectedDay`, das für den Leertag-Hinweis in `DayDetail.kt` unverändert erhalten bleibt und von dieser Änderung **nicht** berührt wird.
- Falls `./gradlew` mit `Unable to locate a Java Runtime` fehlschlägt: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` vor dem Gradle-Aufruf setzen.
- Owner-Feld pro Task ist Codex (per `codex:rescue`-Skill delegieren) oder Claude (Micro-Fix/Ausnahmefall) — siehe jeweiligen Task.
- Die Gehpose in Task 5 ist **Pflicht**, kein optionales Polish — Task 6 baut direkt darauf auf, kein Platzhalter-Asset im fertigen Feature.
- Der Skill `compose-animations` ist global installiert (verfügbar für Claude Code und Codex) und **muss** vor Task 6 konsultiert werden — die Wanderweg-Szene ist das visuelle Herzstück des Features und soll wirklich gut aussehen, nicht nur technisch funktionieren.

---

## Task 1: `CalendarEvent.created`-Feld + Google-Mapper

**Owner:** Codex

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/domain/model/Models.kt:25-52` (`CalendarEvent`)
- Modify: `app/src/main/java/com/prehmus/selli/data/google/GoogleCalendarEventMapper.kt`
- Test: `app/src/test/java/com/prehmus/selli/data/google/GoogleCalendarEventMapperTest.kt` (falls nicht vorhanden: neu anlegen, Paket/Konventionen aus benachbarten Tests im selben Ordner übernehmen)

**Interfaces:**
- Produces: `CalendarEvent.created: LocalDateTime?` (neues Feld, Default `null`, rückwärtskompatibel für alle bestehenden Konstruktor-Aufrufe/Test-Factories).

- [ ] **Step 1: Neues Feld auf `CalendarEvent` ergänzen**

In `Models.kt`, direkt nach `blocksSharedFreeTime`:

```kotlin
data class CalendarEvent(
    // ... bestehende Felder unverändert ...
    val blocksSharedFreeTime: Boolean = !isAllDay,
    /**
     * Zeitpunkt, an dem der Termin ursprünglich angelegt/bekannt wurde (Google `Event.created`).
     * Treibt die Fortschrittsberechnung des Wir-Zeit-Countdowns. Für ICS-Importe (Outlook,
     * Dr.-Plano-Feed) gibt es kein verlässliches Äquivalent → bleibt `null`.
     */
    val created: LocalDateTime? = null,
)
```

- [ ] **Step 2: Schreibe den fehlschlagenden Mapper-Test**

Falls `GoogleCalendarEventMapperTest.kt` noch nicht existiert, neu anlegen mit diesem Inhalt (Google-`Event`/`EventDateTime`/`DateTime` liegen aus `com.google.api.services.calendar.model`/`com.google.api.client.util`):

```kotlin
package com.prehmus.selli.data.google

import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.Person
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleCalendarEventMapperTest {
    private val mapper = GoogleCalendarEventMapper(zoneId = ZoneOffset.UTC)

    @Test
    fun `maps created timestamp from Google event`() {
        val createdInstant = Instant.parse("2026-07-20T09:15:00Z")
        val event = Event().apply {
            id = "abc"
            summary = "Wir-Zeit"
            start = EventDateTime().setDateTime(DateTime(Instant.parse("2026-08-10T18:00:00Z").toEpochMilli()))
            end = EventDateTime().setDateTime(DateTime(Instant.parse("2026-08-10T20:00:00Z").toEpochMilli()))
            created = DateTime(createdInstant.toEpochMilli())
        }

        val result = mapper.toCalendarEvent(
            event = event,
            source = CalendarSource.GOOGLE_OWN,
            owner = Person.BASTI,
            ownEmail = "basti@example.com",
            partnerEmail = null,
        )

        assertEquals(createdInstant.atZone(ZoneOffset.UTC).toLocalDateTime(), result.created)
    }

    @Test
    fun `created is null when Google omits it`() {
        val event = Event().apply {
            id = "abc"
            summary = "Wir-Zeit"
            start = EventDateTime().setDateTime(DateTime(Instant.parse("2026-08-10T18:00:00Z").toEpochMilli()))
            end = EventDateTime().setDateTime(DateTime(Instant.parse("2026-08-10T20:00:00Z").toEpochMilli()))
        }

        val result = mapper.toCalendarEvent(
            event = event,
            source = CalendarSource.GOOGLE_OWN,
            owner = Person.BASTI,
            ownEmail = "basti@example.com",
            partnerEmail = null,
        )

        assertNull(result.created)
    }
}
```

- [ ] **Step 3: Test laufen lassen, Fehlschlag bestätigen**

Run: `./gradlew testDebugUnitTest --tests "com.prehmus.selli.data.google.GoogleCalendarEventMapperTest"`
Expected: FAIL (`created` ist noch nicht im Konstruktor-Aufruf gesetzt / Feld existiert noch nicht, je nach Reihenfolge der Steps — falls Step 1 schon gemacht wurde, schlägt der erste Testfall fehl, weil `toCalendarEvent()` das Feld noch nicht befüllt).

- [ ] **Step 4: Mapper erweitern**

In `GoogleCalendarEventMapper.kt`, im `toCalendarEvent(...)`-Aufruf:

```kotlin
return CalendarEvent(
    id = event.id.orEmpty(),
    title = event.summary.orEmpty(),
    start = event.start.toLocalDateTime(isAllDay),
    end = event.end.toLocalDateTime(isAllDay),
    isAllDay = isAllDay,
    source = source,
    owner = owner,
    isSharedEvent = event.isSharedByAttendees(ownEmail, partnerEmail) || event.hasSelliSharedProperty(),
    location = event.location,
    description = event.description,
    seriesId = event.recurringEventId,
    created = event.created?.toInstant()?.atZone(zoneId)?.toLocalDateTime(),
)
```

(`DateTime.toInstant()` existiert in derselben Datei bereits als private Extension — wiederverwenden, nicht duplizieren.)

- [ ] **Step 5: Test laufen lassen, Erfolg bestätigen**

Run: `./gradlew testDebugUnitTest --tests "com.prehmus.selli.data.google.GoogleCalendarEventMapperTest"`
Expected: PASS (beide Testfälle grün)

- [ ] **Step 6: Vollen Testlauf gegenprüfen**

Run: `./gradlew testDebugUnitTest`
Expected: alle bisherigen Tests weiterhin grün (neues Feld mit Default `null` bricht keine bestehenden `CalendarEvent(...)`-Aufrufe/Test-Factories).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/domain/model/Models.kt \
        app/src/main/java/com/prehmus/selli/data/google/GoogleCalendarEventMapper.kt \
        app/src/test/java/com/prehmus/selli/data/google/GoogleCalendarEventMapperTest.kt
git commit -m "Add created timestamp to CalendarEvent from Google Calendar"
```

---

## Task 2: Wir-Zeit-Countdown — reine Domain-Logik

**Owner:** Codex

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/domain/countdown/WirZeitCountdown.kt`
- Test: `app/src/test/java/com/prehmus/selli/domain/countdown/WirZeitCountdownTest.kt`

**Interfaces:**
- Consumes: `CalendarEvent` (`start: LocalDateTime`, `created: LocalDateTime?` aus Task 1).
- Produces:
  - `enum class WirZeitCountdownState { WALKING, ARRIVED_TODAY, NONE_PLANNED }`
  - `data class WirZeitCountdown(val state: WirZeitCountdownState, val progress: Float, val remainingText: String)`
  - `fun calculateWirZeitCountdown(event: CalendarEvent?, now: LocalDateTime, includeMinutes: Boolean = true): WirZeitCountdown`
  - Diese Signatur ist der Vertrag für Task 3 (ViewModel), Task 4/6 (Header) und Task 7 (Widget).

- [ ] **Step 1: Schreibe die fehlschlagenden Tests**

```kotlin
package com.prehmus.selli.domain.countdown

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.Person
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WirZeitCountdownTest {
    private val now = LocalDateTime.of(2026, 8, 1, 12, 0)

    private fun event(
        start: LocalDateTime,
        created: LocalDateTime? = null,
    ): CalendarEvent = CalendarEvent(
        id = "e1",
        title = "Wir-Zeit",
        start = start,
        end = start.plusHours(2),
        isAllDay = false,
        source = CalendarSource.GOOGLE_OWN,
        owner = Person.BASTI,
        isSharedEvent = true,
        created = created,
    )

    @Test
    fun `no event means none planned`() {
        val result = calculateWirZeitCountdown(event = null, now = now)
        assertEquals(WirZeitCountdownState.NONE_PLANNED, result.state)
        assertEquals("", result.remainingText)
    }

    @Test
    fun `event starting today means arrived`() {
        val result = calculateWirZeitCountdown(event(start = now.plusHours(3)), now = now)
        assertEquals(WirZeitCountdownState.ARRIVED_TODAY, result.state)
    }

    @Test
    fun `event that already started today still counts as arrived`() {
        val result = calculateWirZeitCountdown(event(start = now.minusHours(2)), now = now)
        assertEquals(WirZeitCountdownState.ARRIVED_TODAY, result.state)
    }

    @Test
    fun `future event without created has zero progress`() {
        val result = calculateWirZeitCountdown(
            event(start = now.plusDays(3), created = null),
            now = now,
        )
        assertEquals(WirZeitCountdownState.WALKING, result.state)
        assertEquals(0f, result.progress, 0.0001f)
    }

    @Test
    fun `progress is halfway between created and start`() {
        val created = now.minusDays(2)
        val start = now.plusDays(2)
        val result = calculateWirZeitCountdown(event(start = start, created = created), now = now)
        assertEquals(0.5f, result.progress, 0.01f)
    }

    @Test
    fun `progress clamps to zero when now is before created`() {
        val result = calculateWirZeitCountdown(
            event(start = now.plusDays(5), created = now.plusDays(1)),
            now = now,
        )
        assertEquals(0f, result.progress, 0.0001f)
    }

    @Test
    fun `progress clamps to one when created equals start`() {
        val same = now.plusDays(3)
        val result = calculateWirZeitCountdown(event(start = same, created = same), now = now)
        assertEquals(1f, result.progress, 0.0001f)
    }

    @Test
    fun `remaining text includes days hours and minutes with minutes enabled`() {
        val result = calculateWirZeitCountdown(
            event(start = now.plusDays(3).plusHours(4).plusMinutes(22)),
            now = now,
            includeMinutes = true,
        )
        assertEquals("noch 3 Tage, 4 Std., 22 Min.", result.remainingText)
    }

    @Test
    fun `remaining text omits minutes when disabled for widget`() {
        val result = calculateWirZeitCountdown(
            event(start = now.plusDays(3).plusHours(4).plusMinutes(22)),
            now = now,
            includeMinutes = false,
        )
        assertEquals("noch 3 Tage, 4 Std.", result.remainingText)
    }

    @Test
    fun `remaining text drops zero-valued larger units`() {
        val result = calculateWirZeitCountdown(event(start = now.plusMinutes(22)), now = now)
        assertEquals("noch 22 Min.", result.remainingText)
    }

    @Test
    fun `remaining text uses singular Tag for exactly one day`() {
        val result = calculateWirZeitCountdown(event(start = now.plusDays(1)), now = now)
        assertEquals("noch 1 Tag", result.remainingText)
    }

    @Test
    fun `remaining text falls back to gleich when under a minute and minutes enabled`() {
        val result = calculateWirZeitCountdown(event(start = now.plusSeconds(30)), now = now)
        assertEquals("gleich", result.remainingText)
    }

    @Test
    fun `remaining text falls back to unter einer Stunde when under an hour and minutes disabled`() {
        val result = calculateWirZeitCountdown(
            event(start = now.plusMinutes(30)),
            now = now,
            includeMinutes = false,
        )
        assertEquals("< 1 Std.", result.remainingText)
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

Run: `./gradlew testDebugUnitTest --tests "com.prehmus.selli.domain.countdown.WirZeitCountdownTest"`
Expected: FAIL (Kompilierfehler — `WirZeitCountdown.kt` existiert noch nicht)

- [ ] **Step 3: Implementiere die Domain-Logik**

```kotlin
package com.prehmus.selli.domain.countdown

import com.prehmus.selli.domain.model.CalendarEvent
import java.time.Duration
import java.time.LocalDateTime

/** Sichtbarer Zustand des Wir-Zeit-Countdowns: läuft, heute angekommen, oder nichts geplant. */
enum class WirZeitCountdownState { WALKING, ARRIVED_TODAY, NONE_PLANNED }

/**
 * Ergebnis der Countdown-Berechnung für Header und Widget. [progress] ist nur im Zustand
 * [WirZeitCountdownState.WALKING] aussagekräftig (0f..1f). [remainingText] ist leer, wenn kein
 * Text zu einer Restzeit gehört (ARRIVED_TODAY/NONE_PLANNED) — diese Zustände zeigen stattdessen
 * eigene Illustrationen (siehe MascotHeader/SelliWidget).
 */
data class WirZeitCountdown(
    val state: WirZeitCountdownState,
    val progress: Float,
    val remainingText: String,
)

/**
 * Berechnet den Wir-Zeit-Countdown aus dem nächsten Wir-Zeit-Termin (Ergebnis von
 * `NextSharedEventSelector.select()`, `null` = keine zukünftige Wir-Zeit).
 *
 * [includeMinutes] = `true` für den live tickenden Kalender-Header (minutengenau), `false` für
 * das Widget (nur Tage/Stunden — ein bis zu 30 Minuten alter Snapshot zeigt ohnehin keine
 * Live-Aktualisierung, Minuten würden das nur vortäuschen).
 */
fun calculateWirZeitCountdown(
    event: CalendarEvent?,
    now: LocalDateTime,
    includeMinutes: Boolean = true,
): WirZeitCountdown {
    if (event == null) {
        return WirZeitCountdown(WirZeitCountdownState.NONE_PLANNED, progress = 0f, remainingText = "")
    }
    if (!now.toLocalDate().isBefore(event.start.toLocalDate())) {
        return WirZeitCountdown(WirZeitCountdownState.ARRIVED_TODAY, progress = 1f, remainingText = "")
    }
    val remaining = Duration.between(now, event.start).let { if (it.isNegative) Duration.ZERO else it }
    return WirZeitCountdown(
        state = WirZeitCountdownState.WALKING,
        progress = calculateProgress(created = event.created, start = event.start, now = now),
        remainingText = formatCountdown(remaining, includeMinutes),
    )
}

/**
 * 0f = Termin wurde gerade erst angelegt, 1f = Termin-Start erreicht. Ohne bekanntes `created`
 * (z. B. ICS-Importe) bleibt der Fortschritt fest bei 0f (Weg-Anfang) statt zu raten.
 */
internal fun calculateProgress(created: LocalDateTime?, start: LocalDateTime, now: LocalDateTime): Float {
    if (created == null) return 0f
    val total = Duration.between(created, start)
    if (total.isZero || total.isNegative) return 1f
    val elapsed = Duration.between(created, now)
    if (elapsed.isNegative) return 0f
    if (elapsed >= total) return 1f
    return elapsed.toMillis().toFloat() / total.toMillis().toFloat()
}

/** "noch 3 Tage, 4 Std., 22 Min." (Header) bzw. "noch 3 Tage, 4 Std." (Widget, [includeMinutes] = false). */
internal fun formatCountdown(remaining: Duration, includeMinutes: Boolean): String {
    val totalMinutes = remaining.toMinutes()
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60

    val parts = mutableListOf<String>()
    if (days > 0) parts += if (days == 1L) "1 Tag" else "$days Tage"
    if (hours > 0) parts += "$hours Std."
    if (includeMinutes && minutes > 0) parts += "$minutes Min."

    if (parts.isEmpty()) return if (includeMinutes) "gleich" else "< 1 Std."
    return "noch " + parts.joinToString(", ")
}
```

- [ ] **Step 4: Test laufen lassen, Erfolg bestätigen**

Run: `./gradlew testDebugUnitTest --tests "com.prehmus.selli.domain.countdown.WirZeitCountdownTest"`
Expected: PASS (alle 13 Testfälle grün)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/domain/countdown/WirZeitCountdown.kt \
        app/src/test/java/com/prehmus/selli/domain/countdown/WirZeitCountdownTest.kt
git commit -m "Add pure Wir-Zeit countdown progress and formatting logic"
```

---

## Task 3: ViewModel — unabhängiger Wir-Zeit-Fetch + Tap-Navigation

**Owner:** Codex

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/ui/calendar/CalendarViewModel.kt`
- Test: `app/src/test/java/com/prehmus/selli/ui/calendar/CalendarViewModelTest.kt` (falls diese Datei noch nicht existiert: prüfen, ob `CalendarViewModel` bereits anderweitig getestet wird — falls nicht, mit einem Fake/Test-Double für `CalendarMergeService` analog zu bestehenden Repository-Tests im Projekt neu anlegen; die LSP-Suche nach bestehenden `CalendarMergeService`-Test-Doubles zeigt den richtigen Ort)

**Interfaces:**
- Consumes: `calculateWirZeitCountdown(...)` wird hier NICHT aufgerufen — das ViewModel liefert nur den rohen `CalendarEvent?`, die Countdown-Berechnung (inkl. minütlichem Ticken) lebt in `MascotHeader` (Task 4/5), damit `now` dort lokal gehalten werden kann. `mergeService.mergedEvents(range: DateRange): List<CalendarEvent>` (bestehend), `NextSharedEventSelector().select(events, now)` (bestehend), `openDeepLinkedEvent(day: LocalDate, key: EventKey)` (bestehend, Zeile ~287).
- Produces: `CalendarUiState.nextWirZeitEvent: CalendarEvent?`, `CalendarViewModel.onWirZeitCountdownClick()`.

- [ ] **Step 1: Neues State-Feld ergänzen**

In `CalendarUiState` (nach `storedCustomizations`):

```kotlin
data class CalendarUiState(
    // ... bestehende Felder unverändert ...
    val storedCustomizations: List<EventCustomization> = emptyList(),
    /**
     * Nächster Wir-Zeit-Termin ab *heute*, unabhängig vom gerade im Kalender betrachteten Tag
     * (eigener 30-Tage-Fetch, analog zum Widget). Treibt den Countdown im Kalender-Header.
     */
    val nextWirZeitEvent: CalendarEvent? = null,
) {
    // ... bestehende Properties unverändert ...
}
```

- [ ] **Step 2: `NextSharedEventSelector`-Import ergänzen und Fetch-Funktion schreiben**

Import ergänzen: `import com.prehmus.selli.domain.widget.NextSharedEventSelector`

Neue private Funktion, analog zu `refreshFreeBlocks(day: LocalDate)`:

```kotlin
/**
 * Lädt den nächsten Wir-Zeit-Termin unabhängig vom aktuell angezeigten Kalenderausschnitt —
 * dieselbe 30-Tage-Fensterlogik wie `WidgetRefreshWorker`, damit der Header-Countdown auch
 * dann korrekt ist, wenn man gerade einen anderen Monat/Tag anschaut oder gar nicht blättert.
 */
private fun refreshNextWirZeitEvent() {
    viewModelScope.launch {
        val range = DateRange(start = LocalDate.now(), endInclusive = LocalDate.now().plusDays(30))
        val events = runCatching { mergeService.mergedEvents(range) }.getOrDefault(emptyList())
        val nextEvent = NextSharedEventSelector().select(events = events, now = LocalDateTime.now())
        _uiState.update { it.copy(nextWirZeitEvent = nextEvent) }
    }
}
```

Import für `LocalDateTime` ergänzen, falls noch nicht vorhanden (`java.time.LocalDateTime`).

- [ ] **Step 3: Fetch aus `init` und `refresh()` anstoßen**

In `init { ... }`, nach `refresh()`:

```kotlin
init {
    // ... bestehender Code unverändert ...
    uiState = _uiState.asStateFlow()
    refresh()
    refreshNextWirZeitEvent()
}
```

In `refresh()`, im `try`-Block direkt nach dem `_uiState.update { ... eventsByDay = ... }`-Aufruf (vor `refreshFreeBlocks(...)`):

```kotlin
_uiState.update {
    it.copy(
        isSyncing = false,
        eventsByDay = merged.events.groupByDay(),
        loadErrors = merged.errors,
    )
}
refreshNextWirZeitEvent()
refreshFreeBlocks(_uiState.value.selectedDay)
```

- [ ] **Step 4: Tap-Navigation-Funktion ergänzen**

Nach `openDeepLinkedEvent(...)`:

```kotlin
/** Tippen auf den Wir-Zeit-Countdown im Header: springt zum Tag/Termin der nächsten Wir-Zeit. */
fun onWirZeitCountdownClick() {
    val event = _uiState.value.nextWirZeitEvent ?: return
    openDeepLinkedEvent(day = event.start.toLocalDate(), key = event.key())
}
```

(`key()` ist die bestehende Extension aus `domain/model/EventCustomization.kt`, ggf. Import ergänzen: `import com.prehmus.selli.domain.model.key`.)

- [ ] **Step 5: Manuell/per Test verifizieren**

Falls ein `CalendarViewModelTest` mit Fake-`CalendarMergeService` existiert oder im Zuge dieses Tasks angelegt wird, Testfälle ergänzen:
- `refresh()` befüllt `nextWirZeitEvent` aus dem Fake-Service-Ergebnis.
- `onWirZeitCountdownClick()` mit gesetztem `nextWirZeitEvent` ruft `openDeepLinkedEvent` mit dem korrekten Tag/Key auf (beobachtbar am resultierenden `uiState.selectedDay`/`selectedEvent` nach Ablauf der Coroutine, z. B. mit `runTest`/`advanceUntilIdle` wie in bestehenden Coroutine-Tests des Projekts üblich).
- `onWirZeitCountdownClick()` ohne `nextWirZeitEvent` (null) ändert den State nicht.

Falls kein solches Test-Setup existiert und neu aufgesetzt werden müsste, ist das ein größerer Nebenaufwand als dieser Task rechtfertigt — in dem Fall diesen Schritt überspringen und stattdessen per Gerätetest in Task 4/5 mitverifizieren (dort wird der Countdown ohnehin end-to-end sichtbar).

- [ ] **Step 6: Vollen Testlauf gegenprüfen**

Run: `./gradlew testDebugUnitTest`
Expected: alle Tests grün, keine Regression.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/ui/calendar/CalendarViewModel.kt
git commit -m "Fetch next Wir-Zeit event independently of the visible calendar range"
```

---

## Task 4: `MascotHeader` — Mood-Icon entfernen, funktionalen Countdown verdrahten

**Owner:** Codex

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/ui/calendar/MascotHeader.kt`
- Modify: `app/src/main/java/com/prehmus/selli/ui/calendar/CalendarScreen.kt:147-159` (Aufrufstelle)

**Interfaces:**
- Consumes: `calculateWirZeitCountdown(event, now, includeMinutes = true)` (Task 2), `CalendarUiState.nextWirZeitEvent` (Task 3), `CalendarViewModel::onWirZeitCountdownClick` (Task 3).
- Produces: `MascotHeader`s neue öffentliche Parameter `nextWirZeitEvent: CalendarEvent?` und `onWirZeitCountdownClick: () -> Unit` (ersetzen `freeBlocks`/`onFreeBlockClick`) — Task 6 baut auf denselben Parametern auf und ändert nur die interne Darstellung des ausgeklappten Zustands.

Dieser Task liefert die **funktional vollständige** Version (Text + Tap-Navigation + minütliches Ticken, beide Header-Zustände), aber noch ohne die Wanderweg-Illustration — die kommt in Task 6 als rein visuelle Erweiterung obendrauf, ohne dass sich an den hier definierten Parametern noch etwas ändert.

- [ ] **Step 1: Signatur ändern, tote Free-Time-Codeteile entfernen**

In `MascotHeader.kt`: `freeBlocks: List<FreeTimeBlock>` und `onFreeBlockClick: (FreeTimeBlock) -> Unit` aus der Parameterliste von `MascotHeader(...)` entfernen, stattdessen:

```kotlin
@Composable
fun MascotHeader(
    title: String,
    isSyncing: Boolean,
    nextWirZeitEvent: CalendarEvent?,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRefresh: () -> Unit,
    onManageCustomizations: () -> Unit,
    onSwitchAccount: () -> Unit,
    onWirZeitCountdownClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
```

Entfernen: die `mood`-Variable (Zeilen 72-76), den ganzen `if (!collapsed) { Box(...) { SelliMascot(...) } }`-Block (Zeilen 138-149), den Aufruf von `FreeTimeSection(...)` (Zeilen 152-159), sowie die jetzt toten privaten Funktionen `FreeTimeSection`, `FreeBlockCard`, `formatDuration` komplett (Zeilen 204-290 im Ausgangsstand).

Nicht mehr benötigte Imports entfernen: `com.prehmus.selli.domain.model.FreeTimeBlock`, `com.prehmus.selli.ui.components.MascotMood`, `com.prehmus.selli.ui.components.SelliMascot`, `androidx.compose.foundation.layout.size` (falls nach den Entfernungen sonst nirgends mehr in der Datei genutzt — mit der IDE/LSP gegenprüfen), `androidx.compose.foundation.shape.CircleShape` (nur entfernen, falls `CollapseToggle` sie nicht mehr braucht — tut sie, also **behalten**), `java.time.Duration` (wurde nur von `formatDuration` gebraucht → entfernen).

- [ ] **Step 2: Neue `WirZeitCountdownSection`-Composable mit minütlichem Ticken**

Neue Imports ergänzen: `androidx.compose.runtime.LaunchedEffect`, `androidx.compose.foundation.clickable` (oder `Surface(onClick = ...)` wie andernorts in der Datei — hier `Surface` verwenden, um denselben Ripple-/Klick-Stil wie `CollapseToggle`/`FreeBlockCard` beizubehalten), `com.prehmus.selli.domain.countdown.WirZeitCountdownState`, `com.prehmus.selli.domain.countdown.calculateWirZeitCountdown`, `kotlinx.coroutines.delay`, `java.time.LocalDateTime`.

```kotlin
/**
 * Countdown zur nächsten Wir-Zeit. Eingeklappt nur als Text, ausgeklappt als Wanderweg-Szene
 * (siehe [WirZeitCountdownScene], Task 6). Tippen springt in beiden Zuständen zum Termin.
 * Tickt minütlich, solange diese Composable in der Komposition ist — kein Hintergrundlauf.
 */
@Composable
private fun WirZeitCountdownSection(
    event: CalendarEvent?,
    collapsed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = LocalDateTime.now()
        }
    }
    val countdown = remember(event, now) { calculateWirZeitCountdown(event, now) }

    if (collapsed) {
        Surface(
            onClick = onClick,
            color = androidx.compose.ui.graphics.Color.Transparent,
            modifier = modifier,
        ) {
            Text(
                text = countdownLabel(countdown),
                style = MaterialTheme.typography.labelLarge,
                color = onAccentColor(),
            )
        }
    } else {
        WirZeitCountdownScene(countdown = countdown, onClick = onClick, modifier = modifier)
    }
}

private fun countdownLabel(countdown: com.prehmus.selli.domain.countdown.WirZeitCountdown): String =
    when (countdown.state) {
        WirZeitCountdownState.WALKING -> "${countdown.remainingText} bis zur nächsten Wir-Zeit"
        WirZeitCountdownState.ARRIVED_TODAY -> "Heute ist es soweit — eure Wir-Zeit!"
        WirZeitCountdownState.NONE_PLANNED -> "Noch keine Wir-Zeit geplant"
    }
```

`import androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.remember`, `androidx.compose.runtime.setValue`, `androidx.compose.runtime.getValue` sind in der Datei bereits vorhanden (werden schon von `HeaderMenu` genutzt) — nicht doppelt ergänzen.

**Platzhalter für Task 6:** `WirZeitCountdownScene` wird in diesem Task als einfache Text-Variante implementiert (identisch zur eingeklappten Darstellung, nur ohne Kollaps-Bedingung), damit Task 4 für sich allein vollständig funktioniert und testbar ist:

```kotlin
@Composable
private fun WirZeitCountdownScene(
    countdown: com.prehmus.selli.domain.countdown.WirZeitCountdown,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = countdownLabel(countdown),
            style = MaterialTheme.typography.titleMedium,
            color = onAccentColor(),
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}
```

- [ ] **Step 3: `WirZeitCountdownSection` in `MascotHeader` einhängen**

An der Stelle, an der vorher der Mood-Icon-Block und `FreeTimeSection` standen (nach dem Titel/Navigations-`Row`, vor `CollapseToggle`):

```kotlin
WirZeitCountdownSection(
    event = nextWirZeitEvent,
    collapsed = collapsed,
    onClick = onWirZeitCountdownClick,
    modifier = Modifier.padding(top = 8.dp),
)

CollapseToggle(
    collapsed = collapsed,
    onToggle = onToggleCollapsed,
    modifier = Modifier.padding(top = if (collapsed) 2.dp else 6.dp),
)
```

- [ ] **Step 4: Aufrufstelle in `CalendarScreen.kt` anpassen**

```kotlin
MascotHeader(
    title = calendarHeaderTitle(uiState.viewMode, uiState.visibleMonth, uiState.selectedDay),
    isSyncing = uiState.isSyncing,
    nextWirZeitEvent = uiState.nextWirZeitEvent,
    collapsed = layout.headerCollapsed,
    onToggleCollapsed = { layout.updateHeaderCollapsed(!layout.headerCollapsed) },
    onPrevious = goPrevious,
    onNext = goNext,
    onRefresh = { viewModel.refresh(forceNetwork = true) },
    onManageCustomizations = viewModel::openCustomizationManager,
    onSwitchAccount = { showSwitchAccountDialog = true },
    onWirZeitCountdownClick = viewModel::onWirZeitCountdownClick,
)
```

`uiState.freeBlocksOnSelectedDay`/`viewModel::openCreateSheetForFreeBlock` bleiben an **allen anderen** Stellen unverändert (sie werden weiterhin für `DayDetail`s `bothFree`-Leertag-Hinweis gebraucht, siehe Global Constraints).

- [ ] **Step 5: Build gegenprüfen**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (reine Compose-Umverdrahtung, keine Logikänderung an bestehenden Tests).

Run: `./gradlew testDebugUnitTest`
Expected: alle Tests weiterhin grün (dieser Task berührt keine unit-testbare Logik, nur UI-Verdrahtung).

- [ ] **Step 6: Manuelle Verifikation (Compose Preview / Emulator)**

Diese Datei hat keine automatisierten UI-Tests (Projekt-Konvention: Compose-/Glance-UI wird per Smoke-/Gerätetest geprüft, siehe bestehende `SelliWidget.kt`, für die es ebenfalls keine UI-Tests gibt). Manuell prüfen:
- Eingeklappter Header zeigt die Text-Zeile, kein Absturz ohne `nextWirZeitEvent` (NONE_PLANNED-Text erscheint).
- Ausgeklappter Header zeigt denselben Text (noch ohne Szene, kommt in Task 6).
- Antippen des Textes springt zum richtigen Tag/Termin (`onWirZeitCountdownClick` → `openDeepLinkedEvent`).
- Rundes Mood-Icon ist komplett verschwunden.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/ui/calendar/MascotHeader.kt \
        app/src/main/java/com/prehmus/selli/ui/calendar/CalendarScreen.kt
git commit -m "Replace header mood icon and free-time cards with Wir-Zeit countdown text"
```

---

## Task 5: Asset-Produktion — Gehpose fürs Maskottchen

**Owner:** Claude (in Rücksprache mit Basti — Bildgenerierung, keine Codex-Aufgabe)

**Pflicht-Task, kein optionales Polish.** Die Wanderweg-Szene in Task 6 braucht eine eigene Gehpose für das Maskottchen — keine der bestehenden Posen (`mascot_pushing`, `mascot_celebrating`, `mascot_idle`, `mascot_pondering`, `mascot_empty_state`) zeigt ein seitlich laufendes Maskottchen, und die Szene soll von Anfang an mit dem finalen Asset gebaut werden statt mit einem sichtbar unpassenden Platzhalter.

**Files:**
- Create: `app/src/main/res/drawable-nodpi/mascot_walking.webp`

**Interfaces:**
- Produces: `R.drawable.mascot_walking` — wird in Task 6 direkt für den `WirZeitCountdownState.WALKING`-Zustand verwendet (kein Platzhalter-Umweg über `mascot_pushing`).

- [ ] **Step 1: Prompt formulieren**

Prompt im Stil des bestehenden `STYLE_ANCHOR` aus `tools/generate-assets/generate_assets.py` schreiben: seitliche Gehpose, ein Bein leicht angehoben, Blickrichtung nach vorne/rechts (in Laufrichtung des Pfads in Task 6), sonst identische Rußmännchen-Ästhetik (rundlich, dunkel, große Kulleraugen) wie alle bestehenden Posen. `mascot_idle.webp` als Referenzbild verwenden (klarste Frontalansicht als Basis) — gleiches Muster wie zuvor bei `mascot_pushing` (mit `mascot_traveling` als Stil-Referenz) und `mascot_pondering`.

- [ ] **Step 2: Bild erzeugen**

Zuerst `tools/generate-assets/generate_assets.py` (automatisiertes OpenAI-Bildgenerierungs-Tool, `.env` mit API-Key liegt bereits lokal vor) mit dem neuen Prompt versuchen. Führt das nicht zu einem brauchbaren Ergebnis (bei früheren Posen ist das schon vorgekommen — Text-Prompt-Ansatz trifft nicht immer alle Details), Basti bitten, das Bild stattdessen selbst mit seinem eigenen Bildtool zu erzeugen (zwei Referenzbilder gleichzeitig: bestehende Pose als Stil-Referenz + der neue Prompt) und in Downloads abzulegen — das war der zuverlässigere Weg bei den letzten beiden neuen Posen.

- [ ] **Step 3: Freistellen und verifizieren**

Mit `rembg` (Python-API direkt, nicht die CLI — hatte zuvor fehlende Dependencies) freistellen. Echte Transparenz **per Pixel-Histogramm verifizieren, nicht der Vorschau vertrauen** — bei mehreren früheren Assets enthielt das Rohbild aus Downloads ein aufgemaltes Schachbrettmuster statt echter Transparenz (RGB statt RGBA). Auf 512×512 skalieren (Standardgröße aller Mascot-Assets).

- [ ] **Step 4: Einsetzen**

Als `mascot_walking.webp` unter `app/src/main/res/drawable-nodpi/` ablegen. Rohbild aus Downloads und Zwischendateien in `/tmp` danach löschen (wie bei allen bisherigen Asset-Runden).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/drawable-nodpi/mascot_walking.webp
git commit -m "Add walking mascot pose for the Wir-Zeit countdown path scene"
```

---

## Task 6: `MascotHeader` — Wanderweg-Szene für den ausgeklappten Zustand

**Owner:** Codex

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/ui/calendar/MascotHeader.kt` (nur `WirZeitCountdownScene`, aus Task 4)

**Interfaces:**
- Consumes: `WirZeitCountdown` (Task 2), unverändert dieselbe Funktionssignatur wie in Task 4 — dieser Task ändert **nur den Funktionskörper** von `WirZeitCountdownScene`, keine Parameter, kein anderer Aufrufer betroffen. `R.drawable.mascot_walking` (Task 5, Pflicht-Voraussetzung für diesen Task — ohne das Asset nicht sinnvoll umsetzbar).

**Qualitätsanspruch:** Diese Szene ist das visuelle Herzstück des Features und muss wirklich gut aussehen, nicht nur technisch funktionieren. Vor dem Schreiben den installierten Skill `compose-animations` (Jetpack-Compose-Animationsprinzipien, u. a. "kleinste passende API wählen", Timing/Choreografie mehrerer Werte) konsultieren und dessen Empfehlungen anwenden, statt nur die untenstehenden Codeausschnitte unverändert zu übernehmen — diese sind ein technischer Ausgangspunkt, keine gestalterische Endabnahme. Nach der Umsetzung auf einem Emulator/Gerät ansehen und bei Bedarf mehrfach nachjustieren (Timing, Easing, Proportionen, Abstände), bevor der Task als fertig gilt.

Rein visuelle Erweiterung, keine neue Logik. Nutzt `R.drawable.mascot_walking` (Task 5) fürs Laufen, `mascot_celebrating` fürs Ankommen, `mascot_pondering` für "nichts geplant".

- [ ] **Step 1: `WirZeitCountdownScene` durch die Wanderweg-Darstellung ersetzen**

Neue Imports: `androidx.compose.foundation.Canvas`, `androidx.compose.foundation.layout.BoxWithConstraints`, `androidx.compose.foundation.layout.height`, `androidx.compose.animation.core.LinearEasing`, `androidx.compose.animation.core.RepeatMode`, `androidx.compose.animation.core.animateFloat`, `androidx.compose.animation.core.infiniteRepeatable`, `androidx.compose.animation.core.rememberInfiniteTransition`, `androidx.compose.animation.core.tween`, `androidx.compose.ui.geometry.Offset`, `androidx.compose.ui.graphics.Path`, `androidx.compose.ui.graphics.PathEffect`, `androidx.compose.ui.graphics.StrokeCap`, `androidx.compose.ui.graphics.drawscope.Stroke`, `androidx.compose.ui.layout.ContentScale`, `androidx.compose.ui.res.painterResource`, `androidx.compose.foundation.Image`, `com.prehmus.selli.R`, `com.prehmus.selli.domain.countdown.WirZeitCountdownState`.

```kotlin
@Composable
private fun WirZeitCountdownScene(
    countdown: com.prehmus.selli.domain.countdown.WirZeitCountdown,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mascotDrawable = when (countdown.state) {
        WirZeitCountdownState.WALKING -> R.drawable.mascot_walking
        WirZeitCountdownState.ARRIVED_TODAY -> R.drawable.mascot_celebrating
        WirZeitCountdownState.NONE_PLANNED -> R.drawable.mascot_pondering
    }

    Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .padding(vertical = 4.dp),
        ) {
            val pathColor = onAccentColor().copy(alpha = 0.55f)
            val widthPx = constraints.maxWidth.toFloat()

            // Sanft driftende Wolken — zwei Formen, die sich unabhängig langsam bewegen.
            val cloudTransition = rememberInfiniteTransition(label = "clouds")
            val cloudOffset1 by cloudTransition.animateFloat(
                initialValue = 0f,
                targetValue = widthPx,
                animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing)),
                label = "cloud1",
            )
            val cloudOffset2 by cloudTransition.animateFloat(
                initialValue = widthPx,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
                label = "cloud2",
            )

            Box(
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset((cloudOffset1 % widthPx).toInt(), 6) }
                    .size(width = 46.dp, height = 16.dp)
                    .background(onAccentColor().copy(alpha = 0.28f), RoundedCornerShape(50)),
            )
            Box(
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset((cloudOffset2 % widthPx).toInt(), 26) }
                    .size(width = 34.dp, height = 12.dp)
                    .background(onAccentColor().copy(alpha = 0.22f), RoundedCornerShape(50)),
            )

            // Geschwungener Pfad von links nach rechts, gestrichelt.
            Canvas(modifier = Modifier.fillMaxWidth().height(96.dp)) {
                val path = Path().apply {
                    moveTo(0f, size.height * 0.85f)
                    cubicTo(
                        size.width * 0.25f, size.height * 0.55f,
                        size.width * 0.55f, size.height * 0.95f,
                        size.width * 0.9f, size.height * 0.35f,
                    )
                }
                drawPath(
                    path = path,
                    color = pathColor,
                    style = Stroke(
                        width = 6.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 14.dp.toPx())),
                    ),
                )
            }

            // Ziel-Symbol am rechten Wegende.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .size(32.dp)
                    .background(onAccentColor().copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Favorite,
                    contentDescription = "Ziel: nächste Wir-Zeit",
                    tint = onAccentColor(),
                    modifier = Modifier.size(18.dp),
                )
            }

            // Maskottchen-Position entlang des Fortschritts — läuft von links (0%) zum Ziel (100%).
            val mascotBreathing by cloudTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(2000), repeatMode = RepeatMode.Reverse),
                label = "mascotBreathing",
            )
            val mascotX = (widthPx - 40.dp.toPxOrZero()) * countdown.progress.coerceIn(0f, 1f)
            Image(
                painter = painterResource(mascotDrawable),
                contentDescription = null,
                modifier = Modifier
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            mascotX.toInt(),
                            (-mascotBreathing * 3).toInt(),
                        )
                    }
                    .size(40.dp)
                    .align(Alignment.BottomStart),
            )

            Text(
                text = countdownLabel(countdown),
                style = MaterialTheme.typography.labelLarge,
                color = onAccentColor(),
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

@Composable
private fun androidx.compose.ui.unit.Dp.toPxOrZero(): Float =
    with(androidx.compose.ui.platform.LocalDensity.current) { this@toPxOrZero.toPx() }
```

**Hinweis für die Umsetzung:** Der obige Code ist bewusst mit vollqualifizierten Typnamen (`androidx.compose.ui.unit.IntOffset` etc.) geschrieben, um Import-Kollisionen mit bereits vorhandenen Wildcard-ähnlichen Importen in `MascotHeader.kt` zu vermeiden — beim Umsetzen in echte, saubere Imports auflösen (LSP-Tool/IDE-Autocomplete nutzen) statt die vollqualifizierten Pfade im Code zu belassen. Prüfe außerdem, ob `Icons.Filled.Favorite` im Projekt bereits genutzt wird (Icon-Bibliothek ist `androidx.compose.material.icons.Icons`, wie an anderer Stelle in dieser Datei schon importiert) — falls das Material-Icons-Extended-Artefakt nicht eingebunden ist, ein bereits im Projekt vorhandenes Icon als Ziel-Symbol verwenden.

- [ ] **Step 2: Build gegenprüfen**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

Run: `./gradlew testDebugUnitTest`
Expected: alle Tests weiterhin grün (rein visuelle Änderung).

- [ ] **Step 3: Gerätetest (adb Smoke-Test wie bei bisherigen Header-/Widget-Änderungen)**

- Ausgeklappter Header mit einer bevorstehenden Wir-Zeit: Wolken driften sichtbar, Pfad ist erkennbar, Maskottchen steht ungefähr an der erwarteten Fortschritts-Position (grob mit einem Testtermin verifizieren, dessen `created` bekannt ist — z. B. selbst angelegter Termin, `created` = jetzt, Start in 4 Tagen → Maskottchen sollte nahe am linken Rand stehen).
- Termin heute: Szene zeigt die Ankommen-Illustration (`mascot_celebrating`).
- Keine Wir-Zeit geplant: Szene zeigt die Warte-Illustration (`mascot_pondering`).
- Antippen der Szene springt zum Termin.
- Eingeklappt/Ausgeklappt-Umschalten bleibt weich (bestehendes `animateContentSize()` auf der äußeren Box).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/ui/calendar/MascotHeader.kt
git commit -m "Add animated path scene to the expanded Wir-Zeit countdown header"
```

---

## Task 7: Widget — `NextFreeSlotRow` durch Countdown-Zeile ersetzen

**Owner:** Codex

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/ui/widget/SelliWidget.kt`
- Modify: `app/src/main/java/com/prehmus/selli/data/widget/WidgetRefreshWorker.kt`

**Interfaces:**
- Consumes: `calculateWirZeitCountdown(event, now, includeMinutes = false)` (Task 2), `WidgetSnapshot.nextSharedEvent` (bereits vorhanden, keine neue Datenquelle nötig), `WidgetSnapshot.updatedAt` (bereits vorhanden — als `now`-Ersatz für die Zeile-3-Berechnung verwenden, da der Snapshot nur beim Refresh entsteht).
- Nicht betroffen: `WidgetSnapshot.nextFreeSlot`/`FreeSlot`-Datenmodell und `WidgetSnapshotCodec` bleiben unverändert (Rückwärtskompatibilität alter Snapshots) — das Feld wird schlicht nicht mehr befüllt/gelesen, kein Migrations-Aufwand nötig.

- [ ] **Step 1: `nextFreeSlot`-Berechnung aus dem Worker entfernen**

In `WidgetRefreshWorker.kt`: den Aufruf `nextFreeSlot = nextFreeSlot(range = range, events = events, now = now),` aus dem `WidgetSnapshot(...)`-Konstruktor-Aufruf entfernen (Feld bleibt auf seinem Default `null`), die private Funktion `nextFreeSlot(...)` (Zeilen 74-82) komplett löschen, sowie die dadurch ungenutzten Imports `com.prehmus.selli.domain.merge.SharedFreeTimeCalculator` und `com.prehmus.selli.domain.model.FreeSlot` entfernen (mit LSP/IDE gegenprüfen, ob wirklich nichts anderes in der Datei sie noch braucht).

```kotlin
val snapshot = WidgetSnapshot(
    partnerPerson = partner.person,
    partnerDisplayName = partner.displayName,
    nextEvent = nextEvent,
    updatedAt = now,
    nextSharedEvent = NextSharedEventSelector().select(events = events, now = now),
)
```

- [ ] **Step 2: `NextFreeSlotRow` durch `WirZeitCountdownRow` ersetzen**

In `SelliWidget.kt`: Import ergänzen `com.prehmus.selli.domain.countdown.WirZeitCountdownState` und `com.prehmus.selli.domain.countdown.calculateWirZeitCountdown`. Import `com.prehmus.selli.domain.model.FreeSlot` entfernen, sobald `NextFreeSlotRow`/`formatFreeSlot` gelöscht sind (siehe Step 3).

Im `allIntendedRowsEmpty`-Ausdruck für `WidgetTier.LARGE` bleibt `snapshot.nextFreeSlot == null`-Vergleich **entfernen** — Zeile 3 zeigt jetzt denselben `nextSharedEvent` wie Zeile 2, daher entscheidet dessen Vorhandensein nicht zusätzlich über den globalen Leerzustand der Karte (sonst würde die Karte fälschlich "leer" wirken, obwohl Zeile 2 bereits etwas anzeigt):

```kotlin
val allIntendedRowsEmpty = when (widgetTier) {
    WidgetTier.SMALL -> snapshot.nextEvent == null
    WidgetTier.MEDIUM, WidgetTier.LARGE ->
        snapshot.nextEvent == null && snapshot.nextSharedEvent == null
}
```

Im `WidgetCard`-Rendering, `WidgetTier.LARGE`-Zweig:

```kotlin
if (widgetTier == WidgetTier.LARGE) {
    Spacer(modifier = GlanceModifier.height(8.dp))
    WirZeitCountdownRow(event = snapshot.nextSharedEvent, now = snapshot.updatedAt)
}
```

- [ ] **Step 3: `NextFreeSlotRow`/`formatFreeSlot`/`freeSlotTimeFormatter` durch die neue Composable ersetzen**

`NextFreeSlotRow` (Zeilen 288-318), `formatFreeSlot` (Zeilen 368-371) und `freeSlotTimeFormatter` (Zeile 349) komplett löschen, dafür:

```kotlin
@Composable
private fun WirZeitCountdownRow(event: CalendarEvent?, now: java.time.LocalDateTime) {
    val countdown = calculateWirZeitCountdown(event = event, now = now, includeMinutes = false)
    val mascot = when (countdown.state) {
        WirZeitCountdownState.WALKING -> R.drawable.mascot_pondering
        WirZeitCountdownState.ARRIVED_TODAY -> R.drawable.mascot_celebrating
        WirZeitCountdownState.NONE_PLANNED -> R.drawable.mascot_empty_state
    }
    val valueText = when (countdown.state) {
        WirZeitCountdownState.WALKING -> countdown.remainingText
        WirZeitCountdownState.ARRIVED_TODAY -> "Heute ist es soweit!"
        WirZeitCountdownState.NONE_PLANNED -> "Nichts in Sicht"
    }

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(mascot),
            contentDescription = null,
            modifier = GlanceModifier.size(32.dp),
        )
        Spacer(modifier = GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = "Nächste Wir-Zeit in",
                style = TextStyle(color = TextSoft, fontSize = 12.sp),
                maxLines = 1,
            )
            Text(
                text = valueText,
                style = TextStyle(color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    }
}
```

(`java.time.LocalDateTime` bereits importierbar, ggf. sauberen Import statt vollqualifiziertem Pfad in der Parameterliste ergänzen.)

- [ ] **Step 4: Build gegenprüfen**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Bestehende Codec-Tests gegenprüfen**

Run: `./gradlew testDebugUnitTest --tests "com.prehmus.selli.data.widget.WidgetSnapshotCodecTest"`
Expected: PASS, unverändert (Codec/Feld wurden nicht angefasst, siehe Interfaces oben).

Run: `./gradlew testDebugUnitTest`
Expected: alle Tests grün.

- [ ] **Step 6: Gerätetest**

- Großes Widget zeigt Zeile 3 jetzt als Countdown-Text ("Nächste Wir-Zeit in" / "noch 3 Tage, 4 Std.") mit passendem Maskottchen-Icon.
- Leerzustand ("Nichts in Sicht") und Heute-Zustand ("Heute ist es soweit!") korrekt.
- Widget-Refresh (30-Minuten-Job oder manuell über App-Öffnen) aktualisiert die Zeile weiterhin.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/ui/widget/SelliWidget.kt \
        app/src/main/java/com/prehmus/selli/data/widget/WidgetRefreshWorker.kt
git commit -m "Replace widget's next-free-slot row with a Wir-Zeit countdown row"
```

---

## Selbstprüfung (bereits durchgeführt)

- **Spec-Abdeckung:** Alle Abschnitte aus `docs/superpowers/specs/2026-08-05-wir-zeit-countdown-design.md` sind abgedeckt — `created`-Feld (Task 1), Fortschritts-/Text-Logik inkl. aller Randfälle (Task 2), unabhängiger 30-Tage-Fetch (Task 3), Header-Verhalten inkl. Tap/Ticken (Task 4), Gehpose (Task 5, Pflicht statt optional — auf Bastis Wunsch), Wanderweg-Visualisierung (Task 6), Widget-Zeile (Task 7). Lottie-Animation bleibt bewusst zurückgestellt (siehe Spec, "Zurückgestellt").
- **Platzhalter-Scan:** Keine TBD/TODO-Stellen; Task 6 nutzt direkt das in Task 5 produzierte finale Asset statt eines Platzhalters.
- **Typkonsistenz:** `calculateWirZeitCountdown(event, now, includeMinutes)`, `WirZeitCountdown`, `WirZeitCountdownState` werden in Task 2 definiert und in Task 4/6/7 identisch verwendet; `MascotHeader`s neue Parameter (`nextWirZeitEvent`, `onWirZeitCountdownClick`) sind zwischen Task 4 und der Aufrufstelle in `CalendarScreen.kt` konsistent.
- **Nicht angetastet (bewusst):** `CalendarUiState.freeBlocksOnSelectedDay`/`bothFreeOnSelectedDay`/`refreshFreeBlocks`/`openCreateSheetForFreeBlock` bleiben für `DayDetail`s Leertag-Hinweis vollständig erhalten (siehe Global Constraints) — kein Task dieses Plans verändert sie.
- **Reihenfolge/Abhängigkeiten:** Task 5 (Asset) muss vor Task 6 (Szene) abgeschlossen sein, sonst fehlt `R.drawable.mascot_walking`. Task 5 kann parallel zu Task 3/4 laufen (keine Code-Abhängigkeit), Task 7 (Widget) kann parallel zu Task 4–6 laufen, sobald Task 2 fertig ist (beide hängen nur von der reinen Domain-Logik ab, nicht voneinander).
