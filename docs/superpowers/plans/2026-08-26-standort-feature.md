# Standort-Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Vor dem Start jeder Aufgabe:** Lies `CLAUDE.md` im Projekt-Root und nutze das LSP-Tool, um die betroffenen Dateien zu verstehen, bevor du sie änderst.

**Goal:** Selli zeigt beide Personen live auf einer Karte — jede Position als
Kartenmarker in der Personenfarbe mit dem bestehenden Avatar-Gesicht. Die eigene
Position wird akkubewusst im Hintergrund erfasst und über eine neu angebundene
Supabase-Instanz (Free Tier) in Echtzeit mit der jeweils anderen Person geteilt.

**Architecture:** Supabase als einziges neues Backend, ausschließlich für Standorte —
Kalender/Google/ICS bleiben unberührt. Die bestehende Google-Anmeldung bleibt Quelle
der Wahrheit und wird nur ergänzt: ihr Google-ID-Token dient einmalig als
`signInWithIdToken` für Supabase, das danach eine eigene, selbst erneuerte Session
führt. Eine Zeile pro Person in `public.locations`, per Upsert überschrieben; RLS
gegen eine von Basti befüllte Allowlist mit genau zwei E-Mail-Adressen. Ein
Foreground-Service mit adaptiver Taktung liefert die Fixes.

**Tech Stack:** Kotlin, Jetpack Compose, supabase-kt (postgrest/realtime/auth),
Ktor-Android-Engine, Google Maps (`maps-compose` + `play-services-maps`),
`play-services-location` (FusedLocationProviderClient), JUnit4, MockWebServer.

**Spec:** `docs/superpowers/specs/2026-08-26-grundgeruest-standort-design.md`

**Voraussetzung:** `docs/superpowers/plans/2026-08-26-app-grundgeruest.md` ist
umgesetzt — dieser Plan hängt sich in das dort entstandene Ziel
`SelliDestination.LOCATION` ein.

## Global Constraints

- **Owner-Aufteilung** (nach `CLAUDE.md`, nicht nach den „Owner: Codex"-Labels des
  Handover-Prompts — die berücksichtigen die Datei-Ownership-Regel nicht):
  - Tasks 2–7 liegen in `data/`, `domain/`, Gradle/Manifest → **Codex**
  - Tasks 1, 8 liegen in `ui/` bzw. sind Infrastruktur-Provisionierung → **Claude**
- **Keine Secrets im Code.** `selli.supabaseUrl`, `selli.supabaseAnonKey`,
  `selli.mapsApiKey` gehören ausschließlich in `local.properties` und werden als
  `BuildConfig`-Felder bzw. `manifestPlaceholders` bereitgestellt. Nie committen.
- **Feature degradiert still**, wenn Werte fehlen: leere `supabaseUrl` → Standort-Tab
  zeigt Hinweiszustand, kein Absturz (Vorbild: `PLACES_API_KEY` heute).
- Kein Ein/Aus-Schalter für die Standortfreigabe, keine Distanz-/Entfernungsanzeige.
- Keine Standort-Historie, keine Spur — nur die jeweils aktuelle Position.
- Bestehende Google-Sign-in-Implementierung wird **nur ergänzt**, nicht ersetzt.
- Deutsche UI-Texte und KDoc-Kommentare.
- Nach jeder Aufgabe: `./gradlew testDebugUnitTest assembleDebug` grün. Bei
  `Unable to locate a Java Runtime`:
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

---

## Task 1: Supabase-Projekt provisionieren

**Owner:** Claude (Infrastruktur, kein Projektcode)

**Voraussetzung:** Die Supabase-MCP-Anbindung muss in der Session verbunden sein
(`/mcp` → `supabase` → authentifizieren). Ohne sie ist dieser Task blockiert; dann
statt Abbruch die SQL-Migration unten an Basti übergeben, damit er sie im Dashboard
ausführt.

- [ ] **Step 1: Projekt anlegen**

Organisation auflisten, dann Projekt `selli` in der Region `eu-central-1`
(Frankfurt — kürzeste Latenz, DSGVO-Raum) auf dem Free Tier anlegen.

- [ ] **Step 2: Schema-Migration anwenden**

Als getrackte Migration (`apply_migration`, Name `selli_locations`):

