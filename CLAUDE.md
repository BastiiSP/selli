# CLAUDE.md – Selli

Projektkontext für Claude Code in diesem Repository. Pendant zu `AGENTS.md` (dort die Konventionen für Codex).

## Projektkontext

Selli ist eine native Android-Kalender-App für Basti und seine Freundin Melanie ("Melli") Spies. Beide leben ca. zwei Autostunden voneinander entfernt (Fernbeziehung) und haben aktuell keinen gemeinsamen Überblick über ihre Termine. Selli führt die relevanten Kalender beider zusammen, ohne bestehende Kalender-Apps zu ersetzen.

- **Stack:** Kotlin + Jetpack Compose, Widget (v2) via Jetpack Glance
- **Kein eigenes Backend** – die App spricht direkt mit externen Diensten:
  - Google Calendar API für Bastis und Mellis privaten Kalender (inkl. automatischem ACL-Insert-Call für gegenseitige "Details anzeigen"-Freigabe)
  - Ein veröffentlichter ICS-/Webcal-Feed für Bastis MS365-Arbeitskalender (read-only, periodisch abgerufen und geparst)
- Push/Realtime-Sync ist bewusst auf später verschoben – MVP arbeitet mit Refresh beim App-Öffnen

## Owner-Regeln (Claude ↔ Codex)

Jede Datei hat **genau einen Owner** – niemals eine Datei zwischen Claude und Codex aufteilen.

- **Claude (dieses Projekt):** Jetpack Compose UI, Layout, visuelle Umsetzung des Designkonzepts (siehe unten), Glance-Widget-UI, Verdrahtung von UI zu der von Codex bereitgestellten Logik
- **Codex:** reine Logik-Module, Datenmodelle, API-Clients (Google Calendar API, ICS-Parsing), Merge-/Sync-Logik zwischen den Kalenderquellen, Freizeit-Abgleich-Logik, Tests, Gradle-/Build-Konfiguration, README. Tasks mit Owner Codex werden über den `codex:rescue`-Skill delegiert.
- Faustregel: Was das Ergebnis **berechnet** (z. B. ob beide frei sind), ist Codex. Wie das Ergebnis **dargestellt** wird (z. B. das Maskottchen freut sich sichtbar), ist Claude.
- Vor Änderungen: LSP-Tool nutzen, um die bestehende Codestruktur zu verstehen.

## Designkonzept (Quelle der Wahrheit für UI-Arbeit)

*Vollständig erarbeitet und von Basti final bestätigt am 18.07.2026. Das ausführliche Original liegt in Bastis privatem Obsidian-Vault (`02 Projekte/Selli.md`) – dieser Abschnitt ist die für Claude Code direkt nutzbare Kopie, damit kein Vault-Zugriff nötig ist.*

**Grundstimmung:** Warm & verspielt, persönlich statt generisch. Selli soll sich wie eine kleine gemeinsame Welt anfühlen statt wie ein Business-Tool – auch wenn die Bedienlogik bewusst vom bewährten Outlook-Grid übernommen wird.

**Farbsystem:**
- Personenfarben: **Lila** (Melli) und **Grün** (Basti) – jeder Termin trägt die Farbe seiner Person als runde Pill/Badge statt eckigem Balken. Finale Werte in `ui/theme/Color.kt`: `MelliPurple = #7E58B4`, `BastiGreen = #3E8A5C` (Light, gegenüber dem ersten Platzhalter leicht abgedunkelt für 4,5:1-Kontrast bei weißem Pill-Text), `MelliPurpleDark = #B49BD9`, `BastiGreenDark = #7FC79A` (Dark), dazu sanfte `*Soft`-Containertöne für Termin-Karten.
- Gemeinsam erstellte Termine bekommen einen sanften Lila-Grün-Verlauf als eigene dritte Kennung (`selliGradient()` in `Color.kt`)
- Lila-Grün-Gradient zusätzlich als App-weites Branding-Element (Header, App-Icon-Hintergrund, Splash Screen)
- Dark Mode folgt automatisch dem System-Theme (kein manueller Umschalter, siehe `SelliTheme` in `ui/theme/Theme.kt`); beide Farben bekommen abgestimmte, entsättigte/gedimmte Dark-Mode-Varianten statt einfacher Invertierung

**Typografie:** Rundliche, freundliche Schrift – **umgesetzt**: Nunito (Regular/SemiBold/Bold/ExtraBold), statisch gebündelt unter `res/font/`, keine Netz-/Play-Services-Abhängigkeit. Läuft in Überschriften/Titeln/UI-Elementen (`ui/theme/Type.kt`); Fließtext bleibt bewusst Systemschrift für gute Lesbarkeit bei längeren Inhalten.

