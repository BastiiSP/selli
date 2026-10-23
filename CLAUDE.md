# Selli – Projektanweisungen

Native Android-App für zwei verbundene Konten: Kalender zusammenführen, gemeinsame Termine, Standort, Kosten und Ideen. Kotlin und Jetpack Compose; Widget via Jetpack Glance. Google Calendar und lesende ICS-/Webcal-Feeds bleiben Kalenderquellen. Supabase ergänzt Standort, Kosten und Ideen; kein Ersatz für die Kalender-Anbindung. Kalender beim Öffnen aktualisieren; Kalender-Push bleibt ein späteres Vorhaben.

## Zuständigkeiten

Jede Datei hat genau einen Owner; keine Datei zwischen den Werkzeugen aufteilen.
- **Claude Code:** `ui/`, Compose, Layout, Design, Assets, Glance-UI und Verdrahtung der UI zur Logik.
- **Codex:** `data/`, `domain/`, Modelle, API-Clients, ICS-Parsing, Merge-/Sync-/Freizeitlogik, Tests, Gradle, README. Codex-Tasks über `codex:rescue` bzw. eingerichtete Übergabe delegieren; persönlichen Skill `codex-modellwahl` anwenden, Modell/Effort ausdrücklich festlegen und nach Start prüfen.
- Berechnung gehört zu Codex, Darstellung zu Claude. Vor Änderungen vorhandenes LSP-Tool zur Strukturprüfung nutzen; fehlenden Zugriff ausweisen.
- UI verwendet State-Hoisting und unidirektionalen Datenfluss; Business-Logik bleibt in `domain`/`data`, dort keine UI-Framework-Imports. Kotlin-Stil ktlint-kompatibel.
- Vorhandene Design-Tokens und Assets verwenden; Codex trifft keine eigenen Designentscheidungen.

## Verbindliches Design

- Warm, verspielt und persönlich; Outlook-artiges Kalender-Grid mit Tagesdetail, runden Zellen/Karten, weichen Schatten und Personenfarben als Pills.
- Personenfarben: Lila `#7E58B4` und Grün `#3E8A5C` im Light Theme, `#B49BD9` und `#7FC79A` im Dark Theme; Soft-Töne aus `ui/theme/Color.kt`.
- Gemeinsame Termine bekommen `selliGradient()`; Lila-Grün-Verlauf auch für Header, Icon und Splash.
- Dark Mode folgt dem System ohne manuellen Umschalter; abgestimmte entsättigte Varianten statt Invertierung.
- Nunito Regular/SemiBold/Bold/ExtraBold liegt statisch in `res/font/` für Titel/UI; Fließtext bleibt Systemschrift. Keine Netz-/Play-Services-Abhängigkeit der Schriften.
- Eigenständiges rundliches, dunkles Maskottchen mit großen Augen, von Rußmännchen-Ästhetik inspiriert; keine Kopie. Bestehende WebP-Posen in `res/drawable-nodpi/` für Idle, Laden, Leerzustand, Feiern, Nachdenken und Reise verwenden.
- Wir-Zeit-Szene: Reise entlang des Pfads von Erstellung bis Beginn; am Termin feiern, ohne Termin nachdenken. `domain/countdown/WirZeitCountdown.kt` liefert Logik, `ui/home/WirZeitJourneyScene.kt` Darstellung.
- Text/Icons auf `selliGradient()`/`personColor()` immer über `onAccentColor()`; Szene nur auf Akzentfläche, auf warmem Hintergrund unlesbar.
- River Spirit ist eine langsam driftende Begleitfigur des Homescreens, kein Maskottchen-Ersatz. Bestehende gezeichnete Avatare mit Personenfarben-Ring verwenden, keine Fotos.
- Schmaler globaler Verlauf-Header mit Wortmarke/Profil-Avatar und schmale Navigation `Kalender | Ideen | Wir | Kosten | Standort`. `Wir` ist Startziel. Profil/Einstellungen als eigene Vollbildroute über dem Gerüst.
- Kalender-Zeitraumtitel, Blättern und Refresh in `CalendarPeriodBar`. Entfernte Mood-Icon-UI nicht wieder einführen; gespeicherte `LayoutPreferences.headerCollapsed` erhalten.
- Homescreen: Reise-Szene, River Spirit, maximal fünf nächste gemeinsame Termine; Leerzustand mit nachdenkender Pose.
- Profil enthält eigenes/verbundenes Google-Konto, „Ausgeblendet & angepasst“ und „Konto wechseln“.
- Standortkarte: Personenfarben/Avatare, Pin-Anker 0,92, systemabhängige warme Kartenstile ohne POI-/Transit-Labels. Bewegung linear über 1,2 s statt Easing; Frische statt Distanzanzeige. Kein Standort-Ein/Aus-Schalter.
- Illustrationen einmalig während der Entwicklung erzeugen und statisch bündeln; keine Bildgenerierung oder Keys zur Laufzeit.
- App-Icon kann bei Launcher-Masken an Rändern knapp sein; bekannte Einschränkung, kein pauschaler Blocker.

## Daten- und Funktionsgrenzen

