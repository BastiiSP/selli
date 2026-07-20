# Melli ICS Resilienz Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transiente Dr.-Plano-ICS-Fehler automatisch begrenzt wiederholen und verbleibende Quellfehler zusammen mit erfolgreichen Kalenderterminen an die UI liefern.

**Architecture:** Das ICS-Repository klassifiziert Transport- und HTTP-Fehler und kapselt drei Versuche mit injizierbarem Backoff. Der Merge-Service liefert intern ein gemeinsames Ergebnis aus Events und Quellfehlern; die alte Event-only-API delegiert darauf.

**Tech Stack:** Kotlin 2.0, kotlinx-coroutines 1.9, OkHttp/MockWebServer 4.12, JUnit 4

## Global Constraints

- Keine Änderungen unter `ui/`, an `CalendarViewModel.kt` oder `DefaultAppDependencies.kt`.
- Öffentliche Konstruktoren bleiben durch angehängte Default-Parameter quellkompatibel.
- `CancellationException` wird nie gefangen oder wiederholt; `CalendarAuthRequiredException` wird weitergereicht.
- Coroutine-Exceptiontests vergleichen Typ und Nachricht, nie Objektidentität.
- Höchstens drei Versuche; Retry nur für `IOException`, HTTP 429 und HTTP 5xx.

---

### Task 1: Retry-Vertrag des ICS-Repositories

**Files:**
- Modify: `app/src/test/java/com/prehmus/selli/data/ics/OkHttpIcsCalendarRepositoryTest.kt`
- Modify: `app/src/main/java/com/prehmus/selli/data/ics/OkHttpIcsCalendarRepository.kt`

**Interfaces:**
- Consumes: `OkHttpClient`, `IcsCalendarParser`, `DateRange`
- Produces: Konstruktorparameter `backoff: suspend (attempt: Int) -> Unit` mit Default sowie maximal drei klassifizierte Fetch-Versuche

- [ ] **Step 1: Write failing retry tests**

  Ergänze MockWebServer-Fälle für HTTP 503 gefolgt von 200 und HTTP 400. Erfasse Backoff-Aufrufe in einer Liste und prüfe `[1]` beziehungsweise leer.

- [ ] **Step 2: Write failing cancellation test**

  Injiziere einen Client-Interceptor, der `CancellationException("cancelled")` wirft; prüfe Typ und Nachricht sowie keinen Backoff-Aufruf.

- [ ] **Step 3: Run tests to verify RED**

  Run: `./gradlew testDebugUnitTest --tests '*OkHttpIcsCalendarRepositoryTest'`
  Expected: FAIL, weil der Backoff-Konstruktorparameter und Retry fehlen.

- [ ] **Step 4: Implement minimal retry and default client**

  Baue den Default-Client mit 15s Connect-, 20s Read-/Write-, 30s Call-Timeout und `retryOnConnectionFailure(true)`. Führe bis zu drei Versuche aus, schließe jede Response, wiederhole 429/5xx und `IOException`, und verwende standardmäßig exponentielle Delays von 250 ms und 500 ms.

- [ ] **Step 5: Run repository tests to verify GREEN**

  Run: `./gradlew testDebugUnitTest --tests '*OkHttpIcsCalendarRepositoryTest'`
  Expected: PASS.

### Task 2: Sichtbarer Merge-Status

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/domain/model/SourceLoadError.kt`
- Modify: `app/src/main/java/com/prehmus/selli/domain/CalendarMergeService.kt`
- Modify: `app/src/test/java/com/prehmus/selli/domain/merge/DefaultCalendarMergeServiceTest.kt`
- Modify: `app/src/main/java/com/prehmus/selli/domain/merge/DefaultCalendarMergeService.kt`

**Interfaces:**
- Consumes: bestehende drei Quell-Repositories und `CalendarLogger`
- Produces: `SourceLoadError`, `MergedCalendar`, `CalendarMergeService.mergedEventsWithStatus(DateRange)`

- [ ] **Step 1: Write failing status tests**

  Prüfe, dass Google- und Melli-Fehler exakt `Google-Kalender` und `Mellis Arbeitskalender` samt Exceptionnachricht liefern, während Bastis erfolgreiches Event erhalten bleibt. Prüfe separat vollständigen Erfolg mit leerer Fehlerliste.

- [ ] **Step 2: Run merge tests to verify RED**

  Run: `./gradlew testDebugUnitTest --tests '*DefaultCalendarMergeServiceTest'`
  Expected: FAIL, weil Statusmodelle und Methode fehlen.

- [ ] **Step 3: Add backward-compatible domain contract**

  Definiere die beiden Datenklassen und eine Interface-Defaultmethode, die `MergedCalendar(mergedEvents(range), emptyList())` zurückgibt.

- [ ] **Step 4: Implement isolated source results**

  Lass jede Async-Quelle intern Events plus optionalen Fehler liefern, logge Originalexceptions, reiche Cancellation/Auth weiter und führe Events und Fehler nach `await` zusammen. `mergedEvents` gibt `mergedEventsWithStatus(range).events` zurück.

- [ ] **Step 5: Run merge tests to verify GREEN**

  Run: `./gradlew testDebugUnitTest --tests '*DefaultCalendarMergeServiceTest'`
  Expected: PASS einschließlich bestehender Merge-/Freizeitfälle.

### Task 3: Untersuchung und Gesamtverifikation

**Files:**
- Create: `docs/superpowers/investigations/2026-07-20-melli-ics-verschwindet.md`

**Interfaces:**
- Consumes: bestätigte Codefakten, neue sichtbare Fehlermeldungen und bestehendes Logtag `SelliCalendar`
- Produces: reproduzierbare Diagnoseanleitung für das nächste Auftreten

- [ ] **Step 1: Document evidence and hypothesis limits**

  Dokumentiere Symptom, Fakten, Alternativhypothesen, Root-Cause-Hypothese, Fixwirkung und den Hinweis, dass ohne Mellis Gerät keine vollständige Reproduktion möglich war.

- [ ] **Step 2: Run full verification**

  Run: `./gradlew clean testDebugUnitTest assembleDebug`
  Expected: PASS. Falls die angekündigte Sandbox Gradle verhindert, dokumentiere exakt die lokale Grenze und übergib denselben Befehl an den Orchestrator.

- [ ] **Step 3: Copy APK only after successful build**

  Run: `cp app/build/outputs/apk/debug/app-debug.apk selli.apk`
  Expected: Die aktuelle Debug-APK liegt ausschließlich als `selli.apk` im Projektroot; bei nicht ausführbarem Build wird keine alte APK als neu ausgegeben.

- [ ] **Step 4: Audit scope and diff**

  Prüfe mit `git diff --check`, `git status --short` und `git diff --name-only`, dass keine verbotenen Dateien geändert wurden und alle Vertragsanforderungen abgedeckt sind.