```sql
-- Allowlist: genau die zwei bekannten Konten. Von Hand befüllt, nicht von der App.
create table public.selli_members (
  email  text primary key,
  person text not null unique check (person in ('BASTI','MELLI'))
);

-- Aktueller Standort, eine Zeile pro Person, per Upsert überschrieben.
-- Bewusst keine Historie: "Bewegung läuft mit" entsteht durch die Marker-Animation.
create table public.locations (
  user_id         uuid primary key references auth.users(id) on delete cascade,
  person          text not null check (person in ('BASTI','MELLI')),
  latitude        double precision not null,
  longitude       double precision not null,
  accuracy_meters double precision,
  speed_mps       double precision,
  is_moving       boolean not null default false,
  updated_at      timestamptz not null default now()
);

-- security definer, damit die Policies die Allowlist lesen können,
-- ohne sie für Clients zu öffnen.
create or replace function public.is_selli_member() returns boolean
  language sql stable security definer set search_path = public as $$
    select exists (
      select 1 from public.selli_members m
      where lower(m.email) = lower(auth.jwt() ->> 'email')
    )
  $$;

alter table public.selli_members enable row level security;
-- Absichtlich keine Policy: nur is_selli_member() liest diese Tabelle.

alter table public.locations enable row level security;

create policy "members read both positions" on public.locations
  for select to authenticated using (public.is_selli_member());

create policy "members write only their own row" on public.locations
  for insert to authenticated
  with check (public.is_selli_member() and user_id = auth.uid());

create policy "members update only their own row" on public.locations
  for update to authenticated
  using (public.is_selli_member() and user_id = auth.uid())
  with check (public.is_selli_member() and user_id = auth.uid());

-- Kein delete: Positionen werden überschrieben, nicht gelöscht.

-- Echtzeit für die Partnerzeile.
alter publication supabase_realtime add table public.locations;
alter table public.locations replica identity full;
```

- [ ] **Step 3: Allowlist befüllen**

Basti nach den zwei echten Google-Adressen fragen (Bastis eigene ist bekannt, Mellis
nicht) und einfügen:

```sql
insert into public.selli_members (email, person)
values ('<basti@…>', 'BASTI'), ('<melli@…>', 'MELLI')
on conflict (email) do update set person = excluded.person;
```

- [ ] **Step 4: Google-Auth-Provider aktivieren**

Google als Auth-Provider einschalten und die Client-ID aus
`local.properties` → `selli.googleServerClientId` unter den erlaubten Client-IDs
eintragen. **Kein Provider-Secret** — für `signInWithIdToken` nicht nötig.
Vorher Basti bestätigen lassen, dass die Client-ID an Supabase übertragen werden darf.

- [ ] **Step 5: Sicherheitsprüfung**

`get_advisors` für `security` und `performance` laufen lassen und gemeldete Punkte
mit Folgemigrationen beheben (erwartbar: fehlender Index ist hier irrelevant, weil
die Tabelle zwei Zeilen hat — dann begründet abweisen statt blind zu indexieren).

- [ ] **Step 6: Werte an Basti übergeben**

Projekt-URL und `anon`-Key ausgeben mit der Anweisung, sie in `local.properties`
einzutragen:

```
selli.supabaseUrl=https://<ref>.supabase.co
selli.supabaseAnonKey=<anon key>
```

Den `service_role`-Key **nicht** ausgeben und nicht in der App verwenden.

---

## Task 2: Build-Konfiguration für Supabase und Maps

**Owner:** Codex

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `README.md` (Abschnitt zu `local.properties`)

**Interfaces:**
- Produces: `BuildConfig.SUPABASE_URL`, `BuildConfig.SUPABASE_ANON_KEY` (beide `String`,
  leer wenn nicht gesetzt — genau wie `PLACES_API_KEY`)
- Produces: `manifestPlaceholders["MAPS_API_KEY"]`

- [ ] **Step 1: Version-Catalog ergänzen**

```toml
# [versions]
supabase = "3.1.4"
ktor = "3.0.3"
mapsCompose = "6.4.1"
playServicesMaps = "19.0.0"
playServicesLocation = "21.3.0"

# [libraries]
supabase-bom = { group = "io.github.jan-tennert.supabase", name = "bom", version.ref = "supabase" }
supabase-postgrest = { group = "io.github.jan-tennert.supabase", name = "postgrest-kt" }
supabase-realtime = { group = "io.github.jan-tennert.supabase", name = "realtime-kt" }
supabase-auth = { group = "io.github.jan-tennert.supabase", name = "auth-kt" }
ktor-client-android = { group = "io.ktor", name = "ktor-client-android", version.ref = "ktor" }
maps-compose = { group = "com.google.maps.android", name = "maps-compose", version.ref = "mapsCompose" }
play-services-maps = { group = "com.google.android.gms", name = "play-services-maps", version.ref = "playServicesMaps" }
play-services-location = { group = "com.google.android.gms", name = "play-services-location", version.ref = "playServicesLocation" }
```

