---
name: release-build
description: "Baut Selli für die Weitergabe an Basti/Melli: Tests laufen lassen, Debug-APK bauen, nach selli.apk kopieren. Nur manuell aufrufen (/release-build) - löst einen echten Build-Vorgang mit Seiteneffekt (überschreibt selli.apk) aus."
disable-model-invocation: true
---

# Selli: Release-Build

Bündelt den Ablauf, der laut `CLAUDE.md`/`AGENTS.md` nach jeder abgeschlossenen
Aufgabe an diesem Projekt fällig ist: Tests grün, Debug-APK bauen, an den
festen Ablageort für Basti/Melli kopieren.

## Ablauf

1. **Unit-Tests laufen lassen** (insbesondere die von Codex gelieferten
   Logik-Module unter `data/`, `domain/`):
   ```bash
   ./gradlew testDebugUnitTest
   ```
   Bei Rot: stoppen, Fehler zeigen, nicht weiterbauen. Nicht versuchen den
   Test "grün zu reden" - Basti prüft die eigentliche Ursache.

2. **Debug-APK bauen:**
   ```bash
   ./gradlew assembleDebug
   ```

3. **APK an den festen Ablageort kopieren** (einziger Ort für die Weitergabe
   per WhatsApp - kein Desktop, keine weitere Kopie):
   ```bash
   cp app/build/outputs/apk/debug/app-debug.apk selli.apk
   ```
   `selli.apk` liegt im Projekt-Root, ist über `.gitignore` (`*.apk`)
   ausgeschlossen und wird bei jedem Lauf überschrieben.

4. **Claudian-Update-Format ausgeben** (Format steht am Ende der Projekt-
   `CLAUDE.md`) - Basti kopiert das Update in seinen Obsidian-Vault
   (`02 Projekte/Selli.md`).

## Wann abbrechen statt weiterzumachen

- Tests rot → Build nicht ausführen, Testausgabe zeigen.
- `assembleDebug` schlägt fehl → Fehlerausgabe zeigen, nicht raten und selbst
  reparieren ohne Rücksprache, wenn die Ursache unklar ist (z. B. fehlende
  Werte in `local.properties`).
- Kein `local.properties` vorhanden oder Pflichtwerte fehlen
  (`selli.googleServerClientId`, `selli.icsFeedUrl`, `selli.melliIcsFeedUrl`,
  `selli.placesApiKey`) → Build schlägt ohnehin fehl, das direkt benennen statt
  lange Gradle-Fehlermeldungen zu interpretieren.
