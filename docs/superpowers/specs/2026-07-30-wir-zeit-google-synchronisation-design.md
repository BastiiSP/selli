# Wir-Zeit und Google-Synchronisation

## Ziel

„Wir-Zeit“ ist Sellis eindeutige Kennzeichnung für einen gemeinsamen Termin. Ein so
gekennzeichneter Termin muss in beiden Google-Kalendern erscheinen. Ein separater
Schalter zum Einladen des Partners entfällt.

## Verhalten beim Anlegen

- Die Kategorieauswahl „Arbeit / Privat / Wir-Zeit“ bleibt sichtbar.
- Bei „Wir-Zeit“ setzt Selli im Google-Termin automatisch den Partner als Teilnehmer.
- Bei „Arbeit“ und „Privat“ wird kein Partner eingeladen.
- Der bisherige Schalter „Partner einladen — wird ein gemeinsamer Termin“ wird entfernt.
- Die Ganztags-Festlegung „Als gemeinsam verplante Zeit werten“ bleibt unabhängig und
  beeinflusst ausschließlich Sellis gemeinsame Frei-Zeit-Berechnung.

## Verhalten beim Umkategorisieren

- Nur eigene Google-Termine können zu „Wir-Zeit“ gemacht werden.
- Der Wechsel von „Arbeit“ oder „Privat“ zu „Wir-Zeit“ fügt den Partner als
  Google-Teilnehmer hinzu und versendet die Google-Aktualisierung.
- Der Wechsel von „Wir-Zeit“ zu „Arbeit“ oder „Privat“ entfernt den Partner als
  Google-Teilnehmer und versendet die Google-Aktualisierung.
- Wechsel zwischen „Arbeit“ und „Privat“ bleiben rein lokale Selli-Anpassungen.
- Die lokale Kategorie wird erst gespeichert, nachdem die Google-Aktualisierung
  erfolgreich war. Bei einem Fehler bleibt der bisherige Zustand bestehen und Selli
  zeigt eine verständliche Fehlermeldung.
- Bei Serienterminen bedeutet die serienweite Auswahl eine Änderung der gesamten
  Google-Serie. Google Calendar unterstützt keine native Teilnehmeränderung „ab diesem
  Vorkommen“; die UI benennt die serienweite Möglichkeit deshalb als „gesamte Serie“.
- Einzelne Vorkommen einer Serie können unabhängig geteilt oder entteilt werden.

## Google-Integration

Das CalendarRepository erhält eine Operation zum Setzen des Partner-Teilnehmerstatus.
Die Google-Implementierung lädt das Zielereignis, bewahrt alle fremden Teilnehmer,
fügt ausschließlich die konfigurierte Partneradresse hinzu oder entfernt sie und
aktualisiert Sellis Shared-Metadatum. Das Update verwendet `sendUpdates=all`, damit
Einladung beziehungsweise Entfernung beim Partner ankommt.

Für eine serienweite Änderung wird der Serien-Master verwendet; für ein einzelnes
Vorkommen dessen Instanz-ID. Der lokale Event-Cache wird nach erfolgreichen
Änderungen invalidiert beziehungsweise per erzwungenem Refresh neu geladen.

## Fehler und Grenzen

- Partnerkonto oder Partneradresse müssen vorhanden sein.
- Partner- und Arbeitskalender bleiben read-only.
- Andere bereits vorhandene Teilnehmer bleiben unverändert.
- Doppelte Partner-Teilnehmer werden vermieden.
- Eine fehlgeschlagene Google-Aktualisierung erzeugt keine lokale Kategorieänderung.

## Tests

- Repository-Vertrag und Google-Request für Hinzufügen und Entfernen des Partners.
- Erhalt anderer Teilnehmer und Auswahl von Instanz- beziehungsweise Serien-ID.
- ViewModel-Reihenfolge: Google zuerst, lokale Kategorie nur bei Erfolg.
- Erstellen eines „Wir-Zeit“-Termins setzt automatisch `invitePartner=true`; andere
  Kategorien setzen `false`.
- Bestehende Ganztags-Tests bleiben grün.