**Vor dem Festschreiben verifizieren:** ob `supabase-kt 3.1.4` mit Kotlin 2.0.21
kompatibel ist. supabase-kt 3.x setzt Kotlin 2.0+ voraus, das passt; scheitert die
Auflösung trotzdem, die letzte 3.0.x-Version nehmen und die Abweichung im Commit
begründen. Nicht auf 2.x zurückfallen — das braucht eine andere API
(`gotrue-kt` statt `auth-kt`) und würde Task 4 ungültig machen.

- [ ] **Step 2: `defaultConfig` erweitern**

```kotlin
        buildConfigField("String", "SUPABASE_URL", buildConfigStringProperty("selli.supabaseUrl"))
        buildConfigField("String", "SUPABASE_ANON_KEY", buildConfigStringProperty("selli.supabaseAnonKey"))

        // Maps liest den Key aus dem Manifest, nicht aus BuildConfig — daher Platzhalter.
        manifestPlaceholders["MAPS_API_KEY"] = localProperties.getProperty("selli.mapsApiKey", "")
```

- [ ] **Step 3: Dependencies ergänzen**

```kotlin
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.android)
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
```

- [ ] **Step 4: Manifest erweitern**

Berechtigungen bei den bestehenden ergänzen:

```xml
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
```

Im `<application>`-Block:

```xml
        <meta-data
            android:name="com.google.android.geo.API_KEY"
            android:value="${MAPS_API_KEY}" />

        <service
            android:name=".data.location.SelliLocationService"
            android:exported="false"
            android:foregroundServiceType="location" />
```

- [ ] **Step 5: Build laufen lassen**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (auch mit leeren `local.properties`-Werten).

- [ ] **Step 6: README fortschreiben und committen**

Die drei neuen `local.properties`-Schlüssel im README dokumentieren, inklusive des
Hinweises, dass „Maps SDK for Android" in der Cloud Console aktiv sein muss.

---

## Task 3: Google-ID-Token additiv verfügbar machen

**Owner:** Codex

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/data/google/GoogleCalendarDataRepository.kt`
- Create: `app/src/main/java/com/prehmus/selli/domain/repository/GoogleIdTokenProvider.kt`
- Test: `app/src/test/java/com/prehmus/selli/data/google/GoogleIdTokenProviderTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  interface GoogleIdTokenProvider {
      /** Rohes Google-ID-Token der letzten Anmeldung, oder null wenn keine vorliegt. */
      fun lastGoogleIdToken(): String?
  }
  ```
- `GoogleCalendarDataRepository` implementiert es zusätzlich zu den drei bestehenden
  Interfaces. **Kein bestehendes Verhalten ändern.**

- [ ] **Step 1: Failing test schreiben**

Prüft, dass `signIn()` das Token ablegt und `lastGoogleIdToken()` es zurückgibt, und
dass ohne Anmeldung `null` kommt. Das bestehende Test-Setup dieser Klasse
wiederverwenden (mit LSP ansehen, wie andere `GoogleCalendarDataRepository`-Tests
den `CredentialManager` umgehen — lässt sich der nicht faken, stattdessen den
`SharedPreferences`-Pfad direkt testen: schreiben über die neue interne
`persistGoogleIdToken`-Funktion, lesen über `lastGoogleIdToken()`).

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

Run: `./gradlew testDebugUnitTest --tests "*GoogleIdTokenProviderTest*"`
Expected: FAIL.

- [ ] **Step 3: Implementieren**

In `signIn()`, direkt nach `val credential = GoogleIdTokenCredential.createFrom(...)`
und **ohne** den bestehenden Ablauf zu verändern:

```kotlin
            // Zusätzlich zum bestehenden Ablauf: Supabase koppelt sich später einmalig mit
            // diesem Token an und führt danach eine eigene, selbst erneuerte Session.
            persistGoogleIdToken(credential.idToken)
