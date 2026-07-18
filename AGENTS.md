# AGENTS.md – Selli

Codex-Pendant zur `CLAUDE.md` dieses Projekts. Gilt für alle Aufgaben, die Codex in diesem Repository übernimmt.

## Projektkontext

Selli ist eine native Android-Kalender-App für Basti und seine Freundin Melanie ("Melli") Spies. Beide leben ca. zwei Autostunden voneinander entfernt (Fernbeziehung) und haben aktuell keinen gemeinsamen Überblick über ihre Termine. Selli führt die relevanten Kalender beider zusammen, ohne bestehende Kalender-Apps zu ersetzen.

- **Stack:** Kotlin + Jetpack Compose, Widget (v2) via Jetpack Glance
- **Kein eigenes Backend** – die App spricht direkt mit externen Diensten:
  - Google Calendar API für Bastis und Mellis privaten Kalender (inkl. automatischem ACL-Insert-Call für gegenseitige ["Details anzeigen"-Freigabe])
  - Ein veröffentlichter ICS-/Webcal-Feed für Bastis MS365-Arbeitskalender (read-only, periodisch abgerufen und geparst)
- Push/Realtime-Sync ist bewusst auf später verschoben – MVP arbeitet mit Refresh beim App-Öffnen

## Owner-Regeln (Claude ↔ Codex)

Jede Datei hat **genau einen Owner** – niemals eine Datei zwischen Claude und Codex aufteilen.

- **Codex (dieses Projekt):** reine Logik-Module, Datenmodelle, API-Clients (Google Calendar API, ICS-Parsing), Merge-/Sync-Logik zwischen den Kalenderquellen, Freizeit-Abgleich-Logik (z. B. "sind Basti und Melli an einem Tag beide frei"), Tests, Gradle-/Build-Konfiguration, README
- **Claude (bleibt bei Claude Code):** Jetpack Compose UI, Layout, visuelle Umsetzung des Designkonzepts (Farben, Typografie, Maskottchen-/Avatar-Assets), Glance-Widget-UI, Verdrahtung von UI zu der von Codex bereitgestellten Logik
- Faustregel: Was das Ergebnis **berechnet** (z. B. ob beide frei sind), ist Codex. Wie das Ergebnis **dargestellt** wird (z. B. das Maskottchen freut sich sichtbar), ist Claude.

## Coding-Konventionen

- Standard Kotlin-Stil (Android-Kotlin-Style-Guide, ktlint-kompatibel)
- Compose-Best-Practices für UI-nahe Logik, an die Codex zuarbeitet: State-Hoisting, unidirektionaler Datenfluss, keine UI-Frameworks-Importe in `domain`/`data`
- Klare Modul-/Package-Trennung:
  - `data/` – API-Clients, Repositories (Google Calendar, ICS)
  - `domain/` – Merge-Logik, Freizeit-Abgleich, sonstige Geschäftslogik
  - `ui/` – Compose-Screens, Theme, Components (Claude-Territorium)
- **Keine Secrets im Code.** Der ICS-Link (enthält Zugriffstoken) und alle API-Keys/Client-IDs gehören ausschließlich in lokale, nicht versionierte Config (z. B. `local.properties`, mit Eintrag in `.gitignore`). Niemals committen.

## Build & Test

*(Wird ergänzt, sobald das Android-Studio-Grundgerüst steht – konkrete Gradle-Befehle für Build/Test/Lint.)*

## Design-Referenz

Das vollständige Designkonzept (Farben, Typografie, Maskottchen, Avatare, Layout-Prinzipien) liegt in Bastis privatem Obsidian-Vault und ist dort die Quelle der Wahrheit – Codex hat darauf keinen Zugriff. Claude legt die konkreten Design-Tokens (Farbwerte, Theme, Assets) im Projekt ab (z. B. `ui/theme/`), sobald das Setup steht. Codex hält sich beim Implementieren an diese bereits vorhandenen Werte, statt eigene Design-Entscheidungen zu treffen.
