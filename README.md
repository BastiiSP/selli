# Selli

Native Android-App, die die Kalender von Basti und Melli (Fernbeziehung) zusammenführt, damit beide auf einen Blick sehen, was der/die andere vorhat.

- **Stack:** Kotlin + Jetpack Compose, Widget (v2) via Jetpack Glance
- **Kalenderquellen:** Google Calendar API (beide privaten Kalender, automatische gegenseitige ACL-Freigabe), veröffentlichter ICS-Feed für Bastis MS365-Arbeitskalender (read-only)
- **Kein eigenes Backend** — die App spricht direkt mit den externen Diensten

Vollständiges Projekt- und Designkonzept liegt in Bastis privatem Obsidian-Vault (`02 Projekte/Selli.md`) — dort auch die Quelle der Wahrheit für offene Punkte und Entwicklungshistorie.

## Setup

1. Projekt in Android Studio öffnen (Gradle-Sync läuft beim ersten Öffnen automatisch)
2. `local.properties` lokal um die benötigten, nicht versionierten Werte ergänzen (Google-OAuth-Client-Konfiguration, ICS-Feed-URLs, Places-API-Schlüssel)
3. Build & Run über Android Studio oder `./gradlew assembleDebug`

### Lokale Konfiguration

Diese Werte gehören lokal in `local.properties` und werden als `BuildConfig`-Felder bereitgestellt. Keine dieser Angaben wird versioniert:

```properties
selli.googleServerClientId=...
selli.icsFeedUrl=...
selli.melliIcsFeedUrl=...
selli.placesApiKey=...
```

#### Places-API-Schlüssel

`selli.placesApiKey` speist die Adressvorschläge im Ortsfeld. Der Schlüssel braucht in der
Google Cloud Console die **Places API (New)** und ist auf Android-Apps beschränkt. In der
Schlüssel-Restriktion müssen Paketname und Signatur-Fingerabdruck eingetragen sein:

- Paketname: `com.prehmus.selli`
- SHA-1 des verwendeten Keystores — für Debug-Builds:
  `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`

Ein Release-Build mit anderem Keystore braucht einen **zusätzlichen** Eintrag in derselben
Restriktion, sonst antwortet die API mit `403 PERMISSION_DENIED`. Die App liest Paketname und
Fingerabdruck zur Laufzeit aus der eigenen Signatur (`data/places/AndroidAppIdentity.kt`) und
schickt sie als `X-Android-Package`/`X-Android-Cert` mit — hartkodiert ist nichts.

Fehlt der Schlüssel oder ist er ungültig, bleibt das Ortsfeld ein normales Textfeld ohne
Vorschläge; Termine lassen sich weiterhin uneingeschränkt speichern.

## Konventionen

Siehe [`AGENTS.md`](./AGENTS.md) für Owner-Aufteilung (Claude ↔ Codex), Package-Struktur (`data/`, `domain/`, `ui/`) und Coding-Konventionen.