```

Dazu die Klasse um `GoogleIdTokenProvider` erweitern und ergänzen:

```kotlin
    private fun persistGoogleIdToken(idToken: String?) {
        preferences.edit().putString(KEY_GOOGLE_ID_TOKEN, idToken).apply()
    }

    override fun lastGoogleIdToken(): String? =
        preferences.getString(KEY_GOOGLE_ID_TOKEN, null)?.takeUnless(String::isBlank)
```

`KEY_GOOGLE_ID_TOKEN = "google_id_token"` zu den bestehenden Konstanten im
`companion object` ergänzen. In `resetSession()` das Token mit entfernen — „Konto
wechseln" soll auch die Supabase-Kopplung vergessen.

- [ ] **Step 4: Tests laufen lassen**

Run: `./gradlew testDebugUnitTest`
Expected: PASS, alle bestehenden Google-Tests weiterhin grün.

- [ ] **Step 5: Commit**

---

## Task 4: Supabase-Client mit Auth-Kopplung

**Owner:** Codex

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/data/supabase/SelliSupabaseClient.kt`
- Test: `app/src/test/java/com/prehmus/selli/data/supabase/SelliSupabaseClientTest.kt`

**Interfaces:**
- Consumes: `GoogleIdTokenProvider` (Task 3), `BuildConfig.SUPABASE_URL/SUPABASE_ANON_KEY` (Task 2)
- Produces:
  ```kotlin
  class SelliSupabaseClient(
      private val supabaseUrl: String,
      private val supabaseAnonKey: String,
      private val idTokenProvider: GoogleIdTokenProvider,
      private val logger: CalendarLogger = NoOpCalendarLogger,
  ) {
      /** false, wenn URL oder Key fehlen — dann bleibt das Standort-Feature inaktiv. */
      val isConfigured: Boolean

      /**
       * Stellt sicher, dass eine Supabase-Session besteht: vorhandene Session wird
       * wiederverwendet (supabase-kt erneuert sie selbst), sonst einmaliger
       * signInWithIdToken mit dem Google-ID-Token.
       */
      suspend fun ensureSignedIn(): Result<Unit>

      /** Eigene Supabase-User-ID; null, solange keine Session besteht. */
      suspend fun currentUserId(): String?

      /** Der rohe Client für Postgrest/Realtime. Wirft, wenn [isConfigured] false ist. */
      val client: SupabaseClient
  }
  ```

- [ ] **Step 1: Failing test für `isConfigured` schreiben**

Drei Fälle: beide Werte gesetzt → `true`; URL leer → `false`; Key leer → `false`.
Zusätzlich: `ensureSignedIn()` auf einer unkonfigurierten Instanz gibt
`Result.failure` zurück **statt zu werfen**, und `currentUserId()` gibt `null`.
Das sind die einzigen ohne Netzwerk testbaren Zusicherungen — mehr hier nicht
vortäuschen; der Anmeldepfad selbst wird am Gerät verifiziert (Task 9).

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

- [ ] **Step 3: Implementieren**

```kotlin
        private val supabase: SupabaseClient? = if (isConfigured) {
            createSupabaseClient(supabaseUrl = supabaseUrl, supabaseKey = supabaseAnonKey) {
                install(Auth)
                install(Postgrest)
                install(Realtime)
            }
        } else {
            null
        }
```

`ensureSignedIn()`:

```kotlin
    suspend fun ensureSignedIn(): Result<Unit> {
        val supabase = supabase
            ?: return Result.failure(IllegalStateException("Supabase ist nicht konfiguriert."))
        // Bestehende Session gewinnt: supabase-kt erneuert sie über ihren Refresh-Token
        // selbst, das kurzlebige Google-ID-Token wird also nur beim ersten Mal gebraucht.
        if (supabase.auth.currentSessionOrNull() != null) return Result.success(Unit)

        val idToken = idTokenProvider.lastGoogleIdToken()
            ?: return Result.failure(IllegalStateException("Keine Google-Anmeldung vorhanden."))

        return runCatching {
            supabase.auth.signInWith(IDToken) {
                provider = Google
                this.idToken = idToken
            }
        }.onFailure { error ->
            logger.log("Supabase-Anmeldung fehlgeschlagen", error)
        }
    }
```

**Vor dem Schreiben verifizieren:** die exakten Namen `IDToken`, `Google`,
`currentSessionOrNull()` und `signInWith` gegen die tatsächlich aufgelöste
supabase-kt-Version prüfen (LSP/Quellen), statt sie aus dem Gedächtnis zu setzen.
Das ist zwischen 2.x und 3.x umbenannt worden.

