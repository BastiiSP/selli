# Termin entfernen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Vor dem Start jeder Aufgabe:** Lies `CLAUDE.md` im Projekt-Root und nutze das LSP-Tool, um die betroffenen Dateien zu verstehen, bevor du sie änderst — die unten zitierten Codeausschnitte sind der Stand zum Planungszeitpunkt, keine Kopiervorlage ohne eigene Prüfung.

**Goal:** Jeder Termin bekommt eine "Entfernen"-Aktion im `EventActionsSheet` und im
"Ausgeblendet & angepasst"-Verwaltungsscreen. Was dabei passiert, hängt vom Termintyp ab:
eigene Termine (inkl. eigener Wir-Zeit) werden wie bisher echt gelöscht; Wir-Zeit-Termine
der Partnerin lösen eine Lösch-Anfrage aus (Markierung + Benachrichtigung); alles andere
wird weiterhin nur lokal ausgeblendet.

**Architecture:** Ein neues `extendedProperties.shared`-Feld (`selli:deleteRequestedBy`)
auf dem Google-Termin selbst überträgt die Lösch-Anfrage — dasselbe Muster wie die
bestehende `selli:shared`-Markierung, kein neues Backend. Ein neuer, kleiner Detector
erkennt beim bestehenden 30-Minuten-Hintergrund-Job, wenn ein **eigener** Wir-Zeit-Termin
diese Markierung neu trägt, und speist das in die bestehende Benachrichtigungs-Pipeline
ein. `EventCustomization` bekommt ein zusätzliches Feld, damit der Verwaltungsscreen auch
für bereits ausgeblendete Termine weiß, welche Aktion sinnvoll ist.

**Tech Stack:** Kotlin, Jetpack Compose, Google Calendar API (`extendedProperties.shared`),
bestehendes `SharedPreferences`-basiertes Notification-State, JUnit4.

## Global Constraints

- Vier Fälle, siehe Spec (`docs/superpowers/specs/2026-08-07-termin-entfernen-design.md`):
  1. `GOOGLE_OWN` (jede Kategorie) → echtes Löschen, **unverändert, keine Codeänderung**.
  2. `GOOGLE_OWN` + `TOGETHER` → echtes Löschen, **unverändert** (Google storniert beim
     Partner automatisch mit).
  3. `GOOGLE_PARTNER` + `TOGETHER` → **neu:** Lösch-Anfrage statt Löschen.
  4. Alles andere → **unverändert:** nur lokales Ausblenden, kein Sync zwischen Geräten.
- Kein neues Backend. Der einzige neue "geteilte" Speicherort ist
  `extendedProperties.shared` auf dem Google-Termin selbst.
- **Nicht garantiert, muss in Task 2 zuerst verifiziert werden:** ob ein eingeladener
  Teilnehmer (nicht Organisator) über `calendar.events().get/update(calendarId="primary", eventId=<eigene ID>)`
  `extendedProperties.shared` auf seiner eigenen Kopie eines fremden Termins schreiben
  darf. Fallback bei Fehlschlag: lokales Ausblenden passiert trotzdem, Fehler wird
  abgefangen und als Hinweis angezeigt statt die App abstürzen zu lassen.
- Falls `./gradlew` mit `Unable to locate a Java Runtime` fehlschlägt:
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- Alle Tasks: Owner Codex (per `codex:rescue`-Skill delegieren).

---

## Task 1: Datenmodell — `deleteRequestedBy`, `Person.other()`, `EventCustomization.originalCategory`

**Owner:** Codex

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/domain/model/Models.kt` (`Person`, `CalendarEvent`)
- Modify: `app/src/main/java/com/prehmus/selli/domain/model/EventCustomization.kt` (`EventCustomization`)
- Modify: `app/src/main/java/com/prehmus/selli/data/google/GoogleCalendarEventMapper.kt`
- Modify: `app/src/main/java/com/prehmus/selli/data/customization/EventCustomizationCodec.kt`
- Test: `app/src/test/java/com/prehmus/selli/data/google/GoogleCalendarEventMapperTest.kt`
- Test: `app/src/test/java/com/prehmus/selli/data/customization/EventCustomizationCodecTest.kt` (falls nicht vorhanden, mit LSP/Suche prüfen, sonst neu anlegen nach Muster der Mapper-Tests)

**Interfaces:**
- Produces: `CalendarEvent.deleteRequestedBy: Person?` (Default `null`), `fun Person.other(): Person`,
  `EventCustomization.originalCategory: EventCategory?` (Default `null`).

- [ ] **Step 1: `Person.other()` ergänzen**

In `Models.kt`, nach `enum class Person { BASTI, MELLI }`:

```kotlin
fun Person.other(): Person = if (this == Person.BASTI) Person.MELLI else Person.BASTI
```

- [ ] **Step 2: `CalendarEvent.deleteRequestedBy` ergänzen**

In `Models.kt`, `CalendarEvent`, nach `created`:

```kotlin
data class CalendarEvent(
    // ... bestehende Felder unverändert ...
    val created: LocalDateTime? = null,
    /**
     * Person, die über die Lösch-Anfrage-Markierung (`extendedProperties.shared`) um
     * Löschung dieses Wir-Zeit-Termins gebeten hat — nur bei `category == TOGETHER`
     * relevant. `null` = keine offene Anfrage.
     */
    val deleteRequestedBy: Person? = null,
)
```

- [ ] **Step 3: Fehlschlagenden Mapper-Test schreiben**

Ergänze in `GoogleCalendarEventMapperTest.kt` (Muster wie bestehende `created`-Tests):

```kotlin
@Test
fun `maps delete request marker from Google event`() {
    val event = Event().apply {
        id = "abc"
        summary = "Wir-Zeit"
        start = EventDateTime().setDateTime(DateTime(Instant.parse("2026-08-10T18:00:00Z").toEpochMilli()))
        end = EventDateTime().setDateTime(DateTime(Instant.parse("2026-08-10T20:00:00Z").toEpochMilli()))
        extendedProperties = Event.ExtendedProperties().setShared(
            mapOf("selli:deleteRequestedBy" to "BASTI"),
        )
    }

    val result = mapper.toCalendarEvent(
        event = event,
        source = CalendarSource.GOOGLE_OWN,
        owner = Person.MELLI,
        ownEmail = "melli@example.com",
        partnerEmail = null,
    )

    assertEquals(Person.BASTI, result.deleteRequestedBy)
}