**Maskottchen:** Eigenständiges, an Ghiblis Rußmännchen-Ästhetik *angelehntes* (nicht kopiertes) kleines Wesen – rundlich, dunkel, große Kulleraugen, tapsig-niedlich. Finale Illustrationen (nicht mehr Platzhalter) liegen als WebP unter `res/drawable-nodpi/`: `mascot_idle`, `mascot_loading`, `mascot_empty_state`, `mascot_celebrating`, `mascot_pondering`, `mascot_traveling`, `ic_launcher_foreground_art` (App-Icon-Vordergrund). Einsatzorte:
- App-Icon (`ic_launcher_foreground_art`, vor dem Lila-Grün-Gradient aus `ic_launcher_background.xml`) – **bekannte Einschränkung:** füllt die Fläche recht vollständig, bei manchen Launcher-Masken (Kreis/Squircle) könnten Ränder leicht abgeschnitten werden; Polish-Punkt, kein Blocker
- Ladeanimation beim Kalender-Sync ("sammelt" Termine ein, `mascot_loading`)
- Leerzustand ("keine Termine heute", `mascot_empty_state`)
- **Wir-Zeit-Countdown auf dem Homescreen-Tab** (seit 26.08.2026 dort; vorher im Kalender-Header, davor ein reagierendes Mood-Icon): animierte Wanderweg-Szene auf einer Lila-Grün-Fläche. `mascot_traveling` läuft entlang eines Pfads auf ein Ziel-Symbol zu (Fortschritt = Zeit seit Termin-Erstellung bis Termin-Start), `mascot_celebrating` am Tag der Wir-Zeit, `mascot_pondering` wenn keine geplant ist. Logik in `domain/countdown/WirZeitCountdown.kt` (Codex), Szene in `ui/home/WirZeitJourneyScene.kt` (Claude). **Wichtig:** die Szene zeichnet durchgehend mit `onAccentColor()` und gehört damit zwingend auf eine `selliGradient()`-/`personColor()`-Fläche – auf warmem Hintergrund ist sie unlesbar. Spec/Plan: `docs/superpowers/specs/2026-08-05-wir-zeit-countdown-design.md` / `docs/superpowers/plans/2026-08-07-wir-zeit-countdown.md`.
- **River Spirit** (`spirit_river.webp`) – eigenständige, an Ghibli angelehnte Begleitfigur des Homescreens (kein Maskottchen-Ersatz): driftet langsam über der Wanderweg-Szene, `ui/home/RiverSpirit.kt`.

**Avatare:** Illustrierte Profildarstellungen von Basti und Melli im selben gezeichneten Stil wie das Maskottchen (kein Foto) – **umgesetzt**, liegen als `avatar_basti.webp` / `avatar_melli.webp` unter `res/drawable-nodpi/`, mit Personenfarben-Ring in der UI kombiniert.

**App-Grundgerüst (seit 26.08.2026):** Ein schmaler, auf jedem Bildschirm sichtbarer Header (`ui/shell/SelliTopBar.kt`: Lila-Grün-Verlauf, Wortmarke, eigener Avatar als Zugang zum Profilbereich) und eine schmale Bottom-Navigation mit drei gleichwertigen Zielen (`ui/shell/SelliBottomBar.kt`): **Kalender | Wir | Standort**. „Wir" sitzt in der Mitte und ist das Startziel. Das Gerüst liegt in `ui/shell/` (`SelliShell.kt` = Header + `NavHost` + Bottom-Navigation, `SelliDestination.kt` = Routen).

**Kalender-Tab:** Struktur und Bedienlogik wie im MS365-Outlook-Kalender (Grid-Ansicht mit Tagesdetail darunter) – bewusst übernommen, weil bewährt und beiden vertraut. Visuell "warm eingekleidet": abgerundete Zellen/Tageskarten statt eckiger Kästen, weiche Schatten statt harter Linien, Personenfarben als Pills, durchgängig die rundliche Schrift. Zeitraumtitel, Blätter-Pfeile und manuelles Aktualisieren sitzen in `ui/calendar/CalendarPeriodBar.kt`. Das frühere separate, stimmungsreaktive Mood-Icon (`CalendarMergeService.isBothFree(day)`) wurde entfernt – `isBothFree` existiert im Code nur noch für Tests, hat keinen UI-Aufrufer mehr.

**Wir-Tab (Homescreen, `ui/home/HomeScreen.kt`):** die Wanderweg-Szene als große Gradient-Hero-Karte mit dem River Spirit, darunter „Nächste gemeinsame Termine" (max. 5, aus `CalendarUiState.upcomingWirZeitEvents`). Leerzustand mit `mascot_pondering`.

**Profil & Einstellungen (`ui/settings/SettingsScreen.kt`):** Vollbild-Route über dem Gerüst, erreichbar über den Avatar im Header. Enthält Profilkarte, verknüpftes Google-Konto (eigenes + Partner), „Ausgeblendet & angepasst" und „Konto wechseln" – die letzten beiden saßen früher im Überlaufmenü des Kalender-Headers, das mit `MascotHeader.kt` entfallen ist.

