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
- Personenfarben: **Lila** (Melli) und **Grün** (Basti) – jeder Termin trägt die Farbe seiner Person als runde Pill/Badge statt eckigem Balken. Aktuelle Platzhalterwerte in `ui/theme/Color.kt`: `MelliPurple = #8E6BBF`, `BastiGreen = #4C9A6A` (Light), `MelliPurpleDark = #B49BD9`, `BastiGreenDark = #7FC79A` (Dark) – bei Bedarf für Kontrast/Barrierefreiheit feinjustieren, Grundcharakter beibehalten.
- Gemeinsam erstellte Termine bekommen einen sanften Lila-Grün-Verlauf als eigene dritte Kennung
- Lila-Grün-Gradient zusätzlich als App-weites Branding-Element (Header, App-Icon-Hintergrund, Splash Screen)
- Dark Mode folgt automatisch dem System-Theme (kein manueller Umschalter, siehe `SelliTheme` in `ui/theme/Theme.kt`); beide Farben bekommen abgestimmte, entsättigte/gedimmte Dark-Mode-Varianten statt einfacher Invertierung

**Typografie:** Rundliche, freundliche Schriftart (Kandidaten: Nunito oder Quicksand) für Überschriften/UI-Elemente, kombiniert mit gut lesbarer Systemschrift für längere Textinhalte. Durchgängig weiche, keine scharfen/eckigen Schriftformen. Aktuell in `ui/theme/Type.kt` nur `FontFamily.Default` als Platzhalter hinterlegt – beim Umsetzen auf die rundliche Schrift wechseln.

**Maskottchen:** Eigenständiges, an Ghiblis Rußmännchen-Ästhetik *angelehntes* (nicht kopiertes) kleines Wesen – rundlich, dunkel, große Kulleraugen, tapsig-niedlich. Einsatzorte:
- App-Icon (stilisiert, vor Lila-Grün-Gradient) – aktuell nur ein einfacher Kreis-Platzhalter in `res/drawable/ic_launcher_foreground.xml`
- Ladeanimation beim Kalender-Sync ("sammelt" Termine ein)
- Leerzustand ("keine Termine heute")
- Distanz-Feature (symbolisch "unterwegs" zwischen Basti und Melli, v3)
- Reagierendes Element im Kalender-Header: erkennt anhand von `CalendarMergeService.isBothFree(day)` (Owner Codex), wenn an einem betrachteten Tag beide frei sind, und zeigt sich sichtbar erfreut – Brücke zum späteren v3-Feature "Vorschläge für gemeinsame Slots"

**Avatare:** Illustrierte Profildarstellungen von Basti und Melli im selben gezeichneten Stil wie das Maskottchen (kein Foto) – konsistente eigene Bildsprache über die ganze App.

**Hauptansicht (Kalender):** Struktur und Bedienlogik wie im MS365-Outlook-Kalender (Grid-Ansicht mit Tagesdetail darunter) – bewusst übernommen, weil bewährt und beiden vertraut. Visuell "warm eingekleidet": abgerundete Zellen/Tageskarten statt eckiger Kästen, weiche Schatten statt harter Linien, Personenfarben als Pills, durchgängig die rundliche Schrift. Das reagierende Maskottchen sitzt im Header dieser Ansicht.

**Asset-Produktion:** Alle illustrierten Assets (Maskottchen-Posen, Avatare, App-Icon) werden über OpenAI-Bildgenerierung **einmalig während der Entwicklung** erzeugt und als statische Bilddateien ins Projekt gepackt (z. B. unter `res/drawable/`). Kein API-Key, keine Laufzeitkosten, keine Internetabhängigkeit für dieses Feature selbst.

## Coding-Konventionen

- Compose-Best-Practices: State-Hoisting, unidirektionaler Datenfluss, keine Business-Logik in Composables (die liegt bei Codex in `domain`/`data`)
- Klare Modul-/Package-Trennung:
  - `data/` – API-Clients, Repositories (Codex-Territorium)
  - `domain/` – Merge-Logik, Freizeit-Abgleich, sonstige Geschäftslogik (Codex-Territorium)
  - `ui/` – Compose-Screens, Theme, Components (Claude-Territorium)
- **Keine Secrets im Code.** Der ICS-Link (enthält Zugriffstoken) und alle API-Keys/Client-IDs gehören ausschließlich in lokale, nicht versionierte Config (`local.properties`), nie committen.

## Build & Test

- `./gradlew assembleDebug` – Debug-Build (bereits einmal erfolgreich verifiziert beim Setup)
- `./gradlew testDebugUnitTest` – Unit-Tests, insbesondere für von Codex gelieferte Logik-Module