@Test
fun `delete request marker is null when absent`() {
    val event = Event().apply {
        id = "abc"
        summary = "Wir-Zeit"
        start = EventDateTime().setDateTime(DateTime(Instant.parse("2026-08-10T18:00:00Z").toEpochMilli()))
        end = EventDateTime().setDateTime(DateTime(Instant.parse("2026-08-10T20:00:00Z").toEpochMilli()))
    }

    val result = mapper.toCalendarEvent(
        event = event,
        source = CalendarSource.GOOGLE_OWN,
        owner = Person.MELLI,
        ownEmail = "melli@example.com",
        partnerEmail = null,
    )

    assertNull(result.deleteRequestedBy)
}
```

- [ ] **Step 4: Test laufen lassen, Fehlschlag bestätigen**

Run: `./gradlew testDebugUnitTest --tests "com.prehmus.selli.data.google.GoogleCalendarEventMapperTest"`
Expected: FAIL (Feld existiert noch nicht / wird noch nicht befüllt)

- [ ] **Step 5: Mapper erweitern**

In `GoogleCalendarEventMapper.kt`, Konstante ergänzen (Companion object, neben
`SELLI_SHARED_PROPERTY`):

```kotlin
companion object {
    const val SELLI_SHARED_PROPERTY = "selli:shared"
    const val SELLI_DELETE_REQUESTED_PROPERTY = "selli:deleteRequestedBy"
}
```

Im `toCalendarEvent(...)`-Aufruf:

```kotlin
deleteRequestedBy = event.extendedProperties?.shared
    ?.get(SELLI_DELETE_REQUESTED_PROPERTY)
    ?.let { value -> runCatching { Person.valueOf(value) }.getOrNull() },
```

(`runCatching` statt direktem `valueOf`, damit ein unerwarteter/fremder Wert im Feld nie
zum Absturz beim Mappen führt — defensiv, da das Feld theoretisch von außen manipulierbar
wäre.)

- [ ] **Step 6: Test laufen lassen, Erfolg bestätigen**

Run: `./gradlew testDebugUnitTest --tests "com.prehmus.selli.data.google.GoogleCalendarEventMapperTest"`
Expected: PASS

- [ ] **Step 7: `EventCustomization.originalCategory` ergänzen**

In `EventCustomization.kt`:

```kotlin
data class EventCustomization(
    val target: CustomizationTarget,
    val hidden: Boolean,
    val overrides: EventFieldOverrides = EventFieldOverrides(),
    val label: String = "",
    /**
     * Kategorie des Termins zum Zeitpunkt der Anpassung (nicht die Override-Kategorie
     * aus [overrides] — die tatsächliche, ursprüngliche Kategorie). Ausgeblendete Termine
     * fehlen im gefilterten, zusammengeführten Kalender komplett — der Verwaltungsscreen
     * braucht diesen Wert, um zu wissen, ob "Löschen"/"Löschen anfragen" sinnvoll ist.
     * `null` bei älteren, vor diesem Feld gespeicherten Einträgen.
     */
    val originalCategory: EventCategory? = null,
)
```

- [ ] **Step 8: Codec um das neue Feld erweitern**

In `EventCustomizationCodec.kt`, `StoredCustomization` bekommt ein neues, nullable Feld
`originalCategory: String?` (rein additiv, alte JSON-Dateien ohne dieses Feld deserialisieren
mit Gson automatisch zu `null` — **keine** `FORMAT_VERSION`-Erhöhung nötig):

```kotlin
private data class StoredCustomization(
    // ... bestehende Felder unverändert ...
    val label: String,
    val originalCategory: String? = null,
)
```

In `toStoredCustomization()`: `originalCategory = originalCategory?.name` ergänzen.
In `toDomain()`: `originalCategory = originalCategory?.let { EventCategory.valueOf(it) }` ergänzen.

- [ ] **Step 9: Codec-Test ergänzen (Rückwärtskompatibilität)**

Falls `EventCustomizationCodecTest.kt` existiert, Testfall ergänzen: JSON ohne
`originalCategory`-Feld (alter Stand simulieren, z. B. direkt einen JSON-String ohne dieses
Feld dekodieren) liefert `originalCategory = null`, kein Absturz. Zusätzlich Roundtrip-Test:
encode → decode liefert denselben `originalCategory`-Wert zurück, wenn gesetzt.

- [ ] **Step 10: Vollen Testlauf gegenprüfen**

Run: `./gradlew testDebugUnitTest`
Expected: alle Tests grün, keine Regression (beide neuen Felder sind additiv mit
Default-Werten).

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/domain/model/Models.kt \
        app/src/main/java/com/prehmus/selli/domain/model/EventCustomization.kt \
        app/src/main/java/com/prehmus/selli/data/google/GoogleCalendarEventMapper.kt \
        app/src/main/java/com/prehmus/selli/data/customization/EventCustomizationCodec.kt \
        app/src/test/java/com/prehmus/selli/data/google/GoogleCalendarEventMapperTest.kt
git commit -m "Add delete-request marker field and Person.other() helper"
```

---

## Task 2: Google-Schreibpfad für Lösch-Anfragen

