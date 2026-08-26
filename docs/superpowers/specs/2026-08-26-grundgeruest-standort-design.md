# Redesign + Standort-Feature — Designentscheidungen

*Basis: Handover-Prompt vom 25./26.08.2026 (gebrainstormt mit Claudian über den
`grilling`-Skill). Dieses Dokument hält die technischen Entscheidungen fest, die der
Handover offen gelassen hat, und ist die Referenz für die beiden Umsetzungspläne:*

- `docs/superpowers/plans/2026-08-26-app-grundgeruest.md` (Grundgerüst + Profil)
- `docs/superpowers/plans/2026-08-26-standort-feature.md` (Supabase + Karte)

## Warum zwei Pläne

Der Handover beschreibt zwei unabhängige Subsysteme. Das Grundgerüst ist ohne
Standort-Feature lauffähig und auslieferbar (Kalender + Homescreen + leerer
Standort-Tab), das Standort-Feature braucht das Grundgerüst nur als Einhängepunkt.
Getrennte Pläne, damit jeder für sich grün und weitergabefähig ist.

## Teilaufgabe 1+2: Grundgerüst

### Navigationsbibliothek

`androidx.navigation:navigation-compose` (2.8.3). Bisher gab es keine
Navigationsbibliothek im Projekt, weil es nur einen Screen gab. Handgerollter
State-Switch wäre möglich, verliert aber Back-Stack-Verhalten — und das erklärte Ziel
ist Erweiterbarkeit. Vier Routen: `home`, `calendar`, `location`, `settings`.

### Aufteilung des heutigen Headers

`ui/calendar/MascotHeader.kt` (754 Zeilen) trägt heute fünf Dinge gleichzeitig. Die
werden auf drei Orte verteilt:

| Heutiger Inhalt | Neuer Ort |
| --- | --- |
| „Selli"-Wortmarke + `CoupleAvatars` | Globaler `SelliTopBar` (auf jedem Screen) |
| Überlaufmenü → „Ausgeblendet & angepasst", „Konto wechseln" | Profil-/Einstellungsbereich |
| Überlaufmenü → „Aktualisieren" | Kalender-Tab (eigenes Icon in der Zeitraumzeile); Pull-to-Refresh bleibt |
| Zeitraum-Titel + Pfeile ‹ › | Kalender-Tab (`CalendarPeriodBar`) |
| Wir-Zeit-Countdown-Szene (`WirZeitJourneyScene`) + Ein-/Ausklappen | Homescreen-Tab, ausgeklappt als Vollbild-Szene; Ein-/Ausklappen entfällt dort (Platz ist da) |

`LayoutPreferences.headerCollapsed` wird damit funktionslos — bleibt als
persistierter Wert bestehen (kein Migrations-Aufwand), verliert aber seinen Leser.
Die Kalender/Liste-Aufteilung (`calendarFraction`) bleibt unverändert in Gebrauch.

### Bottom-Navigation

