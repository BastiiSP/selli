# Wir-Zeit Google Synchronisation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make „Wir-Zeit“ the single user-facing action that keeps a calendar event in both Google calendars.

**Architecture:** Derive partner invitation from the selected category when creating an event. Add a repository boundary for changing partner attendance on existing own Google events, implement it with Google Calendar event updates, and let the ViewModel persist local category state only after remote success.

**Tech Stack:** Kotlin, coroutines, Jetpack Compose, Google Calendar API v3, JUnit 4, MockWebServer

## Global Constraints

- „Wir-Zeit“ remains visible and always means the event is present in both calendars.
- The separate partner invitation switch is removed.
- All-day free-time blocking remains an independent local-only setting.
- Other attendees must be preserved.
- Partner and work calendar events remain read-only.
- Production behavior is implemented test-first.

---

### Task 1: Derive sharing from category during creation

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/ui/event/CreateEventSheet.kt`
- Modify: `app/src/main/java/com/prehmus/selli/ui/event/NewCalendarEventDraftFactory.kt`
- Test: `app/src/test/java/com/prehmus/selli/ui/event/NewCalendarEventDraftFactoryTest.kt`

**Interfaces:**
- Consumes: `EventCategory`
- Produces: `buildNewCalendarEvent(..., category: EventCategory)` with `invitePartner == (category == EventCategory.TOGETHER)`

- [ ] **Step 1: Write failing draft-factory tests**

Add tests with literal expectations that `TOGETHER` produces `invitePartner=true` and
`PRIVATE`/`WORK` produce `false`.

- [ ] **Step 2: Run the focused tests and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*NewCalendarEventDraftFactoryTest'`
Expected: compilation failure because the factory still accepts `invitePartner` instead of `category`.

- [ ] **Step 3: Implement minimal creation behavior**

Replace the factory parameter with `category: EventCategory`, derive
`invitePartner = category == EventCategory.TOGETHER`, remove `invitePartner` state and
the partner switch from `CreateEventSheet`, and pass the selected category.

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run: `./gradlew testDebugUnitTest --tests '*NewCalendarEventDraftFactoryTest'`
Expected: all focused tests pass.

### Task 2: Add Google partner-attendance updates

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/domain/repository/CalendarRepository.kt`
- Create: `app/src/main/java/com/prehmus/selli/data/google/GoogleCalendarEventSharing.kt`
- Modify: `app/src/main/java/com/prehmus/selli/data/google/GoogleCalendarDataRepository.kt`
- Test: `app/src/test/java/com/prehmus/selli/data/google/GoogleCalendarEventSharingTest.kt`

**Interfaces:**
- Produces: `suspend fun setPartnerAttendance(event: CalendarEvent, shared: Boolean, wholeSeries: Boolean): Result<Unit>`
- Consumes: stored partner email and Google Calendar `events.get`/`events.update`

- [ ] **Step 1: Write failing Google boundary tests**

Cover adding the configured partner while preserving another attendee, removing only
the partner, using the occurrence ID for a single occurrence, using `seriesId` for a
whole-series change, rejecting non-own sources, and setting `sendUpdates=all`.

- [ ] **Step 2: Run the focused tests and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*GoogleCalendarEventSharingTest'`
Expected: compilation failure because `GoogleCalendarEventSharing` does not exist.

- [ ] **Step 3: Implement minimal Google update**

Load the target Google event, normalize attendee emails case-insensitively, preserve
unrelated attendees, update the Selli shared extended property, and execute an update
with `sendUpdates=all`. Wire the helper through `GoogleCalendarDataRepository`.

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run: `./gradlew testDebugUnitTest --tests '*GoogleCalendarEventSharingTest'`
Expected: all focused tests pass.

### Task 3: Synchronize category changes in the ViewModel

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/ui/calendar/CalendarViewModel.kt`
- Modify: `app/src/main/java/com/prehmus/selli/ui/event/EventActionsSheet.kt`
- Test: `app/src/test/java/com/prehmus/selli/ui/calendar/CalendarViewModelTest.kt`

**Interfaces:**
- Consumes: `CalendarRepository.setPartnerAttendance`
- Produces: remote-first category transitions and user-visible success/failure state

- [ ] **Step 1: Write failing ViewModel tests**

Test `PRIVATE -> TOGETHER`, `TOGETHER -> PRIVATE`, failure without local save, and
`PRIVATE -> WORK` without a Google call using a recording repository.

- [ ] **Step 2: Run the focused tests and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*CalendarViewModelTest'`
Expected: new sharing assertions fail because category changes are still local-only.

- [ ] **Step 3: Implement remote-first category transitions**

When a transition crosses the `TOGETHER` boundary for an own Google event, call
`setPartnerAttendance` first. Save the merged local customization only on success,
refresh from Google, and expose an error on failure. Keep `WORK <-> PRIVATE` local.
Update the action sheet copy and series choice to describe the real Google effect;
prevent `TOGETHER` selection for read-only sources.

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run: `./gradlew testDebugUnitTest --tests '*CalendarViewModelTest'`
Expected: all focused tests pass.

### Task 4: Full verification and device installation

**Files:**
- Generated: `app/build/outputs/apk/debug/app-debug.apk`
- Generated/copy: `selli.apk`

**Interfaces:**
- Consumes: completed production and test changes
- Produces: tested APK installed on the paired Android device

- [ ] **Step 1: Run all tests and build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: `BUILD SUCCESSFUL` with zero failed tests.

- [ ] **Step 2: Refresh the handoff APK**

Run: `cp app/build/outputs/apk/debug/app-debug.apk selli.apk`
Expected: root `selli.apk` timestamp and size match the build output.

- [ ] **Step 3: Install over wireless ADB**

Run: `adb devices -l`, then
`adb -s <discovered-device> install -r app/build/outputs/apk/debug/app-debug.apk`
Expected: the device is listed as `device` and installation prints `Success`.