- [ ] **Step 4: Tests laufen lassen, Commit**

---

## Task 5: Standort-Datenmodell und Repository-Vertrag

**Owner:** Codex

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/domain/model/PersonLocation.kt`
- Create: `app/src/main/java/com/prehmus/selli/domain/repository/LocationRepository.kt`
- Test: `app/src/test/java/com/prehmus/selli/domain/model/PersonLocationTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class PersonLocation(
      val person: Person,
      val latitude: Double,
      val longitude: Double,
      val accuracyMeters: Double?,
      val speedMetersPerSecond: Double?,
      val isMoving: Boolean,
      val updatedAt: Instant,
  )

  /** Wie frisch die Position ist — treibt die Beschriftung unter dem Marker. */
  enum class LocationFreshness { LIVE, RECENT, STALE }

  fun PersonLocation.freshness(now: Instant): LocationFreshness
  ```
  Grenzen: `< 2 min` → `LIVE`, `< 30 min` → `RECENT`, sonst `STALE`.
- Produces:
  ```kotlin
  interface LocationRepository {
      /** Aktuelle Positionen beider Personen, live nachgeliefert. */
      fun observeLocations(): Flow<List<PersonLocation>>

      /** Eigene Position hochladen (Upsert auf die eigene Zeile). */
      suspend fun publishOwnLocation(location: PersonLocation): Result<Unit>
  }
  ```

- [ ] **Step 1: Failing test für `freshness` schreiben**

Vier Fälle an den Grenzen: 0 s → `LIVE`, 119 s → `LIVE`, 121 s → `RECENT`,
31 min → `STALE`. Grenzen exakt testen, nicht nur die Mitte der Intervalle.

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

- [ ] **Step 3: Implementieren**

- [ ] **Step 4: Tests laufen lassen, Commit**

---

## Task 6: Supabase-Standort-Repository mit Echtzeit

**Owner:** Codex

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/data/location/SupabaseLocationRepository.kt`
- Create: `app/src/main/java/com/prehmus/selli/data/location/LocationRow.kt` (`@Serializable` DTO)
- Test: `app/src/test/java/com/prehmus/selli/data/location/LocationRowTest.kt`

**Interfaces:**
- Consumes: `SelliSupabaseClient` (Task 4), `PersonLocation`/`LocationRepository` (Task 5)
- Produces: `class SupabaseLocationRepository(client: SelliSupabaseClient, ownPerson: () -> Person, logger: CalendarLogger) : LocationRepository`
- Produces: `LocationRow` mit `fun toDomain(): PersonLocation` und
  `companion object { fun from(location: PersonLocation, userId: String): LocationRow }`
  — Spaltennamen als `@SerialName` exakt wie im Schema (`user_id`, `accuracy_meters`,
  `speed_mps`, `is_moving`, `updated_at`).

- [ ] **Step 1: Failing test für das DTO-Mapping schreiben**

Reine Serialisierungs-Tests ohne Netzwerk: `LocationRow` → JSON → `LocationRow`
Rundreise, und `from(...)`/`toDomain()` Rundreise. Prüft insbesondere, dass die
`@SerialName`-Werte den Schema-Spalten entsprechen — genau hier entsteht sonst der
Fehler „Could not find the '<column>' column in the schema cache".

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

- [ ] **Step 3: `LocationRow` implementieren**

- [ ] **Step 4: `SupabaseLocationRepository` implementieren**

`observeLocations()`: `flow { }`, das

1. `ensureSignedIn()` aufruft und bei Fehlschlag `emptyList()` emittiert statt zu werfen,
2. einmal den aktuellen Stand per `select()` lädt und emittiert,
3. auf `postgresChangeFlow<PostgresAction>` für `public.locations` abonniert und bei
   jeder Änderung den zusammengeführten Stand neu emittiert,
4. bei Verbindungsabbruch alle 60 s per `select()` nachpollt (Fallback laut Spec).

`publishOwnLocation()`: `upsert` mit `onConflict = "user_id"`. Fehler werden
geloggt und als `Result.failure` zurückgegeben, nie geworfen — ein fehlgeschlagener
Upload darf den Service nicht beenden.

- [ ] **Step 5: Tests laufen lassen, Commit**

---

## Task 7: Foreground-Service mit adaptiver Taktung