**Asset-Produktion:** Alle illustrierten Assets (Maskottchen-Posen, Avatare, App-Icon) werden über OpenAI-Bildgenerierung **einmalig während der Entwicklung** erzeugt und als statische Bilddateien ins Projekt gepackt (z. B. unter `res/drawable/`). Kein API-Key, keine Laufzeitkosten, keine Internetabhängigkeit für dieses Feature selbst.

## Coding-Konventionen

- Compose-Best-Practices: State-Hoisting, unidirektionaler Datenfluss, keine Business-Logik in Composables (die liegt bei Codex in `domain`/`data`)
- Klare Modul-/Package-Trennung:
  - `data/` – API-Clients, Repositories (Codex-Territorium)
  - `domain/` – Merge-Logik, Freizeit-Abgleich, sonstige Geschäftslogik (Codex-Territorium)
  - `ui/` – Compose-Screens, Theme, Components (Claude-Territorium)
- **Keine Secrets im Code.** Der ICS-Link (enthält Zugriffstoken) und die Google-OAuth-Client-ID gehören ausschließlich in lokale, nicht versionierte `local.properties` (`selli.googleServerClientId`, `selli.icsFeedUrl`) und werden als `BuildConfig`-Felder bereitgestellt – siehe README. Nie committen.

## Build & Test

- `./gradlew assembleDebug` – Debug-Build
- `./gradlew testDebugUnitTest` – Unit-Tests, insbesondere für von Codex gelieferte Logik-Module
- **Nach jeder abgeschlossenen Aufgabe** (Build + Tests grün): Ergebnis von `app/build/outputs/apk/debug/app-debug.apk` nach `selli.apk` im Projekt-Root kopieren (`cp app/build/outputs/apk/debug/app-debug.apk selli.apk`) und die alte Version dabei überschreiben. Das ist der feste, einzige Ablageort für die Weitergabe an Basti/Melli (z. B. per WhatsApp) – kein Desktop, keine weiteren Kopien an anderer Stelle. `selli.apk` ist über `.gitignore` (`*.apk`) ausgeschlossen, landet also nie im Repo.

## Bekannter Stand (Stand 26.08.2026)

MVP-Kern ist fertig und seitdem in vielen Runden gewachsen: Google-Kalender-Anbindung inkl. automatischer ACL-Freigabe (für beide Personen, inkl. Mellis eigenem zweiten Arbeitskalender-Feed), ICS-Arbeitskalender mit eigenem RFC-5545-Parser, Zusammenführungslogik, komplette Kalender-UI (Monat/Woche/Tag mit Wisch-Navigation), Homescreen-Widget in drei Größenstufen, finale Illustrationen (Maskottchen-Posen, Avatare mit echter Ähnlichkeit) eingebunden. Kalenderquellen sind fehler-isoliert (eigener Google-Kalender, Partner-Google-Kalender, beide ICS-Feeds scheitern unabhängig, sichtbare Fehler-Banner statt stillem Verschlucken).