**Owner:** Codex

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/data/google/GoogleCalendarEventSharing.kt`
- Modify: `app/src/main/java/com/prehmus/selli/domain/repository/CalendarRepository.kt`
- Modify: `app/src/main/java/com/prehmus/selli/data/google/GoogleCalendarDataRepository.kt`
- Test: `app/src/test/java/com/prehmus/selli/data/google/GoogleCalendarEventSharingTest.kt` (falls
  vorhanden — Muster für Fake/Mock der `Calendar`-API-Kette von dort übernehmen; falls nicht
  vorhanden, mit LSP prüfen, ob es andere Google-API-Tests mit Mock-Aufbau im Projekt gibt,
  und deren Muster für einen neuen Test wiederverwenden)

**Interfaces:**
- Produces: `CalendarRepository.requestPartnerDeletion(event: CalendarEvent, wholeSeries: Boolean): Result<Unit>`
  — Vertrag für Task 4 (ViewModel).

**Wichtiger erster Schritt — technische Verifikation vor dem eigentlichen Bauen:**
Bevor der Rest dieses Tasks umgesetzt wird, am echten Gerät/Emulator mit einem echten
Wir-Zeit-Termin, den die Partnerin angelegt hat, **einmal manuell per Kotlin-Snippet oder
Testcode** prüfen, ob `calendar.events().get("primary", <eventId des GOOGLE_PARTNER-Termins>)`
auf dem EIGENEN Google-Konto (nicht dem der Partnerin) überhaupt ein Ergebnis liefert (d. h.
ob die eigene Teilnehmer-Kopie unter derselben `eventId` erreichbar ist) und ob ein
anschließendes `update()` mit geändertem `extendedProperties.shared` ohne 403 durchgeht.
**Ergebnis dieser Verifikation im Commit-Text/Claudian-Update festhalten** — das entscheidet,
ob der Rest dieses Tasks wie unten beschrieben funktioniert oder ob zusätzlich das in der
Spec beschriebene Fallback-Verhalten (Fehler abfangen, nur lokal ausblenden) der **einzige**
tatsächlich wirksame Pfad für Fall 3 bleibt (funktional dann identisch zu Fall 4, aber die
Markierung wird zumindest versucht).

- [ ] **Step 1: Fehlschlagenden Test für `GoogleCalendarEventSharing.requestDeletion` schreiben**

Am bestehenden Testaufbau für `setPartnerAttendance`/`updateSelliSharedMarker` orientieren
(Mock/Fake der `Calendar`-Aufrufkette `calendar.events().get(...).execute()` /
`.update(...).execute()`). Kernaussagen: (a) wirft bei `event.source == GOOGLE_OWN`
(eigene Termine brauchen keine Anfrage), (b) wirft bei `event.category != TOGETHER`,
(c) bei Erfolg wird `extendedProperties.shared["selli:deleteRequestedBy"]` auf den
`name` von `requestedBy` gesetzt und `update()` mit `calendarId = "primary"` und der
richtigen `targetId` (Einzelvorkommen vs. `event.seriesId` bei `wholeSeries = true`)
aufgerufen.

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

Run: `./gradlew testDebugUnitTest --tests "com.prehmus.selli.data.google.GoogleCalendarEventSharingTest"`
Expected: FAIL (Methode existiert noch nicht)

- [ ] **Step 3: `GoogleCalendarEventSharing.requestDeletion` implementieren**

```kotlin
fun requestDeletion(
    event: CalendarEvent,
    requestedBy: Person,
    wholeSeries: Boolean,
): Result<Unit> = runCatching {
    require(event.source != CalendarSource.GOOGLE_OWN) {
        "Eigene Termine werden direkt gelöscht, keine Anfrage nötig."
    }
    require(event.category == EventCategory.TOGETHER) {
        "Eine Lösch-Anfrage ist nur für Wir-Zeit-Termine vorgesehen."
    }

    val targetId = event.seriesId.takeIf { wholeSeries } ?: event.id
    val remoteEvent = calendar.events()
        .get(PRIMARY_CALENDAR_ID, targetId)
        .execute()

    remoteEvent.setSharedProperty(GoogleCalendarEventMapper.SELLI_DELETE_REQUESTED_PROPERTY, requestedBy.name)

    calendar.events()
        .update(PRIMARY_CALENDAR_ID, targetId, remoteEvent)
        .setSendUpdates(SEND_UPDATES_NONE)
        .execute()
    Unit
}

/** Generische Variante von [updateSelliSharedMarker] für beliebige geteilte Schlüssel. */
private fun Event.setSharedProperty(key: String, value: String?) {
    val properties = extendedProperties ?: Event.ExtendedProperties()
    val sharedProperties = properties.shared.orEmpty().toMutableMap()
    if (value != null) sharedProperties[key] = value else sharedProperties.remove(key)
    properties.shared = sharedProperties.ifEmpty { null }
    extendedProperties = properties.takeIf {
        !it.shared.isNullOrEmpty() || !it.private.isNullOrEmpty()
    }
}
```

Neue Imports: `com.prehmus.selli.domain.model.EventCategory`, `com.prehmus.selli.domain.model.Person`.
Neue Konstante in der bestehenden `private companion object`: `const val SEND_UPDATES_NONE = "none"`
(bewusst `"none"` statt `"all"` wie bei `setPartnerAttendance` — die Lösch-Anfrage soll nicht
selbst eine Google-Kalender-E-Mail an alle Teilnehmer auslösen, die Benachrichtigung läuft
über Sellis eigenen Kanal).

- [ ] **Step 4: Test laufen lassen, Erfolg bestätigen**

Run: `./gradlew testDebugUnitTest --tests "com.prehmus.selli.data.google.GoogleCalendarEventSharingTest"`
Expected: PASS

- [ ] **Step 5: `CalendarRepository`-Interface erweitern**

```kotlin
interface CalendarRepository {
    // ... bestehende Methoden unverändert ...

    suspend fun requestPartnerDeletion(event: CalendarEvent, wholeSeries: Boolean): Result<Unit> =
        Result.failure(
            UnsupportedOperationException("Lösch-Anfrage wird für diese Quelle nicht unterstützt."),
        )
}
```

- [ ] **Step 6: `GoogleCalendarDataRepository` überschreiben**

```kotlin
override suspend fun requestPartnerDeletion(
    event: CalendarEvent,
    wholeSeries: Boolean,
): Result<Unit> =
    withGoogleCalendarDispatcher(ioDispatcher) {
        runGoogleApiCatching {
            val ownAccount = requireStoredAccount(OWN_PREFIX)
            GoogleCalendarEventSharing(calendar(ownAccount.email))
                .requestDeletion(event = event, requestedBy = ownAccount.person, wholeSeries = wholeSeries)
                .getOrThrow()
        }
    }
```

(Gleiches Muster wie `setPartnerAttendance` direkt darüber — `calendar(ownAccount.email)`
baut den API-Client mit den eigenen Zugangsdaten auf, `ownAccount.person` ist die anfragende
Person selbst.)

- [ ] **Step 7: Vollen Testlauf + Build gegenprüfen**

Run: `./gradlew clean testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, alle Tests grün.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/data/google/GoogleCalendarEventSharing.kt \
        app/src/main/java/com/prehmus/selli/domain/repository/CalendarRepository.kt \
        app/src/main/java/com/prehmus/selli/data/google/GoogleCalendarDataRepository.kt \
        app/src/test/java/com/prehmus/selli/data/google/GoogleCalendarEventSharingTest.kt
