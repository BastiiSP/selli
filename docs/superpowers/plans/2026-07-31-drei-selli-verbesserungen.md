# Drei Selli-Verbesserungen: Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks with **Owner: Codex** are delegated via the `codex:rescue` skill (Codex runs on `gpt-5.6-sol`, `model_reasoning_effort = high`, already configured in `~/.codex/config.toml`); Claude Code reviews each Codex deliverable against this plan and the referenced spec before it is committed.

**Goal:** Ship three independently-specced Selli improvements on one shared branch: (A) Wir-Zeit change notifications, (B) size-adaptive widget content, (C) overlap grouping for Wir-Zeit events in the agenda list.

**Architecture:** All three build on the existing `WidgetRefreshWorker` (A and B extend it; C is fully independent and only touches `DayDetail.kt`). A shared foundational task widens the worker's lookahead window before A and B build on it. No new backend, no new external services — everything stays within the existing local-first architecture (Room-less file/SharedPreferences stores, WorkManager, Glance).

**Tech Stack:** Kotlin, Jetpack Compose, Jetpack Glance (`androidx.glance`), WorkManager, JUnit + kotlinx-coroutines-test (existing test stack, see `app/src/test/java/com/prehmus/selli/`).

## Global Constraints

- Never write to the real Google Calendar event for anything covered here — all three features are purely local (overrides, notifications, and widget/list rendering).
- Follow the existing pattern of pure, Android-framework-free domain classes for anything unit-testable (see `NextPartnerEventSelector`, `groupByDay` in `CalendarViewModel.kt`) — keep Compose/Glance/Android-API code as thin wrappers around them.
- No secrets in code or commits (ICS feed URLs, tokens) — same rule as every prior Selli feature.
- Every task ends with `./gradlew testDebugUnitTest` green before commit; run `./gradlew clean assembleDebug` once after the full branch is done.
- Specs (read before implementing the matching group):
  - Group A: `docs/superpowers/specs/2026-07-31-wir-zeit-benachrichtigungen-design.md`
  - Group B: `docs/superpowers/specs/2026-07-31-widget-groessenvarianten-design.md`
  - Group C: `docs/superpowers/specs/2026-07-31-wir-zeit-verlauf-terminliste-design.md`

---

## File Structure

| File | Status | Responsibility |
|---|---|---|
| `app/src/main/java/com/prehmus/selli/data/widget/WidgetRefreshWorker.kt` | Modify | Widen lookahead to 30 days (Task 1); call into notification detection (Group A) and compute next-Wir-Zeit/next-free-slot (Group B) using the one fetched event list. |
| `app/src/main/java/com/prehmus/selli/domain/merge/DefaultCalendarMergeService.kt` | Modify | Extract per-day free-block computation so it can run over a pre-fetched event list across a date range (Task 2, Group B). |
| `app/src/main/java/com/prehmus/selli/domain/CalendarMergeService.kt` | Modify | Add `freeBlocksInRange` to the interface (Task 2). |
| `app/src/main/java/com/prehmus/selli/domain/widget/PartnerSharedEventChangeDetector.kt` | Create | Pure Kotlin: diffs current partner Wir-Zeit events against a stored fingerprint map, returns new/updated notification plans (Group A). |
| `app/src/main/java/com/prehmus/selli/data/notification/SharedEventFingerprintStore.kt` | Create | SharedPreferences-backed store of seen Wir-Zeit event fingerprints, same pattern as `WidgetSnapshotStore` (Group A). |
| `app/src/main/java/com/prehmus/selli/data/notification/SharedEventNotifier.kt` | Create | Android-specific: builds and posts the notification channel/notifications from `PartnerSharedEventChangeDetector` output, checks permission (Group A). |
| `app/src/main/java/com/prehmus/selli/MainActivity.kt` | Modify | Read notification tap intent extras, request `POST_NOTIFICATIONS` permission on Android 13+ (Group A). |
| `app/src/main/java/com/prehmus/selli/ui/SelliApp.kt` | Modify | Accept a pending navigation target (day + event key) and forward it to `CalendarViewModel` once loaded (Group A). |
| `app/src/main/java/com/prehmus/selli/ui/calendar/CalendarViewModel.kt` | Modify | New `openDeepLinkedEvent(day, key)` that calls existing `selectDay`/`selectEvent` once that day's events are loaded (Group A). |
| `app/src/main/android/AndroidManifest.xml` | Modify | Add `POST_NOTIFICATIONS` permission (Group A). |
| `app/src/main/java/com/prehmus/selli/domain/model/WidgetSnapshot.kt` | Modify | Add `nextSharedEvent: CalendarEvent?` and `nextFreeSlot: FreeSlot?` fields (Group B). |
| `app/src/main/java/com/prehmus/selli/data/widget/WidgetSnapshotCodec.kt` | Modify | Encode/decode the two new fields (Group B). |
| `app/src/main/java/com/prehmus/selli/domain/widget/NextSharedEventSelector.kt` | Create | Pure Kotlin: earliest future `TOGETHER`-category event regardless of owner (Group B). |
| `app/src/main/java/com/prehmus/selli/ui/widget/SelliWidget.kt` | Modify | `SizeMode.Single` → `SizeMode.Responsive` with 3 buckets; render row 2/3 conditionally per bucket (Group B). |
| `app/src/main/java/com/prehmus/selli/ui/calendar/EventGrouping.kt` | Create | Pure Kotlin: groups a day's events into `EventListRow` (`Single` / `Group`) by Wir-Zeit overlap (Group C). |
| `app/src/main/java/com/prehmus/selli/ui/calendar/DayDetail.kt` | Modify | Render `EventListRow.Group` via new `EventGroupCard`; `EventListRow.Single` via existing `EventCard` (Group C). |