- Google-ACL für gegenseitige Detailfreigabe erhalten; Kalenderquellen unabhängig behandeln, Fehler sichtbar anzeigen.
- Wir-Zeit-Kategoriewechsel setzt/entfernt den verbundenen Google-Teilnehmer.
- Entfernen unterscheidet eigenes echtes Löschen, eigene Wir-Zeit mit Storno beim Partner, Partner-Wir-Zeit mit Lösch-Anfrage und übrige lokale Ausblendung. `selli:deleteRequestedBy` in `extendedProperties.shared` nutzt bestehende Benachrichtigungen. Schreibrecht auf eingeladener Kopie nicht unterstellen; Fallback erhalten und am Gerät prüfen.
- Organisator auswerten: Einladungen nicht pauschal als `GOOGLE_OWN` behandeln. Sehr alte Einträge ohne Organisator bleiben eine dokumentierte Einschränkung; kein behaupteter iCalUID-Fallback.
- ICS MONTHLY/YEARLY erscheint bislang nur am Startdatum; fehlendes `created` hält die Reise-Figur am Anfang. Nicht als vollständig gelöst darstellen.
- Standort: Google-Login additiv per ID-Token mit selbst erneuernder Supabase-Sitzung koppeln. Eine Position pro Konto via Upsert, Realtime mit 60-s-Polling-Fallback; Allowlist/RLS begrenzt auf die zwei Konten.
- Tracking adaptiv: Stillstand 5 min/WLAN/150-m-Filter, Bewegung 30 s/GPS/gebündelt; kein Dauer-GPS.
- Vor Datenänderungen Migrationen und vorhandene Specs/Pläne unter `docs/superpowers/` lesen; Standort-Inbetriebnahme unter `docs/runbooks/supabase-scharfstellen.md`.
- Kosten/Ideen erhalten: gemeinsame Ausgaben 50/50, Settlement-Gruppierung, Ordner mit abhakbaren Punkten und Linkvorschau. Vor Änderungen die bestehenden Specs/Pläne und Implementierung prüfen; historische Fehlerlisten nicht als aktuellen Zustand übernehmen. `loadSettlements()`, fehlerweiterreichendes `settle()` und Kosten-/Ideen-Deep-Links sind im Code vorhanden. Gerätetests mit beiden Konten sowie RLS/Grants, Zeitstempel, Web-Parsing, Benachrichtigungen, Reise-Fortschritt und Lösch-Anfrage nur bei tatsächlicher Durchführung als bestätigt ausweisen.

## Geheimnisse, Build und Übergabe

- Öffentliches Repository: keine persönlichen Hintergründe, echten Konten/Termine, privaten Dokumentverweise oder Zugangsdaten aufnehmen.
- Secrets nur in ignorierter `local.properties`, über `BuildConfig` bereitstellen. Keys: `selli.googleServerClientId`, `selli.icsFeedUrl`, `selli.melliIcsFeedUrl`, `selli.placesApiKey`, `selli.supabaseUrl`, `selli.supabaseAnonKey`, `selli.mapsApiKey`. Keine Werte in Code/Anweisungen.
- `./gradlew assembleDebug` und `./gradlew testDebugUnitTest`.
- Nach abgeschlossener Entwicklungsaufgabe mit grünem Build/Tests `app/build/outputs/apk/debug/app-debug.apk` nach `selli.apk` im Root kopieren und alte Fassung dort ersetzen. Einziger Übergabeort, kein Desktop und keine weiteren Kopien; `*.apk` bleibt ignoriert. Reine Anweisungspflege erfordert keinen neuen APK-Build.

## Pflege dieses Dateipaars

Direkte aktuelle Nutzeraufträge haben Vorrang. Vor Regeländerungen beide Dateien lesen und Unterschiede inhaltlich zusammenführen; gültige Regeln erhalten. AGENTS.md und CLAUDE.md unmittelbar gemeinsam ändern und danach mit `cmp` auf Bytegleichheit prüfen. Anbieterunterschiede ausdrücklich kennzeichnen und in beiden Dateien führen. Codex lädt AGENTS.md; Claude Code lädt bei diesem Paar CLAUDE.md.

## Abschluss: AI Brain Update

Zum Abschluss, bei Übergabe oder Abbruch eines Arbeitsauftrags ein kompaktes, eigenständig verständliches Update in diesem Format ausgeben; auch bei Recherche, Planung und Dokumentation. Reine Zwischenfragen und Brainstorming normal und knapp beantworten. Projekt eindeutig benennen, Datum des Arbeitsstands in Europe/Berlin verwenden und tatsächliche Ergebnisse von Plänen unterscheiden. Alle vier Abschnitte beibehalten; fehlende Prüfungen und leere Angaben ehrlich kennzeichnen. Keine Zugangsdaten oder privaten absoluten Pfade aufnehmen. Das Update ersetzt keine unabhängige Prüfung und behauptet keine automatische Übertragung ins AI Brain.

```text
## AI Brain Update – [Projekt] – [YYYY-MM-DD]

**Projektkennung:** [Workspace-Ordner oder Repository]
**Auftrag:** [Kurze Beschreibung]
**Stand:** [abgeschlossen / teilweise / blockiert; Version oder Commit, falls vorhanden]

### Ergebnis
- [Tatsächliche Änderungen oder Erkenntnisse; relevante Dateien/Artefakte]

### Prüfung
- [Durchgeführte Prüfung und Ergebnis; nicht geprüft bzw. unabhängige Abnahme offen ausdrücklich nennen]

### Offen / Nächster Schritt
- [Offene Punkte, Blocker und nächster Schritt; sonst keine]

### Hinweise
- [Bestätigte Entscheidungen von Vorschlägen trennen; fachliche/technische Besonderheiten, neue Pakete, IDs etc.; sonst keine]
```
