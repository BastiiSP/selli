# Anpassungswünsche 20.07.2026

Status: von Basti bestätigt, bereit für Implementierungsplan.

Kontext: MVP ist voll funktionsfähig, auf Bastis Handy installiert, Rollout an Melli läuft
gerade (siehe `02 Projekte/Selli.md` im Vault für vollständige Historie). Dieser Batch bündelt
fünf Anpassungswünsche aus einer gemeinsamen Brainstorming-Session, die zusammen umgesetzt
werden.

## 1. Größenanpassung Header/Kalender/Terminliste

**Was:** Header, Kalender/Zeitstrahl und Terminliste bekommen eine anpassbare Größenaufteilung.

**Warum:** Basti empfindet den Header teils als zu raumgreifend, und die Terminliste ist an
vollen Tagen zu klein (viel Scrollen). Kein einzelner akuter Schmerzpunkt, sondern genereller
Wunsch nach mehr Layout-Kontrolle.

**Anforderungen:**
- Gilt einheitlich für alle drei Ansichten (Monat/Woche/Tag) — eine gemeinsame Aufteilung,
  keine separate pro Ansicht.
- Mechanismus offen: Slider/Drag-Handle zwischen den Bereichen ist die bevorzugte Lösung, ein
  einfaches Ein-/Ausklappen pro Bereich ist ebenfalls akzeptabel, falls technisch einfacher
  umsetzbar. Die Wahl trifft die Umsetzung.
- Sinnvolle Mindestgrößen pro Bereich, damit kein Bereich durch Verschieben komplett
  unbrauchbar wird.
- Der eingestellte Zustand wird dauerhaft gespeichert (z.B. DataStore, analog zur bestehenden
  Persistenz von Login-Zustand/Kategorien) und bleibt nach App-Neustart erhalten.

## 2. Farbkorrektur Wochen-/Tagesansicht

**Was:** Termine in Woche/Tag zeigen aktuell fälschlich dieselbe Farbe für Mellis und Bastis
Arbeitstermine.

**Warum (Root Cause, bereits verifiziert):** `TimelineView.kt` färbt Termine über
`categoryTimelineStyle(event.category)` (Arbeit/Privat/Wir-Zeit) statt über die Person. Bastis
Outlook-Arbeitstermine und Mellis Dr.-Plano-Arbeitstermine landen beide in der Kategorie
„Arbeit" und bekommen identische Farbe, unabhängig vom `owner`-Feld. Die Monatsansicht ist davon
nicht betroffen — sie färbt Termine bereits korrekt nach Person (Pills).

**Anforderungen:**
- Woche/Tag soll wie die Monatsansicht die Personenfarbe (Grün = Basti, Lila = Melli) je Termin
  zeigen.
- Die Kategorie-Unterscheidung (Arbeit/Privat/Wir-Zeit) darf dabei nicht komplett verloren
  gehen, tritt aber optisch in den Hintergrund oder wird über ein anderes Merkmal dargestellt
  (z.B. Icon, Muster, Rahmenstil). Konkrete Umsetzung liegt bei der Implementierung.
- „Wir-Zeit"-Termine behalten voraussichtlich weiterhin den bestehenden Lila-Grün-Verlauf, da
  sie ohnehin beide Personen betreffen — sollte im Plan bestätigt werden.

## 3. Neu-laden-Möglichkeit

**Was:** Eine Möglichkeit, neu eingetragene Termine der Partnerin/des Partners zu sehen, ohne
die App komplett schließen und neu öffnen zu müssen.

