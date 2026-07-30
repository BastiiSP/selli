# Mehrtägige Ganztagstermine und Frei-Zeit-Festlegung Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mehrtägige Ganztagstermine anlegen und pro Ganztagstermin rein lokal festlegen, ob er Sellis gemeinsame Frei-Zeit-Berechnung blockiert.

**Architecture:** Das bestehende `EventCustomization`-System erhält eine nullable Frei-Zeit-Überschreibung und löst sie im Merge zu einem wirksamen `CalendarEvent`-Wert auf. Die Anlage übermittelt nur Start und exklusives Enddatum an Google und speichert die Selli-spezifische Entscheidung anschließend lokal; lokales Bearbeiten verwendet unverändert Occurrence-/SeriesFrom-Targets.

**Tech Stack:** Kotlin 2.1, Java 17, Jetpack Compose/Material 3, Coroutines/Flow, Gson, Google Calendar API, JUnit 4.

## Global Constraints

- Alle neuen Frei-Zeit-Entscheidungen bleiben lokal; niemals eine Google-Update-Operation dafür ausführen.
- Importierte Ganztagstermine ohne explizite Festlegung blockieren weiterhin nicht.
- Getimte Termine blockieren weiterhin automatisch.
- Ganztägige UI-Endtage sind inklusiv; Domain-/Google-Endzeitpunkte bleiben exklusiv.
- Bestehende JSON-Dateien im Format Version 1 müssen ohne Migration weiter lesbar sein.
- Keine Secrets oder lokalen Feed-URLs in Code, Tests, Dokumentation oder Commits aufnehmen.
- Nach grünen Unit-Tests und erfolgreichem Debug-Build `app/build/outputs/apk/debug/app-debug.apk` nach `selli.apk` kopieren.

## File Map

- `domain/model/Models.kt`: wirksamer Event-Wert und Auswahl im neuen Event.
- `domain/model/EventCustomization.kt`: nullable lokale Frei-Zeit-Überschreibung.
- `data/customization/EventCustomizationCodec.kt`: rückwärtskompatible JSON-Persistenz.
- `domain/merge/DefaultCalendarMergeService.kt`: Override-Auflösung und Frei-Zeit-Filter.
- `ui/event/NewCalendarEventDraftFactory.kt`: pure Umwandlung inklusiver Formulardaten in exklusive Event-Zeitpunkte.
- `ui/event/CreateEventSheet.kt`: Start-/Endtag und Standard-Schalter für Ganztagstermine.
- `ui/event/EditEventSheet.kt`: lokaler Schalter beim Bearbeiten.
- `ui/calendar/CalendarViewModel.kt`: kombinierte Customization nach Anlage und beim Bearbeiten.
- `ui/calendar/CalendarScreen.kt`: unveränderte Callback-Verdrahtung mit erweitertem Override-Modell.
- Tests unter den korrespondierenden `app/src/test/java/...`-Paketen.

---

### Task 1: Datenmodell und rückwärtskompatible Persistenz

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/domain/model/Models.kt`
- Modify: `app/src/main/java/com/prehmus/selli/domain/model/EventCustomization.kt`
- Modify: `app/src/main/java/com/prehmus/selli/data/customization/EventCustomizationCodec.kt`
- Modify: `app/src/test/java/com/prehmus/selli/domain/model/EventCustomizationTest.kt`
- Modify: `app/src/test/java/com/prehmus/selli/data/customization/EventCustomizationCodecTest.kt`

**Interfaces:**
- Produces: `CalendarEvent.blocksSharedFreeTime: Boolean`
- Produces: `NewCalendarEvent.blocksSharedFreeTime: Boolean`
- Produces: `EventFieldOverrides.blocksSharedFreeTime: Boolean?`

- [ ] **Step 1: Write failing model and codec tests**

Add assertions that `EventFieldOverrides(blocksSharedFreeTime = false)` is not empty, that both
boolean states survive a codec roundtrip, and that this old document decodes with `null`:

```kotlin
val legacy = """
    {"version":1,"customizations":[{
      "targetType":"occurrence","source":"GOOGLE_OWN","eventId":"legacy",
      "hidden":false,"label":"Alt"
    }]}
""".trimIndent()
assertNull(codec.decode(legacy).single().overrides.blocksSharedFreeTime)
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
./gradlew testDebugUnitTest --tests '*EventCustomizationTest' --tests '*EventCustomizationCodecTest'
```

Expected: compilation fails because `blocksSharedFreeTime` does not exist.

- [ ] **Step 3: Add the minimal model fields**

Use these signatures:

```kotlin
data class CalendarEvent(
    // existing fields
    val blocksSharedFreeTime: Boolean = !isAllDay,
)