**Seitdem ergänzt** (Details/Historie: Bastis Obsidian-Vault `02 Projekte/Selli.md`, technische Specs/Pläne unter `docs/superpowers/`):
- **Sitzung merken, lokale Ausblendungen/Anpassungen, Serien anlegen** (ursprünglicher "Alltagstauglichkeits-Block") — Termin antippen → Aktionen-Sheet, Verwaltung über „Ausgeblendet & angepasst" (seit 26.08.2026 im Profil-/Einstellungsbereich, vorher im Header-Menü).
- **Termin-Kategorien** (Arbeit/Privat/Wir-Zeit) + Freie-Zeit-Anzeige, später durch den Wir-Zeit-Countdown ersetzt (siehe Maskottchen-Abschnitt).
- **Wir-Zeit synchronisiert automatisch mit Google:** Kategorie-Wechsel zu/von "Wir-Zeit" setzt/entfernt den Partner als Google-Teilnehmer.
- **Widget-Größenvarianten** (klein/mittel/groß, `SizeMode.Exact`), **Wir-Zeit-Benachrichtigungen** und **Wir-Zeit-Verlauf-Gruppierung** in der Terminliste.
- **Fünf Anpassungswünsche:** Header/Kalender/Liste-Größenanpassung, Zeitstrahl-Farbe nach Person, Pull-to-Refresh, Feed-Caching/Retry gegen HTTP-429-Sperren, **eigene Termine löschen** (echte Google-Löschung, Vorkommen/Serie).
- **Mehrtägige Termine** (ganztägig und getimt) inkl. frei/beschäftigt-Entscheidung, **Google-Maps-Ortsanbindung**.
- **Wir-Zeit-Countdown:** ersetzt die Freie-Zeit-Anzeige im Header und die "nächster freier Slot"-Widget-Zeile — siehe Maskottchen-Abschnitt oben.
- **Termin entfernen (vereinheitlicht):** Aktionen-Sheet bietet jetzt für jeden Termin eine Entfernen-Option — eigener Termin → echtes Löschen (unverändert), eigene Wir-Zeit → echtes Löschen (storniert automatisch beim Partner), **Wir-Zeit der Partnerin/des Partners → neue Lösch-Anfrage** (Markierung `selli:deleteRequestedBy` in `extendedProperties.shared` + Benachrichtigung über das bestehende Wir-Zeit-System, kein neues Backend), alles andere → weiterhin nur lokales Ausblenden. „Ausgeblendet & angepasst" hat dafür ein Mülleimer-Icon pro Zeile bekommen. Spec/Plan: `docs/superpowers/specs/2026-08-07-termin-entfernen-design.md` / `docs/superpowers/plans/2026-08-07-termin-entfernen.md`.
- **App-Grundgerüst statt Einzelbildschirm (26.08.2026):** `navigation-compose`, globaler Header, Bottom-Navigation mit drei Zielen, eigener Homescreen, neuer Profil-/Einstellungsbereich. `ui/calendar/MascotHeader.kt` (754 Zeilen, trug fünf Dinge gleichzeitig) ist dabei aufgelöst worden – die Wanderweg-Szene ist nach `ui/home/`, Wortmarke/Avatare in den globalen Header, Zeitraumnavigation in `CalendarPeriodBar`, die Menüeinträge in die Einstellungen gewandert. `LayoutPreferences.headerCollapsed` hat seitdem keinen Leser mehr (bleibt erhalten, damit gespeicherte Gerätewerte nicht ins Leere laufen). Spec/Plan: `docs/superpowers/specs/2026-08-26-grundgeruest-standort-design.md` / `docs/superpowers/plans/2026-08-26-app-grundgeruest.md`.
- **Fix: `GOOGLE_OWN`-Fehltagging bei Einladungen.** `fetchEvents()` markierte bislang jeden Termin aus dem eigenen Kalender-Fetch als `GOOGLE_OWN`, auch wenn man nur eingeladen (nicht Organisator) war — führte zu doppelt angezeigten Partner-Wir-Zeit-Terminen und einem irreführenden "Endgültig löschen"-Button (löscht bei einem fremden Termin nach Google-Semantik nur die eigene Teilnahme, nicht den ganzen Termin). Behoben über `Event.isOrganizedByPartner(partnerEmail)`, das erstmals das Google-Feld `organizer`/`organizer.self` auswertet.

**272 Unit-Tests grün** (Stand 26.08.2026). Bekannte, bewusst in Kauf genommene Einschränkungen (kein Bug):
- MONTHLY/YEARLY-RRULEs aus ICS-Feeds erscheinen nur als Einzeltermin am Startdatum
- ICS-Termine haben kein `created`-Datum → im Wir-Zeit-Countdown-Wanderweg steht das Maskottchen dort dauerhaft am Weganfang
- Sehr alte Google-Termine ganz ohne `organizer`-Feld werden vom Dopplungs-Fix nicht erfasst (kein Fallback über `iCalUID`)
- App-Icon-Vordergrund evtl. zu randvoll für manche Launcher-Masken (siehe Designkonzept oben)
- Merkregel: Text/Icons auf `selliGradient()`- oder `personColor()`-Flächen nie hart `Color.White` geben, sondern `onAccentColor()` verwenden (Dark-Mode-Kontrast)

**Ausstehende Gerätetests** (funktional/unabhängig verifiziert, aber noch nicht am echten Gerät mit beiden echten Konten bestätigt): Wir-Zeit-Countdown-Fortschrittsposition, Termin-entfernen-Lösch-Anfrage inkl. der offenen technischen Kernfrage, ob ein eingeladener Teilnehmer `extendedProperties.shared` auf seiner eigenen Kopie schreiben darf (Fallback ist getestet und greift, falls nicht), Dopplungs-Fix.

## Claudian-Update-Format

Am Ende eines Handover-Prompts (bzw. nach jeder erledigten Aufgabe) dieses Format ausgeben:
```
## Claudian-Update – [Datum]

### Implementiert
- [Was gebaut / geändert wurde]

### Getestet
- [Was getestet wurde und Ergebnis]

### Offen / Nächster Block
- [Was als nächstes kommt]

### Technische Notizen
- [Wichtige Entscheidungen, neue Pakete, IDs etc.]
```
Basti kopiert dieses Update zu Claudian, der die Projektdokumentation im Vault (`02 Projekte/Selli.md`) damit pflegt.