**Warum:** Die App lädt laut MVP-Architektur bewusst nur beim Öffnen neu (kein Push/Realtime-
Sync, siehe „Technische Planung" in `Selli.md`). Trägt Melli oder Basti während eine Sitzung
bereits läuft etwas ein, sieht die andere Person es aktuell nicht, ohne die App neu zu starten.

**Anforderungen:**
- Kein Echtzeit-Push nötig — diese MVP-Entscheidung bleibt bestehen.
- Stattdessen: eine jederzeit nutzbare, manuelle Aktualisierungsmöglichkeit (z.B. Pull-to-
  Refresh-Geste und/oder ein Button). Genaue UI-Platzierung/-Form liegt bei der Umsetzung.
- Ergänzt den bereits vorhandenen Auto-Refresh nach dem Anlegen eines eigenen Termins (der
  funktioniert schon), deckt aber zusätzlich den Fall ab, dass die *andere* Person etwas
  einträgt.

## 4. Untersuchung: Mellis Arbeitskalender verschwindet zeitweise

**Was:** Melli beobachtet, dass ihr eigener Dr.-Plano-Arbeitskalender teils nicht angezeigt
wird — reproduzierbar, wenn sie mehrere Monate/Wochen in die Zukunft swiped und dann wieder
zurückkehrt. Bastis Arbeitstermine sowie private Termine beider Personen bleiben davon
unberührt. Auf Bastis Gerät nicht reproduzierbar.

**Das ist kein fertiges Design, sondern ein Untersuchungsauftrag.** Bereits verifizierte
architektonische Fakten als Ausgangspunkt:

- Es gibt keinerlei Caching: Jede Navigation (Swipe, Pfeile, Ansichtswechsel) löst einen
  kompletten Neu-Download des **gesamten** ICS-Feeds aus (`OkHttpIcsCalendarRepository.fetchEvents`
  ignoriert den übergebenen `range` beim Netzwerk-Request — nur beim Parsen wird gefiltert).
  Das gilt für Bastis Outlook-Feed genauso wie für Mellis Dr.-Plano-Feed.
- Fehler pro Quelle werden in `DefaultCalendarMergeService.fetchEventsOrEmpty` einzeln
  abgefangen und nur geloggt (`AndroidCalendarLogger`, kein NoOp) — schlägt Mellis ICS-Fetch
  fehl, zeigt die App einfach keine Termine aus dieser Quelle, ohne sichtbare Fehlermeldung.
- `owner = Person.MELLI` ist in `DefaultAppDependencies.kt` korrekt für ihren Feed verdrahtet —
  kein Verwechslungs-Bug mit Bastis Kalender.
- Der verwendete `OkHttpClient` hat keine explizite Timeout-/Retry-Konfiguration (Standard:
  10s). Schnelles, wiederholtes Swipen erzeugt viele parallele Voll-Downloads des Feeds — bei
  einem fremden Server (Dr. Plano) potenziell eher ein Problem als bei Google/Microsoft.

**Ziel:** Root Cause finden (echter Test mit Mellis Gerät/Feed nötig — auf Bastis Gerät nicht
reproduzierbar) und beheben. Zusätzlich klären: Sollten Fehler beim Laden einzelner Quellen
künftig sichtbarer gemacht werden (statt still zu verschwinden), damit sowas schneller auffällt?

## 5. Eigene Termine löschen

**Was:** Für eigene Google-Kalender-Termine soll es zusätzlich zum bestehenden lokalen
Ausblenden/Bearbeiten eine echte Löschen-Funktion geben.

**Warum:** Aktuell lässt sich ein eigener Termin nur lokal ausblenden (bleibt in Google
bestehen) oder lokal überschreiben. Für Termine, die einem selbst gehören, ist das umständlich,
wenn der Termin tatsächlich nicht mehr gebraucht wird.

**Anforderungen:**
- Echtes Löschen über die Google Calendar API — nicht nur lokales Ausblenden.
- Gilt für **alle eigenen Google-Kalender-Termine**, unabhängig davon, ob sie über Selli oder
  direkt in Google Calendar angelegt wurden — nicht für den Arbeitskalender (ICS, read-only)
  und nicht für Termine der Partnerin/des Partners (dafür bleibt ausschließlich lokales
  Ausblenden möglich, da man dort nicht Owner ist).
- Bei Serien dieselbe Wahl wie beim bestehenden Bearbeiten: „nur dieses Vorkommen" oder „dieses
  und alle folgenden" löschen — nutzt echte Google-Serien-Semantik.
- Ergänzt die bestehende Ausblenden/Bearbeiten-Auswahl im Termin-Aktionen-Sheet um eine dritte
  Option.
- Der Löschen-Button hebt sich farblich klar von Ausblenden/Bearbeiten ab (z.B. rote,
  transparente Füllung), damit die destruktive Aktion nicht versehentlich ausgelöst wird.

## Owner-Hinweis für den Implementierungsplan

- Punkte 1, 2, 5 (UI-Teil) → Claude (Compose/UI)
- Punkt 3 (Refresh-Trigger UI) → Claude, darunterliegende Fetch-Logik ggf. Codex
- Punkt 4 (Untersuchung/Fix Netzwerk/Parsing) → Codex
- Punkt 5 (Google-Calendar-Delete-API-Aufruf, Serien-Semantik) → Codex

Kein Punkt wird zwischen beiden Tools aufgeteilt innerhalb derselben Datei — die obige Zuordnung
ist grob nach Bereich, der Implementierungsplan konkretisiert pro Datei.