data class NewCalendarEvent(
    // existing fields
    val blocksSharedFreeTime: Boolean = isAllDay,
)

data class EventFieldOverrides(
    // existing fields
    val blocksSharedFreeTime: Boolean? = null,
)
```

Include the nullable field in `isEmpty()`.

- [ ] **Step 4: Extend codec without changing format version**

Add `blocksSharedFreeTime: Boolean?` to `StoredCustomization`, encode from overrides and decode
back into `EventFieldOverrides`. Gson maps the absent legacy field to `null`.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run the command from Step 2. Expected: all selected tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/domain/model/Models.kt \
  app/src/main/java/com/prehmus/selli/domain/model/EventCustomization.kt \
  app/src/main/java/com/prehmus/selli/data/customization/EventCustomizationCodec.kt \
  app/src/test/java/com/prehmus/selli/domain/model/EventCustomizationTest.kt \
  app/src/test/java/com/prehmus/selli/data/customization/EventCustomizationCodecTest.kt
git commit -m "Add local all-day free-time preference"
```

### Task 2: Merge- und Frei-Zeit-Verhalten

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/domain/merge/DefaultCalendarMergeService.kt`
- Modify: `app/src/test/java/com/prehmus/selli/domain/merge/DefaultCalendarMergeServiceTest.kt`

**Interfaces:**
- Consumes: `EventFieldOverrides.blocksSharedFreeTime`
- Produces: merged `CalendarEvent.blocksSharedFreeTime`

- [ ] **Step 1: Write failing merge tests**

Cover these behaviors with real repository fakes already present in the test class:

```kotlin
@Test
fun freeBlocks_blockingMultiDayAllDayEventBlocksMiddleDay() = runTest {
    val event = event(
        id = "trip",
        start = testDay.minusDays(1).atStartOfDay(),
        end = testDay.plusDays(2).atStartOfDay(),
        isAllDay = true,
    )
    val service = service(
        googleEvents = listOf(event),
        customizations = listOf(
            customization(
                target = CustomizationTarget.Occurrence(EventKey(event.source, event.id)),
                overrides = EventFieldOverrides(blocksSharedFreeTime = true),
            ),
        ),
    )
    assertTrue(service.freeBlocks(testDay).isEmpty())
}
```

Also assert:

- no override leaves the full 9–22 free block;
- explicit `false` leaves the full block;
- a timed event still blocks even if a synthetic override contains `false`;
- a blocking-only override sets `hasAnyCustomization` but not `isCustomized`.

- [ ] **Step 2: Run focused test and verify RED**

```bash
./gradlew testDebugUnitTest --tests '*DefaultCalendarMergeServiceTest'
```

Expected: new blocking Ganztagstest returns a free block instead of an empty list.

- [ ] **Step 3: Resolve the override during merge**

In `withOverrides`, distinguish visible field changes from the new calculation-only override:

```kotlin
val hasFreeTimeOverride = overrides.blocksSharedFreeTime != null
val hasApplicableOverride = hasFieldOverride || hasCategoryOverride || hasFreeTimeOverride

return copy(
    // existing copies
    blocksSharedFreeTime = overrides.blocksSharedFreeTime ?: blocksSharedFreeTime,
    isCustomized = hasFieldOverride,
    hasAnyCustomization = hasApplicableOverride,
)
```

- [ ] **Step 4: Include only marked all-day events in blocked intervals**

Replace the blanket all-day exclusion with:

```kotlin
.filter { event -> !event.isAllDay || event.blocksSharedFreeTime }
```

The existing clipping turns an included all-day interval into 9–22 for the queried day.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run the command from Step 2. Expected: all merge tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/domain/merge/DefaultCalendarMergeService.kt \
  app/src/test/java/com/prehmus/selli/domain/merge/DefaultCalendarMergeServiceTest.kt
git commit -m "Honor all-day events in free-time calculation"
```

