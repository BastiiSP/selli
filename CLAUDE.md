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

**Maskottchen:** Eigenständiges, an Ghiblis Rußmännchen-Ästhetik *angelehntes* (nicht kopiertes) kleines Wesen – rundlich, dunkel, große Kulleraugen, tapsig-niedlich. Finale Illustrationen (nicht mehr Platzhalter) liegen als WebP unter `res/drawable-nodpi/`: `mascot_idle`, `mascot_loading`, `mascot_empty_state`, `mascot_celebrating`, `mascot_traveling` (für v3 reserviert), `ic_launcher_foreground_art` (App-Icon-Vordergrund). Einsatzorte:
- App-Icon (`ic_launcher_foreground_art`, vor dem Lila-Grün-Gradient aus `ic_launcher_background.xml`) – **bekannte Einschränkung:** füllt die Fläche recht vollständig, bei manchen Launcher-Masken (Kreis/Squircle) könnten Ränder leicht abgeschnitten werden; Polish-Punkt, kein Blocker
- Ladeanimation beim Kalender-Sync ("sammelt" Termine ein, `mascot_loading`)
- Leerzustand ("keine Termine heute", `mascot_empty_state`)
- Distanz-Feature (symbolisch "unterwegs" zwischen Basti und Melli, `mascot_traveling`, v3)
- Reagierendes Element im Kalender-Header: nutzt `CalendarMergeService.isBothFree(day)` (Owner Codex) – zeigt `mascot_celebrating`, wenn an einem betrachteten Tag beide frei sind. **Bekannte Einschränkung:** `isBothFree` wertet aktuell jeden einzelnen Termin als „nicht frei", ist also sehr streng – trifft daher seltener zu als eigentlich gewünscht.

**Avatare:** Illustrierte Profildarstellungen von Basti und Melli im selben gezeichneten Stil wie das Maskottchen (kein Foto) – **umgesetzt**, liegen als `avatar_basti.webp` / `avatar_melli.webp` unter `res/drawable-nodpi/`, mit Personenfarben-Ring in der UI kombiniert.

**Hauptansicht (Kalender):** Struktur und Bedienlogik wie im MS365-Outlook-Kalender (Grid-Ansicht mit Tagesdetail darunter) – bewusst übernommen, weil bewährt und beiden vertraut. Visuell "warm eingekleidet": abgerundete Zellen/Tageskarten statt eckiger Kästen, weiche Schatten statt harter Linien, Personenfarben als Pills, durchgängig die rundliche Schrift. Das reagierende Maskottchen sitzt im Header dieser Ansicht.

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

## Bekannter Stand (Stand 20.07.2026)

MVP-Kern ist fertig: Google-Kalender-Anbindung inkl. automatischer ACL-Freigabe, ICS-Arbeitskalender mit eigenem RFC-5545-Parser, Zusammenführungslogik inkl. `isBothFree`, komplette Kalender-UI, Homescreen-Widget, finale Illustrationen (inkl. Avataren mit echter Ähnlichkeit) eingebunden. Consent-Flow abgefangen, erster Verbinden-Crash (Google-Konto-Auswahl) und Dark-Mode-Textkontrast behoben. Kalenderquellen sind fehler-isoliert: eigener Google-Kalender, Partner-Google-Kalender und ICS-Feed scheitern unabhängig; Fetch-Fehler werden über `CalendarLogger` (`domain/logging`, Android-Implementierung `AndroidCalendarLogger`, Log-Tag `SelliCalendar`) geloggt statt still geschluckt.

Alltagstauglichkeits-Block umgesetzt (20.07.2026):
- **Sitzung merken:** `SessionRepository` (`sessionState()`/`resetSession()`, implementiert vom `GoogleCalendarDataRepository` über die ohnehin persistierten Konten) — App-Start springt bei vollständiger Verknüpfung direkt in den Kalender, bei halber Verknüpfung zum Partner-Schritt; „Konto wechseln …" im Header-Menü setzt nur Sellis Verknüpfung zurück.
- **Lokale Ausblendungen/Anpassungen:** `EventCustomizationRepository` (JSON-Datei-Store `FileEventCustomizationRepository` unter `data/customization/`, Gson) + Anwendung im `DefaultCalendarMergeService`. Targets: einzelnes Vorkommen oder Serie ab Vorkommen (`CustomizationTarget.SeriesFrom`, Occurrence schlägt Series). Der echte Google-Kalender wird nie verändert. UI: Termin antippen → Aktionen-Sheet, Verwaltung über „Ausgeblendet & angepasst" im Header-Menü. `CalendarEvent` hat dafür `seriesId` (Google: `recurringEventId`, ICS: UID) und `isCustomized`.
- **Serien anlegen:** `NewCalendarEvent.recurrence` (`EventRecurrence`: DAILY/WEEKLY/MONTHLY/YEARLY + optionales inklusives `until`) → `GoogleRruleFormatter` erzeugt die RRULE, Google verwaltet die Serie nativ; Fetch expandiert via `singleEvents=true`.

67 Unit-Tests grün. Bekannte offene Punkte:
- Neuer Block (Session-Restore, Overrides, Serien) noch nicht end-to-end am echten Gerät durchgetestet
- `isBothFree` verfeinert (≥3h zusammenhängender freier Block, 9–22 Uhr), aber noch nicht end-to-end am echten Gerät durchgetestet
- MONTHLY/YEARLY-RRULEs aus dem ICS-Feed erscheinen bewusst nur als Einzeltermin am Startdatum (dokumentierte Einschränkung, kein Bug)
- App-Icon-Vordergrund evtl. zu randvoll für manche Launcher-Masken (siehe Designkonzept oben)
- Merkregel: Text/Icons auf `selliGradient()`- oder `personColor()`-Flächen nie hart `Color.White` geben, sondern `onAccentColor()` verwenden (Dark-Mode-Kontrast)

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