git commit -m "Add Google Calendar write path for partner deletion requests"
```

---

## Task 3: Erkennung + Benachrichtigung für Lösch-Anfragen

**Owner:** Codex

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/domain/notification/PartnerSharedEventChangeDetector.kt`
  (nur der `SharedEventChange`-sealed-interface-Teil)
- Create: `app/src/main/java/com/prehmus/selli/domain/notification/SharedEventDeletionRequestDetector.kt`
- Create: `app/src/main/java/com/prehmus/selli/data/notification/DeleteRequestNotificationStore.kt`
- Modify: `app/src/main/java/com/prehmus/selli/domain/notification/SharedEventNotificationFormatter.kt`
- Modify: `app/src/main/java/com/prehmus/selli/data/widget/WidgetRefreshWorker.kt`
- Test: `app/src/test/java/com/prehmus/selli/domain/notification/SharedEventDeletionRequestDetectorTest.kt`
- Test: `app/src/test/java/com/prehmus/selli/domain/notification/SharedEventNotificationFormatterTest.kt`
  (falls vorhanden, sonst neuer minimaler Test für den neuen Zweig)

**Interfaces:**
- Consumes: `CalendarEvent.deleteRequestedBy` (Task 1), `Person.other()` (Task 1).
- Produces: `SharedEventChange.DeleteRequested(event, requestedBy)`,
  `SharedEventDeletionRequestDetector.detect(currentEvents, self, alreadyNotified): SharedEventDeletionRequestResult`.

- [ ] **Step 1: `SharedEventChange.DeleteRequested` ergänzen**

In `PartnerSharedEventChangeDetector.kt`, im `sealed interface SharedEventChange`:

```kotlin
sealed interface SharedEventChange {
    val event: CalendarEvent

    data class New(override val event: CalendarEvent) : SharedEventChange

    data class Updated(
        override val event: CalendarEvent,
        val changedFields: List<String>,
    ) : SharedEventChange

    /** Der Partner hat um Löschung dieses eigenen Wir-Zeit-Termins gebeten. */
    data class DeleteRequested(
        override val event: CalendarEvent,
        val requestedBy: Person,
    ) : SharedEventChange
}
```

Import ergänzen: `com.prehmus.selli.domain.model.Person`. Der bestehende `PartnerSharedEventChangeDetector`
selbst bleibt unverändert — er erzeugt weiterhin nur `New`/`Updated`.

- [ ] **Step 2: Fehlschlagenden Test für `SharedEventDeletionRequestDetector` schreiben**

```kotlin
package com.prehmus.selli.domain.notification

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.EventKey
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.key
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedEventDeletionRequestDetectorTest {
    private val detector = SharedEventDeletionRequestDetector()
    private val now = LocalDateTime.of(2026, 8, 7, 12, 0)

    private fun event(
        id: String = "e1",
        owner: Person = Person.MELLI,
        category: EventCategory = EventCategory.TOGETHER,
        deleteRequestedBy: Person? = Person.BASTI,
    ): CalendarEvent = CalendarEvent(
        id = id,
        title = "Wir-Zeit",
        start = now.plusDays(1),
        end = now.plusDays(1).plusHours(2),
        isAllDay = false,
        source = CalendarSource.GOOGLE_OWN,
        owner = owner,
        isSharedEvent = true,
        category = category,
        deleteRequestedBy = deleteRequestedBy,
    )

    @Test
    fun `detects new delete request on own together event`() {
        val target = event()
        val result = detector.detect(listOf(target), self = Person.MELLI, alreadyNotified = emptySet())

        assertEquals(1, result.changes.size)
        assertEquals(target, result.changes.single().event)
        assertEquals(Person.BASTI, result.changes.single().requestedBy)
        assertTrue(target.key() in result.updatedNotified)
    }

    @Test
    fun `ignores already notified requests`() {
        val target = event()
        val result = detector.detect(listOf(target), self = Person.MELLI, alreadyNotified = setOf(target.key()))

        assertTrue(result.changes.isEmpty())
        assertTrue(target.key() in result.updatedNotified)
    }

    @Test
    fun `ignores events without a request`() {
        val target = event(deleteRequestedBy = null)
        val result = detector.detect(listOf(target), self = Person.MELLI, alreadyNotified = emptySet())

        assertTrue(result.changes.isEmpty())
        assertTrue(result.updatedNotified.isEmpty())
    }

    @Test
    fun `ignores requests on events not owned by self`() {
        val target = event(owner = Person.BASTI)
        val result = detector.detect(listOf(target), self = Person.MELLI, alreadyNotified = emptySet())

        assertTrue(result.changes.isEmpty())
    }

    @Test
    fun `ignores requests on non together events`() {
        val target = event(category = EventCategory.PRIVATE)
        val result = detector.detect(listOf(target), self = Person.MELLI, alreadyNotified = emptySet())

        assertTrue(result.changes.isEmpty())
    }
}
```

- [ ] **Step 3: Test laufen lassen, Fehlschlag bestätigen**

Run: `./gradlew testDebugUnitTest --tests "com.prehmus.selli.domain.notification.SharedEventDeletionRequestDetectorTest"`
Expected: FAIL (Klasse existiert noch nicht)

- [ ] **Step 4: `SharedEventDeletionRequestDetector` implementieren**

```kotlin
package com.prehmus.selli.domain.notification

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.EventKey
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.key

data class SharedEventDeletionRequestResult(
    val changes: List<SharedEventChange.DeleteRequested>,
    /** Alle aktuell offenen Anfragen — beim nächsten Lauf als [alreadyNotified] übergeben. */
    val updatedNotified: Set<EventKey>,
)

/**
 * Erkennt, wenn ein **eigener** Wir-Zeit-Termin neu die Lösch-Anfrage-Markierung des
 * Partners trägt — Gegenstück zu [PartnerSharedEventChangeDetector], das bewusst nur
 * Termine des Partners betrachtet, während eine Lösch-Anfrage auf den eigenen liegt.
 */
class SharedEventDeletionRequestDetector {
    fun detect(
        currentEvents: List<CalendarEvent>,
        self: Person,
        alreadyNotified: Set<EventKey>,
    ): SharedEventDeletionRequestResult {
        val requested = currentEvents.filter { event ->
            event.owner == self &&
                event.category == EventCategory.TOGETHER &&
                event.deleteRequestedBy != null
        }
        val changes = requested
            .filter { event -> event.key() !in alreadyNotified }
            .map { event -> SharedEventChange.DeleteRequested(event, requireNotNull(event.deleteRequestedBy)) }

        return SharedEventDeletionRequestResult(
            changes = changes,
            updatedNotified = requested.map { it.key() }.toSet(),
        )
    }
}
```