### Task 3: Mehrtägigen Anlege-Entwurf rein und testbar erzeugen

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/ui/event/NewCalendarEventDraftFactory.kt`
- Create: `app/src/test/java/com/prehmus/selli/ui/event/NewCalendarEventDraftFactoryTest.kt`
- Modify: `app/src/main/java/com/prehmus/selli/ui/event/CreateEventSheet.kt`

**Interfaces:**
- Produces:

```kotlin
internal fun buildNewCalendarEvent(
    title: String,
    startDay: LocalDate,
    endDayInclusive: LocalDate,
    isAllDay: Boolean,
    startTime: LocalTime,
    endTime: LocalTime,
    location: String?,
    invitePartner: Boolean,
    recurrence: EventRecurrence?,
    blocksSharedFreeTime: Boolean,
): NewCalendarEvent
```

- [ ] **Step 1: Write failing factory tests**

Assert a five-day selection from 2026-08-03 through 2026-08-07 produces:

```kotlin
assertEquals(LocalDateTime.of(2026, 8, 3, 0, 0), event.start)
assertEquals(LocalDateTime.of(2026, 8, 8, 0, 0), event.end)
assertTrue(event.isAllDay)
assertTrue(event.blocksSharedFreeTime)
```

Add tests rejecting an all-day end before start and preserving same-day timed behavior.

- [ ] **Step 2: Run factory tests and verify RED**

```bash
./gradlew testDebugUnitTest --tests '*NewCalendarEventDraftFactoryTest'
```

Expected: test compilation fails because the factory is missing.

- [ ] **Step 3: Implement the pure factory**

For all-day input, require `endDayInclusive >= startDay`, use start midnight and
`endDayInclusive.plusDays(1).atStartOfDay()`. For timed input, require `endTime > startTime` and use
the start day for both timestamps. Trim title/location and pass recurrence, invite and local flag.

- [ ] **Step 4: Run factory tests and verify GREEN**

Run the command from Step 2. Expected: all factory tests pass.

- [ ] **Step 5: Wire inclusive Start-/Endtag into the Compose sheet**

Replace `day` with `startDay`, add `endDayInclusive`, a date-picker target enum and the local
boolean state:

```kotlin
var endDayInclusive by remember { mutableStateOf(initialDay) }
var blocksSharedFreeTime by rememberSaveable { mutableStateOf(true) }
var datePickerTarget by remember { mutableStateOf<DateTarget?>(null) }
```

For all-day mode show two buttons labelled `Von …` and `Bis einschließlich …`, plus:

```kotlin
LabeledSwitch(
    label = "Als gemeinsam verplante Zeit werten",
    checked = blocksSharedFreeTime,
    onCheckedChange = { blocksSharedFreeTime = it },
)
```

When the selected start moves after the current end, set the end to the new start. Call
`buildNewCalendarEvent` from the save button. Timed mode keeps one date and its two time pickers.

- [ ] **Step 6: Compile and run focused tests**

```bash
./gradlew testDebugUnitTest --tests '*NewCalendarEventDraftFactoryTest'
```

Expected: Kotlin/Compose compilation succeeds and tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/ui/event/NewCalendarEventDraftFactory.kt \
  app/src/main/java/com/prehmus/selli/ui/event/CreateEventSheet.kt \
  app/src/test/java/com/prehmus/selli/ui/event/NewCalendarEventDraftFactoryTest.kt
git commit -m "Create multi-day all-day events"
```

### Task 4: Festlegung nach der Anlage lokal speichern

**Files:**
- Create: `app/src/test/java/com/prehmus/selli/ui/calendar/MainDispatcherRule.kt`
- Create: `app/src/test/java/com/prehmus/selli/ui/calendar/CalendarViewModelTest.kt`
- Modify: `app/src/main/java/com/prehmus/selli/ui/calendar/CalendarViewModel.kt`

**Interfaces:**
- Consumes: `NewCalendarEvent.blocksSharedFreeTime`
- Produces: one combined `EventCustomization` after successful creation

- [ ] **Step 1: Add coroutine test rule and failing creation tests**

`MainDispatcherRule` sets and resets `Dispatchers.Main` around each test. Use a recording
`CalendarRepository`, empty `CalendarMergeService`, and in-memory `EventCustomizationRepository`.

For both `true` and `false`, call `viewModel.createEvent(allDayDraft, category)`,
`advanceUntilIdle()`, then assert:

```kotlin
assertEquals(expected, customizations.single().overrides.blocksSharedFreeTime)
assertEquals(1, calendarRepository.createCalls.size)
```

Add a recurring draft and assert the target is:

```kotlin
CustomizationTarget.SeriesFrom(
    source = CalendarSource.GOOGLE_OWN,
    seriesId = created.id,
    fromStart = created.start,
)
```

- [ ] **Step 2: Run ViewModel tests and verify RED**

```bash
./gradlew testDebugUnitTest --tests '*CalendarViewModelTest'
```

Expected: no customization is stored when the chosen category equals the derived category.

- [ ] **Step 3: Replace category-only persistence with combined persistence**

Rename `applyCategoryToCreatedEvent` to `applyLocalSettingsToCreatedEvent`. Build one
`EventFieldOverrides` containing:

```kotlin
category = category.takeIf { it != derivedCategory }
blocksSharedFreeTime = draft.blocksSharedFreeTime.takeIf { draft.isAllDay }
```