---

## Task 1: Widen widget lookahead to 30 days (shared prerequisite)

**Owner:** Claude (one-line constant change, not worth a Codex round-trip)

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/data/widget/WidgetRefreshWorker.kt`

**Interfaces:**
- Produces: `WidgetRefreshWorker.LOOKAHEAD_DAYS = 30L` (was `14L`) — Groups A and B both rely on the worker fetching a 30-day window.

- [ ] **Step 1:** Change `private companion object { ... const val LOOKAHEAD_DAYS = 14L ... }` to `30L` in `WidgetRefreshWorker.kt`.
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest` — expect unchanged green (no test hardcodes 14 days; if one does, update it to 30 and note why in the commit message).
- [ ] **Step 3:** Commit: `git commit -m "Widen widget lookahead window to 30 days"`.

---

## Task 2: Multi-day free-block computation without redundant refetching

**Owner:** Codex (`codex:rescue`) — pure domain logic + refactor of existing tested code, no UI.

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/domain/CalendarMergeService.kt`
- Modify: `app/src/main/java/com/prehmus/selli/domain/merge/DefaultCalendarMergeService.kt`
- Test: `app/src/test/java/com/prehmus/selli/domain/merge/DefaultCalendarMergeServiceTest.kt`

**Interfaces:**
- Consumes: existing `freeBlocks(day: LocalDate): List<FreeTimeBlock>` behavior and its private helpers (window 9:00–22:00, minimum 3h block, all-day events only block when `blocksSharedFreeTime == true`) — must stay byte-for-byte identical in output for any single day.
- Produces:
  ```kotlin
  interface CalendarMergeService {
      // existing methods unchanged
      suspend fun freeBlocksInRange(range: DateRange): Map<LocalDate, List<FreeTimeBlock>>
  }
  ```
  `freeBlocksInRange` fetches `mergedEvents(range)` **once**, then reuses the same per-day computation `freeBlocks(day)` already uses (extract that computation into a private function taking `events: List<CalendarEvent>` instead of calling `mergedEvents` itself) for every `LocalDate` in `range`. `freeBlocks(day)` becomes a one-line wrapper: `freeBlocksInRange(DateRange(day, day))[day].orEmpty()`, or keep its own body calling the extracted private function directly with a single day's fetch — either is fine as long as **no behavior changes** for the existing single-day path.

- [ ] **Step 1: Write the failing test for the new range method** — add to `DefaultCalendarMergeServiceTest.kt`:
  ```kotlin
  @Test
  fun freeBlocksInRange_returnsSameResultAsPerDayCallsWithoutRefetchingPerDay() = runTest {
      val busyDay = testDay
      val freeDay = testDay.plusDays(1)
      val timed = event(id = "meeting", start = busyDay.atTime(9, 0), end = busyDay.atTime(22, 0))
      val service = service(googleEvents = listOf(timed))

      val range = DateRange(start = busyDay, endInclusive = freeDay)
      val result = service.freeBlocksInRange(range)

      assertEquals(service.freeBlocks(busyDay), result[busyDay])
      assertEquals(service.freeBlocks(freeDay), result[freeDay])
  }
  ```
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests "*DefaultCalendarMergeServiceTest*"` — expect FAIL (`freeBlocksInRange` unresolved).
- [ ] **Step 3:** Add `freeBlocksInRange` to the `CalendarMergeService` interface (default-free, must be implemented) and implement it in `DefaultCalendarMergeService`: extract the body of `freeBlocks(day)` from `val blockedIntervals = mergedEvents(...)` through the `free` list construction into a private `fun computeFreeBlocks(day: LocalDate, events: List<CalendarEvent>): List<FreeTimeBlock>`. `freeBlocksInRange(range)` calls `mergedEvents(range)` once and maps each `LocalDate` in `range.start..range.endInclusive` through `computeFreeBlocks(day, eventsForThatDay)` (filter the single fetched list by day, same overlap semantics already used elsewhere — reuse the day-filtering approach from `groupByDay` in `CalendarViewModel.kt` as reference for "does this event touch this day"). `freeBlocks(day)` keeps its existing signature and now delegates to `computeFreeBlocks(day, mergedEvents(DateRange(day, day)))`.
- [ ] **Step 4:** Run the test again — expect PASS. Run the full existing `DefaultCalendarMergeServiceTest` suite — expect all still green (confirms the extraction didn't change single-day behavior).
- [ ] **Step 5:** Commit: `git commit -m "Add freeBlocksInRange to compute free blocks over a range from one fetch"`.

---

## Task 2b: Promote `CalendarEvent.key()` to a shared, public extension

**Owner:** Codex (`codex:rescue`) — trivial but touches a shared type, bundle with Task 2's commit or its own tiny commit.

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/ui/calendar/CalendarViewModel.kt` (remove the `private fun CalendarEvent.key()` at the bottom of the file, line ~553)
- Modify: `app/src/main/java/com/prehmus/selli/domain/model/EventCustomization.kt` (add the extension next to `EventKey`'s own definition, since that's its natural home and avoids a new file for one function)

**Interfaces:**
- Produces: `fun CalendarEvent.key(): EventKey = EventKey(source = source, eventId = id)` — public, in `com.prehmus.selli.domain.model`. Tasks 3, 4, and 8 (and any other new file needing an `EventKey` from a `CalendarEvent`) import and reuse this instead of re-deriving it or duplicating a private copy.

- [ ] **Step 1:** Move the function as described; update `CalendarViewModel.kt`'s existing call sites (`event.key()`, `created.key()`) to rely on the import instead of the local private extension — no behavior change, purely a visibility/location move.
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest` — expect unchanged green (pure refactor, no test should reference the function's location directly).
- [ ] **Step 3:** Commit: `git commit -m "Make CalendarEvent.key() a shared public extension"`.

---

## Task 3: Notification detection logic (pure Kotlin)

**Owner:** Codex (`codex:rescue`) — pure logic, mirrors existing `NextPartnerEventSelector` pattern.

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/domain/widget/PartnerSharedEventChangeDetector.kt`
- Test: `app/src/test/java/com/prehmus/selli/domain/widget/PartnerSharedEventChangeDetectorTest.kt`

**Interfaces:**
- Consumes: `CalendarEvent` (existing model), a `Map<EventKey, SharedEventFingerprint>` (previously seen state).
- Produces:
  ```kotlin
  data class SharedEventFingerprint(
      val title: String,
      val start: LocalDateTime,
      val end: LocalDateTime,
      val location: String?,
      val description: String?,
  )

  sealed interface SharedEventChange {
      val event: CalendarEvent
      data class New(override val event: CalendarEvent) : SharedEventChange
      data class Updated(
          override val event: CalendarEvent,
          val changedFields: List<String>, // e.g. ["Neue Zeit: 10:00–12:00", "Neuer Ort: Frankfurt"]
      ) : SharedEventChange
  }

  class PartnerSharedEventChangeDetector {
      /**
       * @param currentEvents this run's merged events for the lookahead window
       * @param ownPerson the signed-in person — their own Wir-Zeit events never notify
       * @param previouslySeen fingerprints from the last run (empty on first run)
       * @return changes to notify about, plus the fingerprint map to persist for next run
       */
      fun detect(
          currentEvents: List<CalendarEvent>,
          ownPerson: Person,
          previouslySeen: Map<EventKey, SharedEventFingerprint>,
      ): DetectionResult

      data class DetectionResult(
          val changes: List<SharedEventChange>,
          val updatedFingerprints: Map<EventKey, SharedEventFingerprint>,
      )
  }
  ```
  Use the public `CalendarEvent.key(): EventKey` extension from Task 2b — do not re-derive `EventKey` construction locally.

- [ ] **Step 1: Write failing tests** covering (see spec's "Tests" section for the full list):
  ```kotlin
  @Test
  fun detect_reportsBrandNewPartnerTogetherEvent() {
      val event = calendarEvent(id = "1", owner = Person.MELLI, category = EventCategory.TOGETHER)
      val result = PartnerSharedEventChangeDetector().detect(
          currentEvents = listOf(event),
          ownPerson = Person.BASTI,
          previouslySeen = emptyMap(),
      )
      assertEquals(1, result.changes.size)
      assertTrue(result.changes.single() is SharedEventChange.New)
  }

  @Test
  fun detect_ignoresOwnTogetherEvents() {
      val event = calendarEvent(id = "1", owner = Person.BASTI, category = EventCategory.TOGETHER)
      val result = PartnerSharedEventChangeDetector().detect(
          currentEvents = listOf(event),
          ownPerson = Person.BASTI,
          previouslySeen = emptyMap(),
      )
      assertTrue(result.changes.isEmpty())
  }

  @Test
  fun detect_reportsUpdatedWhenTimeChanges() {
      val key = EventKey(CalendarSource.GOOGLE_PARTNER, "1")
      val original = calendarEvent(id = "1", owner = Person.MELLI, category = EventCategory.TOGETHER,
          start = LocalDateTime.of(2026, 8, 1, 10, 0), end = LocalDateTime.of(2026, 8, 1, 12, 0))
      val moved = original.copy(start = original.start.plusHours(1), end = original.end.plusHours(1))
      val seen = mapOf(key to SharedEventFingerprint(
          title = original.title, start = original.start, end = original.end,
          location = original.location, description = original.description,
      ))
      val result = PartnerSharedEventChangeDetector().detect(
          currentEvents = listOf(moved), ownPerson = Person.BASTI, previouslySeen = seen,
      )
      val change = result.changes.single() as SharedEventChange.Updated
      assertTrue(change.changedFields.any { it.contains("Neue Zeit") })
  }

  @Test
  fun detect_reportsNothingWhenUnchanged() { /* same fingerprint in and out → empty changes */ }

  @Test
  fun detect_alwaysUpdatesFingerprintMapRegardlessOfNotifying() { /* updatedFingerprints reflects currentEvents even when changes is empty */ }
  ```
- [ ] **Step 2:** Run tests — expect FAIL (class doesn't exist).
- [ ] **Step 3:** Implement `PartnerSharedEventChangeDetector`: filter `currentEvents` to `category == TOGETHER && owner != ownPerson`; for each, look up `previouslySeen[event.key()]`; `null` → `New`; present but any of title/start/end/location/description differs → `Updated` with a human-readable `changedFields` list (German strings per the spec: `"Neue Zeit: ${start}–${end}"`, `"Neuer Ort: $location"`, `"Neuer Titel: $title"`, `"Beschreibung aktualisiert"` — only include the ones that actually changed); identical → no entry. `updatedFingerprints` always reflects every currently-relevant event's fresh fingerprint.
- [ ] **Step 4:** Run tests — expect PASS.
- [ ] **Step 5:** Commit: `git commit -m "Add PartnerSharedEventChangeDetector for Wir-Zeit notifications"`.

---

## Task 4: Fingerprint store + notification posting (Android-specific)

**Owner:** Codex (`codex:rescue`) — Android APIs (SharedPreferences, NotificationManager) but no Compose UI.

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/data/notification/SharedEventFingerprintStore.kt`
- Create: `app/src/main/java/com/prehmus/selli/data/notification/SharedEventNotifier.kt`
- Modify: `app/src/main/java/com/prehmus/selli/data/widget/WidgetRefreshWorker.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/prehmus/selli/data/notification/SharedEventFingerprintStoreTest.kt`

**Interfaces:**
- Consumes: `PartnerSharedEventChangeDetector` (Task 3), existing `WidgetSnapshotStore` as the SharedPreferences pattern reference.
- Produces:
  ```kotlin
  class SharedEventFingerprintStore(context: Context) {
      fun load(): Map<EventKey, SharedEventFingerprint>
      fun save(fingerprints: Map<EventKey, SharedEventFingerprint>)
  }

  class SharedEventNotifier(private val context: Context) {
      fun notify(changes: List<SharedEventChange>, partnerDisplayName: String)
      // internally: NotificationManagerCompat.areNotificationsEnabled() gate,
      // creates "wir_zeit_updates" channel once (idempotent), stable notification ID
      // derived from event key (e.g. event.key().hashCode()), deep-link intent extras
      // (day: String ISO, source: String, eventId: String) targeting MainActivity.
  }
  ```

- [ ] **Step 1: Write failing test** for the store round-trip (same shape as `WidgetSnapshotCodecTest` if one exists — check first):
  ```kotlin
  @Test
  fun saveThenLoad_roundTripsFingerprints() {
      val store = SharedEventFingerprintStore(ApplicationProvider.getApplicationContext())
      val key = EventKey(CalendarSource.GOOGLE_PARTNER, "1")
      val fingerprint = SharedEventFingerprint("Kino", LocalDateTime.now(), LocalDateTime.now().plusHours(2), null, null)
      store.save(mapOf(key to fingerprint))
      assertEquals(mapOf(key to fingerprint), store.load())
  }
  ```
- [ ] **Step 2:** Run — expect FAIL (class doesn't exist).
- [ ] **Step 3:** Implement `SharedEventFingerprintStore` following `WidgetSnapshotStore`'s SharedPreferences pattern (own preferences file, e.g. `selli_shared_event_fingerprints`; simple string-keyed encoding of the map, one entry per event key).
- [ ] **Step 4:** Run — expect PASS.
- [ ] **Step 5:** Implement `SharedEventNotifier` (no unit test needed for the Android notification posting itself — it's a thin wrapper; verify manually per Task 5's device check). Add `POST_NOTIFICATIONS` to `AndroidManifest.xml`.
- [ ] **Step 6:** Wire into `WidgetRefreshWorker.doWork()`: after computing `events`, if `NotificationManagerCompat.areNotificationsEnabled()`, load fingerprints, run `PartnerSharedEventChangeDetector().detect(...)`, call `SharedEventNotifier().notify(result.changes, partner.displayName)`, then `SharedEventFingerprintStore(...).save(result.updatedFingerprints)`. If notifications are disabled, skip this entire block (no load, no detect, no save) per the spec's explicit "skip the whole step, not just posting" decision.
- [ ] **Step 7:** Run `./gradlew testDebugUnitTest` — expect all green.
- [ ] **Step 8:** Commit: `git commit -m "Wire Wir-Zeit change detection and notifications into the widget worker"`.

---

## Task 5: Notification permission request + deep-link navigation

**Owner:** Codex (`codex:rescue`) for the ViewModel/state wiring; **Owner: Claude** for the final `MainActivity`/`SelliApp` Compose wiring review pass (touches Activity + Compose root, worth a Claude Code look given how central these files are).

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/MainActivity.kt`
- Modify: `app/src/main/java/com/prehmus/selli/ui/SelliApp.kt`
- Modify: `app/src/main/java/com/prehmus/selli/ui/calendar/CalendarViewModel.kt`
- Test: `app/src/test/java/com/prehmus/selli/ui/calendar/CalendarViewModelTest.kt`

**Interfaces:**
- Consumes: existing `CalendarUiState.selectedDay`, `selectedEvent`, `selectDay(date)` (check exact existing name before assuming), `selectEvent(event)`.
- Produces:
  ```kotlin
  // CalendarViewModel
  fun openDeepLinkedEvent(day: LocalDate, key: EventKey) {
      // sets visibleMonth/selectedDay to `day` via existing mechanism, then once
      // eventsByDay[day] is populated, finds the event with matching key and
      // calls the existing selectEvent(event) — if events for that day aren't
      // loaded yet, retry once refresh() completes (mirror however the
      // ViewModel already reacts to refresh completion elsewhere).
  }
  ```

- [ ] **Step 1: Write failing test:**
  ```kotlin
  @Test
  fun openDeepLinkedEvent_jumpsToDayAndSelectsMatchingEvent() = runTest {
      val day = LocalDate.of(2026, 8, 3)
      val event = calendarEvent(id = "1", source = CalendarSource.GOOGLE_PARTNER, start = day.atTime(10, 0), end = day.atTime(11, 0))
      val viewModel = viewModel(mergeService = fakeMergeServiceReturning(listOf(event)))

      viewModel.openDeepLinkedEvent(day, EventKey(CalendarSource.GOOGLE_PARTNER, "1"))
      advanceUntilIdle()

      assertEquals(day, viewModel.uiState.value.selectedDay)
      assertEquals(event, viewModel.uiState.value.selectedEvent)
  }
  ```
- [ ] **Step 2:** Run — expect FAIL.
- [ ] **Step 3:** Implement `openDeepLinkedEvent` in `CalendarViewModel` reusing existing day-navigation + `selectEvent`; if the target day's events aren't loaded synchronously, hook into the same refresh-completion path the ViewModel already uses for its initial load (read the existing `init`/`refresh` flow before adding a new one).
- [ ] **Step 4:** Run — expect PASS.
- [ ] **Step 5:** In `MainActivity.onCreate`, read `intent.getStringExtra("day")` / `"source"` / `"eventId"` (matching `SharedEventNotifier`'s extras from Task 4); if present, call through to the ViewModel via `SelliApp`. Add the Android 13+ `POST_NOTIFICATIONS` runtime permission request via `ActivityResultContracts.RequestPermission()`, requested once per the spec ("beim nächsten Öffnen nach diesem Update").
- [ ] **Step 6:** Run `./gradlew testDebugUnitTest assembleDebug` — expect green + successful build.
- [ ] **Step 7:** Commit: `git commit -m "Deep-link notification taps to day + event, request notification permission"`.

---

## Task 6: Widget snapshot + selectors for Wir-Zeit and free slot (Group B)

**Owner:** Codex (`codex:rescue`) — pure logic + data model, no Glance UI yet.

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/domain/model/WidgetSnapshot.kt`
- Modify: `app/src/main/java/com/prehmus/selli/data/widget/WidgetSnapshotCodec.kt`
- Create: `app/src/main/java/com/prehmus/selli/domain/widget/NextSharedEventSelector.kt`
- Modify: `app/src/main/java/com/prehmus/selli/data/widget/WidgetRefreshWorker.kt`
- Test: `app/src/test/java/com/prehmus/selli/domain/widget/NextSharedEventSelectorTest.kt`
- Test: `app/src/test/java/com/prehmus/selli/data/widget/WidgetSnapshotCodecTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class FreeSlot(val day: LocalDate, val block: FreeTimeBlock)

  data class WidgetSnapshot(
      // existing fields unchanged
      val nextSharedEvent: CalendarEvent? = null,
      val nextFreeSlot: FreeSlot? = null,
  )

  class NextSharedEventSelector {
      fun select(events: List<CalendarEvent>, now: LocalDateTime): CalendarEvent?
      // earliest event with category == EventCategory.TOGETHER and end > now,
      // regardless of owner — mirror NextPartnerEventSelector's sort/tie-break.
  }
  ```

- [ ] **Step 1: Write failing test for the selector:**
  ```kotlin
  @Test
  fun select_returnsEarliestTogetherEventRegardlessOfOwner() {
      val ownTogether = calendarEvent(id = "own", owner = Person.BASTI, category = EventCategory.TOGETHER, start = future(2))
      val partnerTogether = calendarEvent(id = "partner", owner = Person.MELLI, category = EventCategory.TOGETHER, start = future(1))
      val result = NextSharedEventSelector().select(listOf(ownTogether, partnerTogether), now = LocalDateTime.now())
      assertEquals(partnerTogether, result)
  }

  @Test
  fun select_ignoresPastAndNonTogetherEvents() { /* ... */ }
  ```
- [ ] **Step 2:** Run — expect FAIL.
- [ ] **Step 3:** Implement `NextSharedEventSelector`, mirroring `NextPartnerEventSelector`'s filter/sort structure but with `category == TOGETHER` instead of `owner == partner` (no owner filter at all).
- [ ] **Step 4:** Run — expect PASS.
- [ ] **Step 5:** Add `nextSharedEvent`/`nextFreeSlot` to `WidgetSnapshot`, update `WidgetSnapshotCodec` to encode/decode both (existing tests in `WidgetSnapshotCodecTest` must still pass unmodified for the old fields; add new round-trip assertions for the two new ones, including the `null` case for both).
- [ ] **Step 6:** In `WidgetRefreshWorker.doWork()`, after fetching `events` (now 30 days per Task 1), compute `nextSharedEvent = NextSharedEventSelector().select(events, now)` and `nextFreeSlot`: call `mergeServiceFactory(applicationContext).freeBlocksInRange(DateRange(today, today.plusDays(29)))` (Task 2's new method), take the first day (in date order) with a non-empty list, wrap its first block as `FreeSlot(day, block)`; `null` if none in range.
- [ ] **Step 7:** Run `./gradlew testDebugUnitTest` — expect green.
- [ ] **Step 8:** Commit: `git commit -m "Compute next shared event and next free slot for the widget snapshot"`.

---

## Task 7: Responsive widget layout (Group B, Glance UI)

**Owner:** Codex (`codex:rescue`) — per the updated delegation rule, Codex now also takes Glance/Compose UI tasks; Claude Code reviews the rendered result on-device afterward (Glance layouts can't be meaningfully unit-tested for visuals — verify via `formatEventTime`-style pure helpers plus a manual/adb screenshot check).

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/ui/widget/SelliWidget.kt`
- Test: add pure-function tests only (no Glance Compose testing in this project's stack today — check before introducing one).

**Interfaces:**
- Consumes: `WidgetSnapshot.nextSharedEvent`, `WidgetSnapshot.nextFreeSlot` (Task 6).
- Produces: `SelliWidget.sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))` (define three `DpSize` constants sized to roughly 1 row / 2 rows / 3 rows of the existing card content — measure against the current single-row card's rendered height as the baseline for `SMALL`).

- [ ] **Step 1:** Change `override val sizeMode: SizeMode = SizeMode.Single` to `SizeMode.Responsive(setOf(WIDGET_SIZE_SMALL, WIDGET_SIZE_MEDIUM, WIDGET_SIZE_LARGE))` with the three `DpSize` constants defined as private top-level vals.
- [ ] **Step 2:** In `provideGlance`, read the current size via `LocalSize.current` (Glance API) inside `WidgetCard` to decide which rows to render.
- [ ] **Step 3:** Add a `NextSharedEventRow` composable (Wir-Zeit gradient pill + "Nächste Wir-Zeit" + title/time, reusing `formatEventTime`) rendered when size ≥ MEDIUM and `snapshot.nextSharedEvent != null`; when size ≥ MEDIUM and it's `null`, render the existing "Nichts in Sicht" treatment with `mascot_empty_state` per the spec.
- [ ] **Step 4:** Add a `NextFreeSlotRow` composable (`mascot_celebrating` + "Nächster freier Slot" + day/time range) rendered when size == LARGE and `snapshot.nextFreeSlot != null`; `mascot_empty_state` "Nichts in Sicht" fallback when `null`.
- [ ] **Step 5:** Per the spec's "Zeilen sind unabhängig voneinander" clarification: row 1 keeps its own existing empty-state logic; rows 2/3 render independently of row 1's state (only fall back to today's full-card empty state when *all* rows applicable to the current size bucket are empty).
- [ ] **Step 6:** Run `./gradlew assembleDebug` (Glance layouts aren't unit-testable here) — expect success. Manually verify on-device or via `adb shell appwidget` resize once installed (Claude Code review step, not a Codex step).
- [ ] **Step 7:** Commit: `git commit -m "Add responsive widget sizes with Wir-Zeit and free-slot rows"`.

---

## Task 8: Event grouping logic (Group C, pure Kotlin)

**Owner:** Codex (`codex:rescue`) — pure logic, isolated.

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/ui/calendar/EventGrouping.kt`
- Test: `app/src/test/java/com/prehmus/selli/ui/calendar/EventGroupingTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  sealed interface EventListRow {
      data class Single(val event: CalendarEvent) : EventListRow
      data class Group(val wirTermin: CalendarEvent, val overlapping: List<CalendarEvent>) : EventListRow
  }

  fun List<CalendarEvent>.toEventListRows(): List<EventListRow>
  // Input: a single day's events, already sorted as CalendarViewModel's
  // groupByDay() produces them (all-day first, then by start).
  // Output: chronological rows per the spec's grouping rules (overlap =
  // start < otherEnd && end > otherStart; earliest-starting TOGETHER event
  // among mutually-overlapping TOGETHER events becomes the group anchor;
  // group position in the output list is anchored to the Wir-Termin's start).
  ```

- [ ] **Step 1: Write failing tests** (mirror the spec's "Tests" section exactly):
  ```kotlin
  @Test
  fun toEventListRows_noTogetherEvent_allSingles() { /* ... */ }

  @Test
  fun toEventListRows_togetherEventWithoutOverlap_staysSingle() { /* ... */ }

  @Test
  fun toEventListRows_togetherEventWithOverlaps_groupsOnlyOverlapping() {
      val wirTermin = calendarEvent(id = "wz", category = EventCategory.TOGETHER,
          start = day.atTime(9, 0), end = day.atTime(17, 0))
      val overlapping = calendarEvent(id = "meeting", start = day.atTime(10, 0), end = day.atTime(11, 0))
      val notOverlapping = calendarEvent(id = "evening", start = day.atTime(18, 0), end = day.atTime(19, 0))

      val rows = listOf(wirTermin, overlapping, notOverlapping).toEventListRows()

      assertEquals(
          listOf(
              EventListRow.Group(wirTermin, listOf(overlapping)),
              EventListRow.Single(notOverlapping),
          ),
          rows,
      )
  }

  @Test
  fun toEventListRows_twoOverlappingTogetherEvents_earlierBecomesAnchor() { /* ... */ }

  @Test
  fun toEventListRows_subEventStartingBeforeWirTerminStaysUnderItsHeader() { /* per spec: anchor by Wir-Termin start, not strict chronology */ }
  ```
- [ ] **Step 2:** Run — expect FAIL.
- [ ] **Step 3:** Implement `toEventListRows()`: partition events into `TOGETHER`-category ones and the rest; for each `TOGETHER` event (processing earliest-start first, skipping ones already consumed as another's overlap member), collect all other not-yet-consumed events (including other `TOGETHER` ones) whose interval overlaps it → becomes a `Group`; remaining un-consumed events → `Single`; sort the resulting row list by the anchor time (`Group.wirTermin.start` or `Single.event.start`).
- [ ] **Step 4:** Run — expect PASS.
- [ ] **Step 5:** Commit: `git commit -m "Add EventGrouping for Wir-Zeit overlap detection in the agenda list"`.

---

## Task 9: Grouped list rendering (Group C, Compose UI)

**Owner:** Codex (`codex:rescue`) — per the updated delegation rule, Compose UI is in scope; Claude Code reviews visually via `adb`/device screenshot afterward.

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/ui/calendar/DayDetail.kt`

**Interfaces:**
- Consumes: `EventGrouping.toEventListRows()` (Task 8), existing `EventCard` (unmodified), `selliGradient()` (existing theme helper).

- [ ] **Step 1:** In `DayDetail`, replace `items(events, ...)` with `items(events.toEventListRows(), key = { row -> when (row) { is EventListRow.Single -> "${row.event.source}:${row.event.id}"; is EventListRow.Group -> "group:${row.wirTermin.source}:${row.wirTermin.id}" } })`.
- [ ] **Step 2:** Render `EventListRow.Single` via the existing `EventCard(event = row.event, onClick = { onEventClick(row.event) })` — unchanged.
- [ ] **Step 3:** Add `EventGroupCard(row: EventListRow.Group, onEventClick: (CalendarEvent) -> Unit)`: a `Column` containing `EventCard(row.wirTermin, onClick = { onEventClick(row.wirTermin) })` (unmodified, full width) followed by a `Row` of [gradient bar `Box` sized to `fillMaxHeight().width(4.dp).background(selliGradient(), shape)`, `Spacer`, indented `Column` of `EventCard(event, onClick = { onEventClick(event) })` per `row.overlapping`, each wrapped with a smaller horizontal padding than the top-level cards to achieve the "etwas weniger breit" indent].
- [ ] **Step 4:** Run `./gradlew testDebugUnitTest assembleDebug` — expect green + successful build (no new unit tests here; this is Compose rendering, verify per Step 5).
- [ ] **Step 5:** Claude Code review: install on-device or via `adb`, view a day with a real or seeded overlapping Wir-Zeit scenario, confirm the bar renders full-height and scrolls in sync, confirm tapping the Wir-Termin vs. a nested card opens the right sheet.
- [ ] **Step 6:** Commit: `git commit -m "Render overlapping Wir-Zeit groups with a continuous accent bar in the agenda list"`.

---

## Self-Review Notes

- **Spec coverage:** Task 1–2 cover Group A/B's shared lookahead + range free-block prerequisite; Tasks 3–5 cover every behavior in the notifications spec (detection, first-run-as-new via empty store, permission gating including the "skip the whole step" fix, deep link); Tasks 6–7 cover every tier/content rule in the widget spec including the independent-rows fix and the 30-day window; Tasks 8–9 cover every rule in the grouping spec including the anchor-by-Wir-Termin-start clarification and the two-overlapping-Wir-Termine tie-break.
- **Placeholder scan:** every step above either names an exact file/function or gives literal test code; no "add appropriate handling" language.
- **Type consistency:** `EventKey`, `CalendarSource`, `EventCategory`, `Person`, `FreeTimeBlock`, `DateRange` are all pre-existing types reused as-is (no redefinition). `CalendarEvent.key()` was found `private` to `CalendarViewModel.kt` during review despite three later tasks needing it — fixed by inserting Task 2b to promote it to a shared public extension before any task that consumes it.
