# Selli

Native Android-App, die die Kalender von Basti und Melli (Fernbeziehung) zusammenführt, damit beide auf einen Blick sehen, was der/die andere vorhat.

- **Stack:** Kotlin + Jetpack Compose, Widget (v2) via Jetpack Glance
- **Kalenderquellen:** Google Calendar API (beide privaten Kalender, automatische gegenseitige ACL-Freigabe), veröffentlichter ICS-Feed für Bastis MS365-Arbeitskalender (read-only)
- **Kein eigenes Backend** — die App spricht direkt mit den externen Diensten

Vollständiges Projekt- und Designkonzept liegt in Bastis privatem Obsidian-Vault (`02 Projekte/Selli.md`) — dort auch die Quelle der Wahrheit für offene Punkte und Entwicklungshistorie.

## Setup

1. Projekt in Android Studio öffnen (Gradle-Sync läuft beim ersten Öffnen automatisch)
2. `local.properties` lokal um die benötigten, nicht versionierten Werte ergänzen (Google-OAuth-Client-Konfiguration, ICS-Feed-URL) — Details siehe Handover-Prompt/Vault-Notiz
3. Build & Run über Android Studio oder `./gradlew assembleDebug`

## Konventionen

Siehe [`AGENTS.md`](./AGENTS.md) für Owner-Aufteilung (Claude ↔ Codex), Package-Struktur (`data/`, `domain/`, `ui/`) und Coding-Konventionen.