**Owner:** Codex

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/domain/location/LocationCadence.kt` (reine Logik, testbar)
- Create: `app/src/main/java/com/prehmus/selli/data/location/SelliLocationService.kt`
- Test: `app/src/test/java/com/prehmus/selli/domain/location/LocationCadenceTest.kt`

**Interfaces:**
- Consumes: `LocationRepository` (Task 5)
- Produces:
  ```kotlin
  enum class TrackingMode { STATIONARY, MOVING }

  data class CadenceSettings(
      val intervalMillis: Long,
      val minUpdateDistanceMeters: Float,
      val maxUpdateDelayMillis: Long,
      val highAccuracy: Boolean,
  )

  fun TrackingMode.settings(): CadenceSettings

  /**
   * Reine Entscheidungsfunktion, damit die Taktung ohne Android-Framework testbar bleibt.
   */
  fun nextTrackingMode(
      current: TrackingMode,
      speedMetersPerSecond: Float?,
      metersSinceLastUpload: Float,
      millisSinceLastMovement: Long,
  ): TrackingMode
  ```

Werte laut Spec, exakt:

| Modus | `intervalMillis` | `minUpdateDistanceMeters` | `maxUpdateDelayMillis` | `highAccuracy` |
| --- | --- | --- | --- | --- |
| `STATIONARY` | 300_000 | 150f | 900_000 | false |
| `MOVING` | 30_000 | 50f | 60_000 | true |

Übergangsregeln: `STATIONARY → MOVING` wenn `speedMetersPerSecond > 2.5f` **oder**
`metersSinceLastUpload > 200f`. `MOVING → STATIONARY` wenn keine der beiden
Bedingungen erfüllt ist **und** `millisSinceLastMovement >= 300_000`.

- [ ] **Step 1: Failing tests für `nextTrackingMode` schreiben**

Sechs Fälle: Stillstand bleibt Stillstand; Geschwindigkeit über der Schwelle startet
Bewegung; große Distanz ohne Geschwindigkeitswert (`null`) startet Bewegung; Bewegung
bleibt Bewegung, solange die Bedingung hält; Bewegung bleibt Bewegung bei Stillstand
unter 5 Minuten; Bewegung fällt nach genau 5 Minuten auf Stillstand zurück.
Zusätzlich: `settings()` liefert für beide Modi exakt die Tabellenwerte.

- [ ] **Step 2: Tests laufen lassen, Fehlschlag bestätigen**

- [ ] **Step 3: `LocationCadence.kt` implementieren**

- [ ] **Step 4: Tests laufen lassen** — Expected: PASS (8 Tests).

- [ ] **Step 5: `SelliLocationService` implementieren**

Foreground-Service, der

- im `onCreate` einen Notification-Kanal mit `IMPORTANCE_LOW` anlegt
  (ID `selli_location`, Name „Standortfreigabe") und still eine Notification zeigt:
  Titel „Selli teilt deinen Standort", `setOngoing(true)`, `setSilent(true)`,
  Tap öffnet `MainActivity`;
- `startForeground(id, notification, FOREGROUND_SERVICE_TYPE_LOCATION)` aufruft
  (ab API 29 die dreiargumentige Überladung, sonst die zweiargumentige);
- `FusedLocationProviderClient.requestLocationUpdates` mit einem aus
  `TrackingMode.settings()` gebauten `LocationRequest` registriert
  (`Priority.PRIORITY_HIGH_ACCURACY` bzw. `PRIORITY_BALANCED_POWER_ACCURACY`,
  `setWaitForAccurateLocation(false)`);
- bei jedem Fix `nextTrackingMode(...)` befragt und die Registrierung **nur bei
  echtem Moduswechsel** neu aufsetzt (sonst wackelt die Taktung ständig);
- jeden Fix per `publishOwnLocation` hochlädt und Fehler nur loggt;
- ohne `ACCESS_FINE_LOCATION` sofort `stopSelf()` aufruft statt eine
  `SecurityException` zu werfen.

Start/Stop: `companion object { fun start(context: Context); fun stop(context: Context) }`.

- [ ] **Step 6: Build laufen lassen, Commit**

---

## Task 8: Standort-Tab — Berechtigungen und Karte

**Owner:** Claude

**Files:**
- Create: `app/src/main/res/raw/map_style_selli.json`
- Create: `app/src/main/res/raw/map_style_selli_night.json`
- Create: `app/src/main/java/com/prehmus/selli/ui/location/LocationViewModel.kt`
- Create: `app/src/main/java/com/prehmus/selli/ui/location/LocationPermissionGate.kt`
- Create: `app/src/main/java/com/prehmus/selli/ui/location/LocationMap.kt`
- Create: `app/src/main/java/com/prehmus/selli/ui/location/LocationScreen.kt`
- Modify: `app/src/main/java/com/prehmus/selli/ui/shell/SelliShell.kt`
- Modify: `app/src/main/java/com/prehmus/selli/AppDependencies.kt`
- Modify: `app/src/main/java/com/prehmus/selli/DefaultAppDependencies.kt`

**Interfaces:**
- Consumes: `LocationRepository`, `PersonLocation`, `LocationFreshness`,
  `SelliLocationService.start/stop`, `SelliDestination.LOCATION`
- Produces: `AppDependencies.locationRepository: LocationRepository`
- Produces: `data class LocationUiState(val locations: List<PersonLocation>, val isSupabaseConfigured: Boolean)`
- Produces: `@Composable fun LocationScreen(viewModel: LocationViewModel, ownPerson: Person, modifier: Modifier)`

- [ ] **Step 1: Kartenstile schreiben**

`map_style_selli.json`: gedämpfte Grüntöne für `poi.park`/`landscape.natural`,
creme (`#F4EDE3`) für `road`, entsättigtes Wasser (`#CFDDE0`),
`poi.business` Labels aus, `transit` aus. `map_style_selli_night.json` dieselben
Elementtypen auf den Nacht-Neutraltönen aus `Color.kt` (`NightSurface`,
`NightSurfaceVariant`, `MilkTextSoft`).