Save whenever the overrides are not empty. Use `SeriesFrom(created.source, created.id,
created.start)` when `draft.recurrence != null`, otherwise `Occurrence(created.key())`.

Return success/failure to `createEvent`. On local failure, close the sheet, refresh, and show
`"Termin angelegt, lokale Einstellungen konnten nicht gespeichert werden."`; never delete the
already created Google event.

- [ ] **Step 4: Run ViewModel tests and verify GREEN**

Run the command from Step 2. Expected: all ViewModel creation tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/ui/calendar/CalendarViewModel.kt \
  app/src/test/java/com/prehmus/selli/ui/calendar/MainDispatcherRule.kt \
  app/src/test/java/com/prehmus/selli/ui/calendar/CalendarViewModelTest.kt
git commit -m "Persist created all-day free-time choice"
```

### Task 5: Ganztagstermine rein lokal bearbeiten

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/ui/event/EditEventSheet.kt`
- Modify: `app/src/main/java/com/prehmus/selli/ui/calendar/CalendarViewModel.kt`
- Modify: `app/src/test/java/com/prehmus/selli/ui/calendar/CalendarViewModelTest.kt`

**Interfaces:**
- Consumes and produces: `EventFieldOverrides.blocksSharedFreeTime`

- [ ] **Step 1: Write failing edit tests**

Select an imported all-day event, begin editing, then save:

```kotlin
viewModel.selectEvent(importedAllDay)
viewModel.beginEditingSelectedEvent(wholeSeries = false)
viewModel.saveEventOverrides(EventFieldOverrides(blocksSharedFreeTime = true))
advanceUntilIdle()
```

Assert the saved occurrence customization contains `true`, a pre-existing category remains
present when a later edit omits category, and `RecordingCalendarRepository.createCalls` plus
`deleteCalls` remain empty.

Add the inverse test for an existing `true` customization changed to explicit `false`.

- [ ] **Step 2: Run ViewModel tests and verify RED**

```bash
./gradlew testDebugUnitTest --tests '*CalendarViewModelTest'
```

Expected: at least the preservation assertion fails because save currently preserves category
only.

- [ ] **Step 3: Merge existing local-only overrides**

In `saveEventOverrides`, read the existing target customization once. Preserve both category and
`blocksSharedFreeTime` when the incoming field is `null`:

```kotlin
val merged = overrides.copy(
    category = overrides.category ?: existing?.overrides?.category,
    blocksSharedFreeTime = overrides.blocksSharedFreeTime
        ?: existing?.overrides?.blocksSharedFreeTime,
)
```

Keep `hidden = existing?.hidden ?: false` and the nonblank existing label.

- [ ] **Step 4: Add the switch to `EditEventSheet`**

Initialize:

```kotlin
var blocksSharedFreeTime by remember(event) {
    mutableStateOf(event.blocksSharedFreeTime)
}
```

Only for `event.isAllDay`, render the same labelled switch used during creation. Pass:

```kotlin
blocksSharedFreeTime = blocksSharedFreeTime.takeIf {
    event.isAllDay && it != event.blocksSharedFreeTime
}
```

inside `EventFieldOverrides`.

- [ ] **Step 5: Run ViewModel tests and verify GREEN**

Run the command from Step 2. Expected: all edit and creation tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/ui/event/EditEventSheet.kt \
  app/src/main/java/com/prehmus/selli/ui/calendar/CalendarViewModel.kt \
  app/src/test/java/com/prehmus/selli/ui/calendar/CalendarViewModelTest.kt
git commit -m "Edit all-day free-time choice locally"
```

### Task 6: Gesamtsystem verifizieren und APK bereitstellen

**Files:**
- Modify only if required by compile/test findings.
- Generate ignored artifact: `selli.apk`

**Interfaces:**
- Verifies all earlier tasks together.

- [ ] **Step 1: Run full unit suite**

```bash
./gradlew testDebugUnitTest
```

Expected: exit 0, zero failed tests.

- [ ] **Step 2: Run debug build**

```bash
./gradlew assembleDebug
```

Expected: exit 0 and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 3: Copy the required handoff APK**

```bash
cp app/build/outputs/apk/debug/app-debug.apk selli.apk
```

Verify both files have identical SHA-256 hashes.

- [ ] **Step 4: Audit scope and secrets**

```bash
git diff --check
git status --short
git diff main...HEAD --stat
```

Inspect every changed tracked file and confirm no token, client ID, ICS URL, unrelated design
change, or Google event-update call was added.

- [ ] **Step 5: Commit any final tracked correction**

If verification required a tracked fix, repeat Steps 1–4 and commit only that correction. The
ignored `selli.apk` is never staged.