Drei gleichwertige Ziele, Reihenfolge laut Handover („zentrales/mittleres Ziel"):

```
Kalender   |   Wir   |   Standort
```

**Start-Ziel: `home` („Wir").** Der Handover nennt es ausdrücklich das zentrale
Ziel; es ist der „auf einen Blick"-Screen. Ein-Zeilen-Änderung, falls Basti lieber
im Kalender startet.

Schmal gehalten: `NavigationBar` mit `windowInsetsPadding`, 64 dp Inhalt, Label
klein unter dem Icon. Aktives Ziel bekommt die Personenfarbe der *eigenen* Person
als Indikator — subtile Personalisierung ohne neues Farbsystem.

### Homescreen („Wir")

Reihenfolge von oben:

1. **Wanderweg-Szene** (`WirZeitJourneyScene`, unverändert übernommen) — jetzt mit
   voller Breite und ~220 dp Höhe statt gequetscht im Header.
2. **River Spirit** (`spirit_river.webp`) — neue Begleitfigur, driftet langsam
   und schwebend über der Szene (siehe „Animationen").
3. **„Nächste gemeinsame Termine"** — Liste der nächsten Wir-Zeit-Termine. Braucht
   neben dem bestehenden `nextWirZeitEvent` eine Liste; Feld
   `upcomingWirZeitEvents: List<CalendarEvent>` (max. 5) im `CalendarUiState`,
   gefüllt aus demselben 30-Tage-Fetch.
4. **Leerzustand**, wenn keine Wir-Zeit geplant ist: `mascot_pondering` +
   „Noch keine Wir-Zeit geplant" (die Szene zeigt in diesem Fall schon
   `mascot_pondering`, die Liste ergänzt den Text).

### Profil & Einstellungen

Erreichbar über ein Profil-Icon rechts im globalen Header (eigener `PersonAvatar`).
Eigener Screen (Route `settings`), nicht Bottom-Nav-Ziel. Inhalt, bewusst knapp:

1. **Profilkarte** — großer eigener `PersonAvatar` (96 dp) auf `selliGradient()`,
   darunter der Anzeigename.
2. **Verknüpftes Google-Konto** — eigene E-Mail; als Unterzeile die verknüpfte
   Partner-E-Mail (das „verknüpft" ist ohne beide Seiten sinnlos).
3. **„Ausgeblendet & angepasst"** — Zeile, die das bestehende
   `CustomizationManagerSheet` öffnet. Ersetzt den bisherigen Zugang im Header-Menü.
4. **„Konto wechseln"** — muss hierher, weil das Header-Menü wegfällt; sonst wäre
   die Funktion unerreichbar. Bestehender Bestätigungsdialog unverändert.

Datenquelle für 1+2: `SessionRepository.sessionState()` → `SessionState.Linked`
liefert `ownAccount` und `partnerAccount`. Neues, kleines `SettingsViewModel`.

### Animationen

Gefordert war, per `find-skills` nach einem stärkeren Animations-Skill zu suchen.
**Ergebnis: nichts Besseres gefunden.** Die Treffer mit hohen Installationszahlen
(`emilkowalski/skills@*` ~120K, `heygen-com/hyperframes` ~320K) sind CSS/Framer-Motion
und für Compose unbrauchbar; compose-spezifisch existiert nur
`android/skills@jetpack-compose-m3` (310 Installs, M3-Layout statt Animation).
Es bleibt beim vorhandenen `compose-animations`-Skill.

Konkrete Motion-Entscheidungen:

- **Tab-Wechsel:** `NavHost` mit `fadeIn`/`fadeOut` (180 ms) plus 4 % Scale-In.
  Kein horizontales Sliden — die drei Ziele sind gleichwertig, eine Richtung würde
  eine Hierarchie behaupten, die es nicht gibt.
- **River Spirit:** `rememberInfiniteTransition` mit zwei entkoppelten
  `animateFloat`-Werten (vertikaler Drift ±10 dp / 7 s, horizontaler Drift ±14 dp /
  11 s, beide `FastOutSlowInEasing`, `RepeatMode.Reverse`) — teilerfremde Dauern,
  damit die Bewegung nicht sichtbar taktet. Gelesen in `Modifier.offset { }`, also
  im Layout-Block statt in der Komposition.
- **Kartenmarker:** Positionswechsel über `animateValueAsState` auf `LatLng`
  (eigener `TwoWayConverter`), `tween(1200 ms, LinearEasing)` — linear, weil eine
  Fahrt gleichmäßig ist und Easing hier Beschleunigung vortäuschen würde.
- **Einstellungsbereich:** kein eigener Übergang; als Vollbild-Route mit dem
  Standard-Slide von `navigation-compose`.

## Teilaufgabe 3: Standort-Backend

### Supabase-Anbindung

`supabase-kt` (io.github.jan-tennert.supabase) Module `postgrest-kt`, `realtime-kt`,
`auth-kt` + Ktor-Android-Engine. Konfiguration ausschließlich über
`local.properties` → `BuildConfig`:

```
selli.supabaseUrl=https://<ref>.supabase.co
selli.supabaseAnonKey=<anon key>
```

Fehlen die Werte, ist das Standort-Feature inaktiv und der Tab zeigt einen
Hinweiszustand — genau wie der Places-Key heute (leere Vorschlagsliste statt Absturz).

### Kopplung an das bestehende Google-Sign-in

Die bestehende Implementierung bleibt unverändert bestehen und wird nur **ergänzt**:
`GoogleCalendarDataRepository.signIn()` legt das rohe Google-ID-Token
(`GoogleIdTokenCredential.idToken`) zusätzlich ab. Damit macht eine neue,
eigenständige Klasse `supabase.auth.signInWith(IDToken) { provider = Google }`.

Warum das trägt: Supabase stellt danach eine eigene Session mit Refresh-Token aus
und erneuert sie selbst. Das ID-Token wird also genau einmal gebraucht (Ablauf nach
1 h ist irrelevant), und der Google-Sign-in-Pfad muss nichts von Supabase wissen.

Im Supabase-Dashboard muss der Google-Provider aktiv sein und
`selli.googleServerClientId` unter den erlaubten Client-IDs stehen. Ein
Provider-Secret ist für ID-Token-Sign-in **nicht** nötig.

### Schema

Eine Zeile pro Person, per Upsert überschrieben. **Keine Verlaufstabelle** — „Bewegung
läuft mit" entsteht dadurch, dass der Marker zwischen zwei Upserts animiert, nicht
durch eine Spur.

```sql
create table public.locations (
  user_id           uuid        primary key references auth.users(id) on delete cascade,
  person            text        not null check (person in ('BASTI','MELLI')),
  latitude          double precision not null,
  longitude         double precision not null,
  accuracy_meters   double precision,
  speed_mps         double precision,
  is_moving         boolean     not null default false,
  updated_at        timestamptz not null default now()
);
```

### Zugriffsbeschränkung auf genau zwei Konten

Eine Allowlist-Tabelle, von Basti mit den zwei echten E-Mail-Adressen befüllt.
Ohne Eintrag darf ein angemeldetes Konto weder lesen noch schreiben — ein Fremder,
der sich per Google an dem Supabase-Projekt anmeldet, sieht nichts.

```sql
create table public.selli_members (
  email  text primary key,
  person text not null unique check (person in ('BASTI','MELLI'))
);

create or replace function public.is_selli_member() returns boolean
  language sql stable security definer set search_path = public as $$
    select exists (select 1 from public.selli_members m
                   where lower(m.email) = lower(auth.jwt() ->> 'email'))
  $$;
```

Policies auf `public.locations`: `select` für `is_selli_member()`;
`insert`/`update` zusätzlich mit `user_id = auth.uid()`. Kein `delete`.
`selli_members` selbst bekommt RLS ohne jede Policy — nur `is_selli_member()`
(security definer) liest sie, kein Client.

### Echtzeit

Supabase Realtime, `postgres_changes` auf `public.locations` (Publication
`supabase_realtime`). Der Client abonniert nur `UPDATE`/`INSERT` der Partnerzeile.
Fällt die Verbindung weg, greift ein Polling-Fallback alle 60 s.

## Teilaufgabe 4: Tracking & Karte

### Karte

Google Maps (`com.google.maps.android:maps-compose` 6.x + `play-services-maps`),
Entscheidung von Basti am 26.08.2026. Grund: das Projekt hat schon einen
Google-Cloud-Key (Places) und eine Maps-Intent-Anbindung; MapLibre hätte eine
zusätzliche fremde Tile-Quelle gebraucht.

Key über `local.properties` → `selli.mapsApiKey` → `manifestPlaceholders`.
Kartenstil warm eingekleidet über `res/raw/map_style_selli.json` (+ dunkle Variante
`map_style_selli_night.json`): gedämpfte Grüntöne für Flächen, creme statt weiß für
Straßen, entsättigtes Wasser, POI-Beschriftungen aus.

Marker: `location_pin_basti.webp` / `location_pin_melli.webp`, auf 64 dp skaliert,
`anchor = (0.5, 0.92)` (Pin-Spitze, nicht Bildmitte). Unter jedem Marker eine
Frische-Angabe („vor 2 Min."). **Kein Ein/Aus-Schalter, keine Distanzanzeige** —
laut Handover ausdrücklich nicht gewünscht.

### Tracking-Taktung

Foreground-Service mit `foregroundServiceType="location"` und stiller Notification
(Kanal-Importance `LOW`). Ab Android 14 ist das für verlässliche
Hintergrund-Standortdaten unvermeidbar; ohne Service drosselt Android auf wenige
Fixes pro Stunde, dann springt die Position statt mitzulaufen.

Adaptive Taktung nach dem Vorbild von Googles Standortfreigabe (Entscheidung mit
Basti am 26.08.2026 abgestimmt, Ziel „Akku möglichst schonen"):

| Zustand | Priority | Intervall | Distanzfilter | Bündelung | Schätzung |
| --- | --- | --- | --- | --- | --- |
| Stillstand | `BALANCED_POWER_ACCURACY` (WLAN/Funkzelle, kein GPS) | 5 min | 150 m | 15 min | ~0,5 %/h |
| Bewegung | `HIGH_ACCURACY` | 30 s | 50 m | 60 s | ~2–4 %/h |

Übergang Stillstand → Bewegung: ein Fix mit `speed > 2,5 m/s` **oder** > 200 m vom
letzten hochgeladenen Punkt. Zurück nach 5 min ohne qualifizierende Bewegung.
Der Distanzfilter sorgt dafür, dass im Stillstand faktisch keine Uploads laufen.

### Berechtigungen

`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`,
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`.

Android verlangt Hintergrund-Standort in einem **zweiten, separaten** Dialog nach
dem Vordergrund-Dialog. Der Standort-Tab führt deshalb durch zwei Stufen mit einem
freundlich formulierten Zwischenschritt („Damit Selli auch mitläuft, wenn das Handy
in der Tasche steckt") statt beide Dialoge hintereinander zu feuern. Verweigerte
Berechtigung → Hinweiszustand mit Knopf in die Systemeinstellungen, kein Absturz,
Partnerposition wird trotzdem angezeigt.

Da Selli nicht über den Play Store läuft, entfällt der Genehmigungsprozess für
Hintergrund-Standort — der Systemdialog bleibt.

## Was bewusst nicht gebaut wird

- Kein Ein/Aus-Schalter für die Standortfreigabe, keine Distanzanzeige (Handover).
- Keine Standort-Historie / kein Spurverlauf.
- Keine Änderung an Kalenderlogik, Datenmodellen, Google-/ICS-Anbindung außer dem
  additiven ID-Token-Zugriff und dem neuen `upcomingWirZeitEvents`-Feld.
- Kein Wechsel der bestehenden Google-Anmeldung auf Supabase Auth.