- [ ] **Step 2: `LocationViewModel` schreiben**

Sammelt `observeLocations()` in einen `StateFlow<LocationUiState>` und startet den
Service, sobald die Vordergrund-Berechtigung vorliegt (Aufruf kommt aus der UI, das
ViewModel kennt keine Activity).

- [ ] **Step 3: `LocationPermissionGate` schreiben**

Zweistufig, wie in der Spec begründet:

1. Ohne `ACCESS_FINE_LOCATION`: freundlicher Hinweis mit `mascot_pondering` und
   Knopf „Standort freigeben" → `rememberLauncherForActivityResult(RequestMultiplePermissions)`
   für `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`.
2. Danach, ohne `ACCESS_BACKGROUND_LOCATION` (nur ab API 29): eigener
   Zwischenschritt mit dem Text „Damit Selli auch mitläuft, wenn das Handy in der
   Tasche steckt" und Knopf „Immer erlauben" → separater
   `RequestPermission`-Launcher. **Nicht** gemeinsam mit Stufe 1 anfragen — Android
   lehnt das stillschweigend ab.
3. Dauerhaft verweigert: Hinweis mit Knopf in die App-Einstellungen
   (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`). Die Karte bleibt sichtbar und
   zeigt die Position der Partnerin — nur die eigene fehlt.

- [ ] **Step 4: `LocationMap` schreiben**

`GoogleMap` aus `maps-compose` mit `MapProperties(mapStyleOptions = …)` je nach
`isSystemInDarkTheme()`. Für jede `PersonLocation` ein `Marker`:

```kotlin
    val target = LatLng(location.latitude, location.longitude)
    // Linear, weil eine Autofahrt gleichmäßig verläuft — Easing würde hier eine
    // Beschleunigung vortäuschen, die es nicht gibt.
    val animated by animateValueAsState(
        targetValue = target,
        typeConverter = LatLngConverter,
        animationSpec = tween(durationMillis = 1_200, easing = LinearEasing),
        label = "marker-${location.person}",
    )
```

`LatLngConverter: TwoWayConverter<LatLng, AnimationVector2D>` in derselben Datei
definieren. Marker-Icon aus `location_pin_basti`/`location_pin_melli` per
`BitmapDescriptorFactory.fromBitmap` auf 64 dp skaliert, `anchor = Offset(0.5f, 0.92f)`
(Pin-Spitze). Darunter die Frische-Beschriftung aus `freshness(...)`
(„gerade jetzt" / „vor N Min." / „vor längerer Zeit").

Kamera: beim ersten Datensatz auf beide Positionen einpassen
(`CameraUpdateFactory.newLatLngBounds`), danach **nicht** mehr automatisch verschieben —
sonst reißt es dem Nutzer die Karte unter den Fingern weg.

- [ ] **Step 5: `LocationScreen` schreiben**

Setzt Gate und Karte zusammen. Ist Supabase nicht konfiguriert
(`isSupabaseConfigured == false`), stattdessen ein ruhiger Hinweiszustand mit
`mascot_pondering` und dem Text „Standortfreigabe ist noch nicht eingerichtet" —
kein Absturz, kein leerer Bildschirm.

- [ ] **Step 6: Verdrahten**

`AppDependencies` um `locationRepository` erweitern, in `DefaultAppDependencies`
`SelliSupabaseClient` + `SupabaseLocationRepository` bauen, in `SelliShell` den
`LOCATION`-Platzhalter durch `LocationScreen` ersetzen. In
`ui/preview/PreviewDependencies.kt` eine Fake-Implementierung ergänzen, sonst
brechen die Previews.

- [ ] **Step 7: Build und Tests, Kontrastprüfung, Commit**

`ui-reviewer`-Subagent auf `app/src/main/java/com/prehmus/selli/ui/location/`.

---

## Task 9: Verifikation und Weitergabe

**Owner:** Claude

- [ ] **Step 1: Volle Verifikation**

```bash
./gradlew clean testDebugUnitTest assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk selli.apk
```

- [ ] **Step 2: Gerätetests auflisten, nicht behaupten**

Folgendes ist ohne echtes Gerät mit beiden echten Konten **nicht** verifizierbar und
gehört als offener Punkt ins Claudian-Update, nicht als „getestet":

- Supabase-`signInWithIdToken` mit dem echten Google-ID-Token
- RLS-Wirkung (sieht jede Person genau beide Zeilen, ein Dritter keine)
- Echtzeit-Zustellung der Partnerposition
- Adaptive Taktung im Auto und der tatsächliche Akkuverbrauch
- Zweistufiger Berechtigungsdialog auf einem echten Android 14+-Gerät
- Kartenmarker-Anker und Lesbarkeit der Kartenstile bei Tag und Nacht

- [ ] **Step 3: `CLAUDE.md` fortschreiben**

Neuer Punkt im Abschnitt „Bekannter Stand": Supabase als zweites Backend
(ausschließlich Standorte), Verweis auf Spec und Plan, und die Ergänzung, dass
`local.properties` jetzt drei weitere Schlüssel braucht.

---

## Self-Review

**Spec-Abdeckung**

| Spec-Anforderung | Task |
| --- | --- |
| Supabase Free Tier, Projekt + Schema + RLS + Google-Provider | 1 |
| Konfiguration nur über `local.properties`, stille Degradierung | 2, 4, 8 |
| Google-Sign-in unverändert, nur ergänzt (ID-Token) | 3 |
| `signInWithIdToken`, danach Supabase-eigene Session | 4 |
| Eine Zeile pro Person, Upsert, keine Historie | 1, 6 |
| Zugriff auf genau zwei Konten begrenzt | 1 |
| Echtzeit + 60-s-Polling-Fallback | 6 |
| Adaptive Taktung mit exakten Werten | 7 |
| Foreground-Service, stille Notification | 7 |
| Zweistufiger Berechtigungsdialog | 8 |
| Google Maps, warme Kartenstile hell/dunkel | 8 |
| Avatar-Marker in Personenfarbe, Pin-Spitze als Anker | 8 |
| Marker-Animation linear 1200 ms | 8 |
| Kein Ein/Aus-Schalter, keine Distanzanzeige | Global Constraints |

**Bewusst offen gelassen** (der Ausführende muss verifizieren statt raten):
- Task 2: tatsächliche supabase-kt-Version und Kotlin-Kompatibilität
- Task 4: exakte supabase-kt-3.x-API-Namen (`IDToken`, `Google`, `currentSessionOrNull`)
- Task 3: ob das bestehende Test-Setup den `CredentialManager` faken kann

**Typkonsistenz geprüft:** `GoogleIdTokenProvider.lastGoogleIdToken()` (3) wird in 4
so aufgerufen. `PersonLocation`/`LocationFreshness`/`freshness` (5) werden in 6 und 8
unter genau diesen Namen benutzt. `LocationRepository.observeLocations/publishOwnLocation`
(5) werden in 6 implementiert und in 7/8 so aufgerufen.
`TrackingMode`/`CadenceSettings`/`nextTrackingMode` (7) nur innerhalb 7.
`SelliSupabaseClient.isConfigured/ensureSignedIn/client` (4) werden in 6 und 8 so benutzt.