- [ ] **Step 5: Test laufen lassen, Erfolg bestätigen**

Run: `./gradlew testDebugUnitTest --tests "com.prehmus.selli.domain.notification.SharedEventDeletionRequestDetectorTest"`
Expected: PASS

- [ ] **Step 6: `DeleteRequestNotificationStore` implementieren**

```kotlin
package com.prehmus.selli.data.notification

import android.content.Context
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.EventKey

/** Merkt sich, für welche Termine schon eine Lösch-Anfrage-Benachrichtigung gezeigt wurde. */
class DeleteRequestNotificationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): Set<EventKey> =
        preferences.getStringSet(KEY_NOTIFIED, emptySet())
            .orEmpty()
            .mapNotNull(::decode)
            .toSet()

    fun save(keys: Set<EventKey>) {
        preferences.edit()
            .putStringSet(KEY_NOTIFIED, keys.map(::encode).toSet())
            .apply()
    }

    private fun encode(key: EventKey): String = "${key.source.name}$SEPARATOR${key.eventId}"

    private fun decode(raw: String): EventKey? {
        val parts = raw.split(SEPARATOR, limit = 2)
        if (parts.size != 2) return null
        val source = runCatching { CalendarSource.valueOf(parts[0]) }.getOrNull() ?: return null
        return EventKey(source = source, eventId = parts[1])
    }

    private companion object {
        const val PREFS_NAME = "selli_delete_request_notifications"
        const val KEY_NOTIFIED = "notified_keys"
        const val SEPARATOR = "|"
    }
}
```

Kein eigener Unit-Test nötig — reiner `SharedPreferences`-Wrapper nach demselben Muster wie
`SharedEventFingerprintStore`, das ebenfalls ungetestet ist (Android-Context-Abhängigkeit).

- [ ] **Step 7: Formatter erweitern**

In `SharedEventNotificationFormatter.kt`, `when (change)` um einen Zweig ergänzen:

```kotlin
is SharedEventChange.DeleteRequested -> {
    title = "$partnerDisplayName möchte einen Termin löschen"
    text = "${change.event.title} – öffnen und löschen, um die Anfrage abzuschließen"
}
```

(Wortlaut Ausführungsdetail — wichtig ist nur, dass klar wird, dass eine Aktion von der
lesenden Person erwartet wird.) Falls ein `SharedEventNotificationFormatterTest` existiert,
Testfall für diesen neuen Zweig ergänzen; falls nicht, keinen neuen Testfall erzwingen (die
Detector-Logik ist in Step 2-5 bereits abgedeckt, der Formatter ist reine String-Zusammensetzung).

- [ ] **Step 8: `WidgetRefreshWorker` verdrahten**

In `notifySharedEventChanges(...)`:

```kotlin
private fun notifySharedEventChanges(events: List<CalendarEvent>, partner: PartnerInfo) {
    val notifier = SharedEventNotifier(applicationContext)
    if (!notifier.areNotificationsEnabled()) return

    val fingerprintStore = SharedEventFingerprintStore(applicationContext)
    val fingerprintResult = PartnerSharedEventChangeDetector().detect(
        currentEvents = events,
        partner = partner.person,
        previouslySeen = fingerprintStore.load(),
    )

    val deleteRequestStore = DeleteRequestNotificationStore(applicationContext)
    val deleteRequestResult = SharedEventDeletionRequestDetector().detect(
        currentEvents = events,
        self = partner.person.other(),
        alreadyNotified = deleteRequestStore.load(),
    )

    notifier.notifyChanges(fingerprintResult.changes + deleteRequestResult.changes, partner.displayName)
    fingerprintStore.save(fingerprintResult.updatedFingerprints)
    deleteRequestStore.save(deleteRequestResult.updatedNotified)
}
```

Neuer Import: `com.prehmus.selli.data.notification.DeleteRequestNotificationStore`,
`com.prehmus.selli.domain.notification.SharedEventDeletionRequestDetector`,
`com.prehmus.selli.domain.model.other` (bzw. der tatsächliche Package-Pfad von `Person.other()`
aus Task 1).

- [ ] **Step 9: Vollen Testlauf + Build gegenprüfen**

Run: `./gradlew clean testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, alle Tests grün.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/domain/notification/PartnerSharedEventChangeDetector.kt \
        app/src/main/java/com/prehmus/selli/domain/notification/SharedEventDeletionRequestDetector.kt \
        app/src/main/java/com/prehmus/selli/data/notification/DeleteRequestNotificationStore.kt \
        app/src/main/java/com/prehmus/selli/domain/notification/SharedEventNotificationFormatter.kt \
        app/src/main/java/com/prehmus/selli/data/widget/WidgetRefreshWorker.kt \
        app/src/test/java/com/prehmus/selli/domain/notification/SharedEventDeletionRequestDetectorTest.kt
git commit -m "Detect and notify about partner delete requests on own Wir-Zeit events"
```

---

## Task 4: ViewModel — Lösch-Anfrage auslösen + sofort lokal ausblenden

**Owner:** Codex

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/ui/calendar/CalendarViewModel.kt`
- Test: `app/src/test/java/com/prehmus/selli/ui/calendar/CalendarViewModelTest.kt` (falls
  vorhanden, wie schon in der Countdown-Runde genutzt — Fake für `CalendarRepository`)

**Interfaces:**
- Consumes: `CalendarRepository.requestPartnerDeletion(event, wholeSeries)` (Task 2).
- Produces: `CalendarViewModel.requestDeletionOfSelectedEvent(wholeSeries: Boolean)` — Vertrag
  für Task 5 (`EventActionsSheet`-Aufrufstelle in `CalendarScreen.kt`).

- [ ] **Step 1: `requestDeletionOfSelectedEvent` implementieren**

Nach `hideSelectedEvent(wholeSeries: Boolean)`:

```kotlin
/**
 * Löst für einen Wir-Zeit-Termin der Partnerin eine Lösch-Anfrage aus: der Termin wird
 * bei einem selbst sofort lokal ausgeblendet (wie [hideSelectedEvent]), zusätzlich wird
 * versucht, eine Markierung auf dem Termin zu hinterlegen, die die Partnerin beim
 * nächsten Sync benachrichtigt. Schlägt Letzteres fehl (z. B. fehlende Schreibrechte),
 * bleibt das lokale Ausblenden trotzdem bestehen — kein Absturz, nur ein Hinweis.
 */
fun requestDeletionOfSelectedEvent(wholeSeries: Boolean) {
    val event = _uiState.value.selectedEvent ?: return
    _uiState.update { it.copy(selectedEvent = null) }
    viewModelScope.launch {
        val requestResult = calendarRepository.requestPartnerDeletion(event, wholeSeries)
        val successMessage = if (requestResult.isSuccess) {
            "Bei dir ausgeblendet — Anfrage an deine Partnerin/deinen Partner geschickt."
        } else {
            "Bei dir ausgeblendet — die Anfrage konnte nicht übermittelt werden, bitte direkt Bescheid geben."
        }
        applyCustomization(
            EventCustomization(
                target = event.customizationTarget(wholeSeries),
                hidden = true,
                label = event.title,
                originalCategory = event.category,
            ),
            successMessage = successMessage,
        )
    }
}
```

- [ ] **Step 2: `applyCustomization`-Aufrufer für `originalCategory` nachziehen**

Damit der Verwaltungsscreen (Task 6) für **alle** neu/erneut gespeicherten Anpassungen den
korrekten Wert hat, in `hideSelectedEvent`, `saveEventCategory` und `saveEventOverrides`
(alle bestehenden `EventCustomization(...)`-Konstruktor-Aufrufe in dieser Datei)
`originalCategory = existing?.originalCategory ?: event.category` (bzw. bei `hideSelectedEvent`,
wo kein `existing`-Lookup vorhanden ist, direkt `originalCategory = event.category`) ergänzen.
Alte, bereits gespeicherte Einträge ohne dieses Feld bleiben `null`, bis sie das nächste Mal
angefasst werden — kein Migrationsschritt nötig (siehe Spec-Randfälle, akzeptierter Zustand
für die Übergangszeit).

- [ ] **Step 3: Manuell/per Test verifizieren**

Falls ein `CalendarViewModelTest` mit Fake-`CalendarRepository` existiert oder in dieser
Session neu aufgesetzt wird (wie schon in der Countdown-Runde erwähnt): Testfälle für
`requestDeletionOfSelectedEvent` — (a) bei Erfolg des Repository-Aufrufs wird die
Anpassung mit `hidden = true` gespeichert, (b) bei Fehlschlag ebenfalls (Ausblenden ist
nicht vom Ergebnis der Anfrage abhängig), (c) `selectedEvent` wird in beiden Fällen sofort
auf `null` gesetzt.

- [ ] **Step 4: Vollen Testlauf gegenprüfen**

Run: `./gradlew testDebugUnitTest`
Expected: alle Tests grün.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/ui/calendar/CalendarViewModel.kt
git commit -m "Add ViewModel action for requesting deletion of a partner's Wir-Zeit event"
```

---

## Task 5: `EventActionsSheet` — dritter Button-Zustand "Löschen anfragen"

**Owner:** Codex

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/ui/event/EventActionsSheet.kt`
- Modify: `app/src/main/java/com/prehmus/selli/ui/calendar/CalendarScreen.kt` (Aufrufstelle,
  Zeilen ~288-298)

**Interfaces:**
- Consumes: `CalendarViewModel::requestDeletionOfSelectedEvent` (Task 4).
- Produces: `EventActionsSheet`s neuer Parameter `onRequestDeletion: (wholeSeries: Boolean) -> Unit`.

- [ ] **Step 1: Gate-Logik erweitern**

In `EventActionsSheet.kt`, nach `canDelete`:

```kotlin
private val canDelete = event.source == CalendarSource.GOOGLE_OWN
private val canRequestDeletion =
    event.source == CalendarSource.GOOGLE_PARTNER && event.category == EventCategory.TOGETHER
```

(Als `val` innerhalb der Composable-Funktion, wie `canDelete` es bereits ist — keine
eigene Top-Level-Deklaration.)

- [ ] **Step 2: Neuen Parameter + Button + Bestätigungsdialog ergänzen**

Signatur um `onRequestDeletion: (wholeSeries: Boolean) -> Unit` erweitern (nach `onDelete`).
Neuer State: `var showRequestDeleteConfirm by remember { mutableStateOf(false) }`.

Button-Block (ersetzt die bisherige `if (canDelete) { ... }`-Bedingung durch eine
`if/else if`, damit nie beide Buttons gleichzeitig erscheinen):

```kotlin
if (canDelete) {
    OutlinedButton(
        onClick = { showDeleteConfirm = true },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    ) {
        Icon(Icons.Default.Delete, contentDescription = null)
        Text("Endgültig löschen", modifier = Modifier.padding(start = 8.dp))
    }
} else if (canRequestDeletion) {
    OutlinedButton(
        onClick = { showRequestDeleteConfirm = true },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    ) {
        Icon(Icons.Default.Delete, contentDescription = null)
        Text("Löschen anfragen", modifier = Modifier.padding(start = 8.dp))
    }
}
```

Neuer Bestätigungsdialog (nach dem bestehenden `showDeleteConfirm`-`AlertDialog`-Block):

```kotlin
if (showRequestDeleteConfirm) {
    AlertDialog(
        onDismissRequest = { showRequestDeleteConfirm = false },
        title = { Text("Löschen anfragen?") },
        text = {
            Text(
                "„${event.title}“ gehört nicht dir — du kannst ihn nicht selbst endgültig " +
                    "löschen. Er verschwindet sofort aus deiner Ansicht; die Person, die ihn " +
                    "angelegt hat, bekommt eine Anfrage, ihn ebenfalls zu löschen.",
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        showRequestDeleteConfirm = false
                        onRequestDeletion(false)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Anfrage senden") }
                TextButton(
                    onClick = { showRequestDeleteConfirm = false },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Abbrechen") }
            }
        },
    )
}
```

(Bewusst ohne Vorkommen/Serie-Auswahl wie beim echten Löschen — Ausführungsdetail: falls
gewünscht, kann das analog zum `pendingScopeAction`-Muster ergänzt werden; für den ersten
Wurf reicht "immer nur dieses Vorkommen" (`onRequestDeletion(false)`), da Wir-Zeit-Serien
in der Praxis selten sind.)

- [ ] **Step 3: Aufrufstelle in `CalendarScreen.kt` anpassen**

```kotlin
uiState.selectedEvent?.let { event ->
    EventActionsSheet(
        event = event,
        onEdit = viewModel::beginEditingSelectedEvent,
        onHide = viewModel::hideSelectedEvent,
        onSetCategory = viewModel::setSelectedEventCategory,
        onResetCustomization = viewModel::resetSelectedEventCustomization,
        onDelete = viewModel::deleteSelectedEvent,
        onRequestDeletion = viewModel::requestDeletionOfSelectedEvent,
        onDismiss = viewModel::dismissEventActions,
    )
}
```

- [ ] **Step 4: Build gegenprüfen**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

Run: `./gradlew testDebugUnitTest`
Expected: alle Tests grün (reine UI-Verdrahtung, keine neue testbare Logik in dieser Datei).

- [ ] **Step 5: Manuelle Verifikation (kein automatisierter UI-Test in diesem Projekt üblich)**

- Eigener Termin: weiterhin nur "Endgültig löschen" sichtbar, Verhalten unverändert.
- Wir-Zeit der Partnerin: "Löschen anfragen" sichtbar statt "Endgültig löschen", Dialog-Text
  korrekt, nach Bestätigung verschwindet der Termin sofort aus der eigenen Ansicht.
- Alles andere: kein Löschen-Button, nur "In Selli ausblenden" wie bisher.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/ui/event/EventActionsSheet.kt \
        app/src/main/java/com/prehmus/selli/ui/calendar/CalendarScreen.kt
git commit -m "Add 'request deletion' action for partner-organized Wir-Zeit events"
```

---

## Task 6: "Ausgeblendet & angepasst" — Mülleimer-Icon pro Zeile

**Owner:** Codex

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/ui/event/CustomizationManagerSheet.kt`
- Modify: `app/src/main/java/com/prehmus/selli/ui/calendar/CalendarScreen.kt` (Aufrufstelle,
  Zeilen ~310-316)
- Modify: `app/src/main/java/com/prehmus/selli/ui/calendar/CalendarViewModel.kt` (neue
  ViewModel-Aktion, siehe unten)

**Interfaces:**
- Consumes: `EventCustomization.originalCategory` (Task 1), `CustomizationTarget` (liefert
  `source` bereits heute über beide Varianten), `CalendarViewModel::deleteSelectedEvent`/
  `requestDeletionOfSelectedEvent` (Task 4) — hier aber **nicht** direkt wiederverwendbar,
  da diese auf `selectedEvent` arbeiten, das im Verwaltungsscreen nicht gesetzt ist. Neue,
  eigenständige ViewModel-Aktion nötig, die direkt mit `CustomizationTarget` arbeitet.

- [ ] **Step 1: `source`/Kategorie-Herleitung als kleine Hilfsfunktion**

In `CustomizationManagerSheet.kt`, private Hilfsfunktion:

```kotlin
private fun CustomizationTarget.source(): CalendarSource = when (this) {
    is CustomizationTarget.Occurrence -> key.source
    is CustomizationTarget.SeriesFrom -> source
}
```

- [ ] **Step 2: `CustomizationRow` um Mülleimer-Icon erweitern**

```kotlin
@Composable
private fun CustomizationRow(
    customization: EventCustomization,
    onRemove: (CustomizationTarget) -> Unit,
    onDelete: (CustomizationTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = customization.target.source()
    val canDelete = source == CalendarSource.GOOGLE_OWN
    val canRequestDeletion =
        source == CalendarSource.GOOGLE_PARTNER && customization.originalCategory == EventCategory.TOGETHER

    Surface(/* … unverändert … */) {
        Row(/* … unverändert … */) {
            Column(modifier = Modifier.weight(1f)) {
                /* … bestehender Text-Block unverändert … */
            }
            if (canDelete || canRequestDeletion) {
                IconButton(onClick = { onDelete(customization.target) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = if (canDelete) "Endgültig löschen" else "Löschen anfragen",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            TextButton(onClick = { onRemove(customization.target) }) {
                Text(if (customization.hidden) "Einblenden" else "Zurücksetzen")
            }
        }
    }
}
```

Neue Imports: `androidx.compose.material3.IconButton`, `androidx.compose.material.icons.filled.Delete`,
`com.prehmus.selli.domain.model.CalendarSource`, `com.prehmus.selli.domain.model.EventCategory`.
`CustomizationManagerSheet`s Signatur bekommt den neuen Parameter `onDelete: (CustomizationTarget) -> Unit`
und reicht ihn an `CustomizationRow` durch.

Bewusst **kein** Bestätigungsdialog an dieser Stelle für die Kürze — falls das im Gerätetest
zu leichtfertig wirkt, ist ein einfacher `AlertDialog` (analog zu `EventActionsSheet`) eine
naheliegende Nachbesserung, aber kein Blocker für diesen Task.

- [ ] **Step 3: ViewModel-Aktion für die Verwaltungsliste**

In `CalendarViewModel.kt`, nach `removeCustomization`:

```kotlin
/**
 * Löst vom Verwaltungsscreen aus dieselbe Aktion aus wie im Termin-Sheet — echtes Löschen
 * für eigene Termine, Lösch-Anfrage für Wir-Zeit der Partnerin. Arbeitet direkt mit dem
 * `CustomizationTarget`, da hier kein `selectedEvent` gesetzt ist. Der zugrunde liegende
 * Google-Termin wird für die eigentliche Aktion frisch benötigt — bei Occurrence-Targets
 * über `EventKey.eventId`/`source` und die zuletzt bekannten Zeit-/Serieninformationen aus
 * der Anpassung selbst rekonstruiert, da ausgeblendete Termine nicht mehr im gefilterten
 * `eventsByDay` stehen.
 */
fun deleteFromCustomizationManager(target: CustomizationTarget) {
    viewModelScope.launch {
        val source = when (target) {
            is CustomizationTarget.Occurrence -> target.key.source
            is CustomizationTarget.SeriesFrom -> target.source
        }
        val minimalEvent = target.toMinimalCalendarEvent(
            label = _uiState.value.storedCustomizations.firstOrNull { it.target == target }?.label.orEmpty(),
            category = _uiState.value.storedCustomizations
                .firstOrNull { it.target == target }?.originalCategory
                ?: EventCategory.TOGETHER,
        )
        val result = if (source == CalendarSource.GOOGLE_OWN) {
            calendarRepository.deleteEvent(minimalEvent, DeletionScope.SINGLE_OCCURRENCE)
        } else {
            calendarRepository.requestPartnerDeletion(minimalEvent, wholeSeries = false)
        }
        result.onSuccess {
            customizationRepository.remove(target)
            _uiState.update {
                it.copy(
                    userMessage = "Erledigt.",
                    storedCustomizations = it.storedCustomizations.filterNot { c -> c.target == target },
                )
            }
            refresh(forceNetwork = true)
        }.onFailure { error ->
            _uiState.update { it.copy(userMessage = error.message ?: "Aktion fehlgeschlagen.") }
        }
    }
}
```

**Wichtiger technischer Haken, im Review zu klären:** `GoogleCalendarEventDeletion`/
`GoogleCalendarEventSharing` brauchen ein vollständiges `CalendarEvent` (`id`, `seriesId`,
`start` für die Serien-Logik). Aus einem `CustomizationTarget.Occurrence` ist nur `eventId`
und `source` bekannt (kein `start`), aus `SeriesFrom` nur `seriesId`/`fromStart`/`source`.
Für `DeletionScope.SINGLE_OCCURRENCE` (der einzige hier verwendete Scope) braucht
`GoogleCalendarEventDeletion.deleteExistingEvent` nur `event.id` — ein "minimales"
`CalendarEvent` mit sinnvollen Platzhaltern für die übrigen Pflichtfelder (z. B.
`start = end = LocalDateTime.now()`, `isAllDay = false`, `owner` aus dem Kontext) ist dafür
ausreichend, solange nie `THIS_AND_FOLLOWING` von hier aus ausgelöst wird. Eine kleine
private Hilfsfunktion `CustomizationTarget.toMinimalCalendarEvent(label, category)` dafür
ergänzen. **Diesen Ansatz beim Umsetzen noch einmal kritisch gegenlesen** — falls er zu
fragil wirkt (z. B. weil `GoogleCalendarEventSharing.requestDeletion` doch mehr Felder
braucht als angenommen), ist die robustere Alternative, den Mülleimer-Button nur für
Targets zu zeigen, deren Termin noch im aktuellen `eventsByDay` auffindbar ist (dort ist
sonst ohnehin nichts zu tun, weil nicht-ausgeblendete Angepasste dort stehen), und für
tatsächlich ausgeblendete Termine ohne Live-Daten ehrlich zu sagen "zum Löschen bitte im
Kalender einblenden und dort öffnen" statt eine brüchige Rekonstruktion zu bauen.

- [ ] **Step 4: Aufrufstelle in `CalendarScreen.kt` anpassen**

```kotlin
if (uiState.isCustomizationManagerOpen) {
    CustomizationManagerSheet(
        customizations = uiState.storedCustomizations,
        onRemove = viewModel::removeCustomization,
        onDelete = viewModel::deleteFromCustomizationManager,
        onDismiss = viewModel::dismissCustomizationManager,
    )
}
```

- [ ] **Step 5: Build gegenprüfen**

Run: `./gradlew clean testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, alle Tests grün.

- [ ] **Step 6: Manuelle Verifikation**

- Eigener ausgeblendeter/angepasster Termin: Mülleimer-Icon sichtbar, löst echtes Löschen aus.
- Ausgeblendete Wir-Zeit der Partnerin: Mülleimer-Icon sichtbar, löst Lösch-Anfrage aus.
- Alles andere (z. B. ausgeblendeter Arbeitskalender-Eintrag): kein Mülleimer-Icon, nur
  "Einblenden".

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/ui/event/CustomizationManagerSheet.kt \
        app/src/main/java/com/prehmus/selli/ui/calendar/CalendarScreen.kt \
        app/src/main/java/com/prehmus/selli/ui/calendar/CalendarViewModel.kt
git commit -m "Add delete/request-deletion shortcut to the customization manager list"
```

---

## Selbstprüfung (bereits durchgeführt)

- **Spec-Abdeckung:** Alle vier Fälle aus `docs/superpowers/specs/2026-08-07-termin-entfernen-design.md`
  abgedeckt — Fall 1/2 unverändert (kein Task nötig), Fall 3 (Task 2-5), Fall 4 unverändert.
  Mülleimer-Icon im Verwaltungsscreen (Task 6).
- **Platzhalter-Scan:** Keine TBD/TODO-Stellen. Zwei bewusst benannte, im jeweiligen Task
  klar markierte technische Unsicherheiten (Attendee-Schreibrecht in Task 2,
  "minimales `CalendarEvent`"-Rekonstruktion in Task 6) — beide mit explizit beschriebenem
  Fallback, kein vages "später klären".
- **Typkonsistenz:** `CalendarRepository.requestPartnerDeletion(event, wholeSeries)` wird in
  Task 2 definiert und in Task 4/6 identisch aufgerufen; `SharedEventChange.DeleteRequested`
  (Task 3) und `CalendarViewModel.requestDeletionOfSelectedEvent`/`deleteFromCustomizationManager`
  (Task 4/6) sind eigenständige, nicht verwechselbare Namen.
- **Reihenfolge/Abhängigkeiten:** Task 1 ist Voraussetzung für alle weiteren. Task 2 und
  Task 3 können parallel laufen (beide hängen nur von Task 1 ab, nicht voneinander). Task 4
  hängt von Task 2 ab. Task 5 hängt von Task 4 ab. Task 6 hängt von Task 1 (originalCategory)
  und Task 2 (requestPartnerDeletion) ab, aber nicht von Task 3, 4 oder 5.
