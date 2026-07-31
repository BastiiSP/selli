# Wir-Zeit-Benachrichtigungen

## Ziel

Wenn der/die Partner:in einen neuen Wir-Zeit-Termin anlegt oder einen bestehenden
Wir-Zeit-Termin inhaltlich ändert, soll man ohne die App zu öffnen eine
Android-Benachrichtigung bekommen. Antippen führt direkt zum betroffenen Termin.

Bewusst **kein neues Backend und keine Echtzeit**: Die Prüfung läuft periodisch im
bestehenden Hintergrund-Job mit, der schon heute alle 30 Minuten für das Widget läuft.
Das entspricht der bisherigen Architektur-Entscheidung von Selli ("kein eigenes
Backend nötig") und vermeidet die zusätzliche Komplexität von Google-Webhook-Kanälen
und einer Push-Zustellung über Firebase.

## Was zählt als relevanter Wir-Zeit-Termin

Ein Termin mit Kategorie "Wir-Zeit" (`EventCategory.TOGETHER`), dessen `owner` der/die
Partner:in ist (nicht die eigene Person). Eigene Wir-Zeit-Termine lösen nie eine
Benachrichtigung aus — die kennt man ja schon, weil man sie selbst angelegt hat.

## Verhalten beim Hintergrund-Check

Der bestehende `WidgetRefreshWorker` (läuft alle 30 Minuten, holt bereits die
zusammengeführten Termine für die Widget-Aktualisierung) bekommt einen zusätzlichen
Schritt: Er vergleicht die aktuell sichtbaren relevanten Wir-Zeit-Termine mit einem
lokal gespeicherten Stand der zuletzt gesehenen Wir-Zeit-Termine (neuer, kleiner
Speicher nach demselben Muster wie der bestehende Widget-Snapshot-Speicher). Pro
Termin werden Titel, Start, Ende und Ort gespeichert.

- **Komplett neuer Termin** (Schlüssel Quelle+ID nicht im gespeicherten Stand) →
  "neu"-Benachrichtigung.
- **Bekannter Termin mit Abweichung** in Titel, Zeit, Ort oder Beschreibung gegenüber
  dem gespeicherten Stand → "aktualisiert"-Benachrichtigung, die die konkret
  geänderten Werte nennt.
- **Unverändert** → keine Benachrichtigung.
- Nach jedem Check wird der gespeicherte Stand auf den aktuellen Stand aktualisiert
  (unabhängig davon, ob benachrichtigt wurde).

**Erster Lauf nach der Installation dieses Updates:** Der Speicher ist leer, daher
gelten beim ersten Check alle aktuell sichtbaren Wir-Zeit-Termine des Partners als
neu und lösen jeweils eine Benachrichtigung aus (bewusste Entscheidung, siehe unten
unter "Entscheidungen" — macht das Feature sofort nach der Installation prüfbar,
ohne auf einen echten neuen Termin warten zu müssen).

Die Erkennungslogik selbst ist eine reine Kotlin-Klasse ohne Android-Abhängigkeiten
(gleiches Prinzip wie die bestehende `NextPartnerEventSelector`), damit sie isoliert
unit-testbar bleibt. Sie bekommt die aktuelle Terminliste plus den gespeicherten
Stand herein und gibt eine Liste von "neu"/"aktualisiert"-Ereignissen mit den
jeweils relevanten Feldern zurück; das Auslösen der eigentlichen Android-
Benachrichtigung passiert danach in einer dünnen, Android-spezifischen Schicht.

## Benachrichtigungsinhalt

- **Neu:** Titel "{Partner-Name} hat gemeinsame Zeit eingetragen", Text: Termin-Titel
  + Datum (z. B. "Wochenende bei euch – Sa, 8. August").
- **Aktualisiert:** Titel "{Partner-Name} hat euren gemeinsamen Termin aktualisiert",
  Text: Termin-Titel + eine kommagetrennte Aufzählung der geänderten Werte (z. B.
  "Wochenende bei euch – Neue Zeit: 10:00–12:00, Neuer Ort: Frankfurt"). Bei
  geänderter Beschreibung ohne weitere Details ein kurzer Hinweis ("Beschreibung
  aktualisiert") statt des vollen Texts.
- Ein Kanal ("Wir-Zeit-Updates"), einmalig beim App-Start angelegt (idempotent).
- Stabile Notification-ID pro Termin (aus Quelle+ID abgeleitet) — mehrere schnelle
  Änderungen am selben Termin ersetzen die vorherige Benachrichtigung in der Leiste,
  statt sich zu stapeln.

## Berechtigung

Ab Android 13 ist `POST_NOTIFICATIONS` eine explizite Laufzeit-Berechtigung. Die App
fragt einmalig beim nächsten Öffnen nach diesem Update danach (System-Dialog über
`ActivityResultContracts.RequestPermission()`), nicht schon beim Verbinden — die
Funktion ist vorher noch nicht relevant. Wird die Berechtigung verweigert oder sind
Benachrichtigungen sonst deaktiviert (`NotificationManagerCompat.areNotificationsEnabled()`),
wird der **komplette** Check-Schritt übersprungen — inklusive Aktualisierung des
gespeicherten Stands. Der Rest der App (Widget, Kalender) bleibt unberührt.

Das ist bewusst so gewählt (nicht nur das Benachrichtigen selbst überspringen,
sondern auch das Mitschreiben): Sonst würden Termine, die während einer Phase ohne
Berechtigung entstehen, still als "bereits gesehen" vermerkt und nie nachträglich
gemeldet. Mit dieser Regel gilt der "erste Lauf" (siehe oben) immer als der erste
Check, bei dem die Berechtigung tatsächlich vorliegt — egal ob das direkt nach der
Installation ist oder erst später, wenn die Berechtigung nachträglich erteilt wird.

## Antippen der Benachrichtigung

Die Benachrichtigung trägt Zieltag und Termin-Schlüssel (Quelle+ID) als Intent-
Extras zu `MainActivity`. Nach dem App-Start werden diese einmalig ausgewertet:
Sobald die Termine für den Zieltag geladen sind, springt die Kalenderansicht über
das bestehende `selectedDay` automatisch dorthin und öffnet über das bestehende
`selectedEvent` das Termin-Aktionen-Sheet für den betroffenen Termin — beides nutzt
vorhandene ViewModel-Mechanismen, es entsteht keine komplett neue Navigation.

## Grenzen (bewusst außerhalb dieses Scopes)

- Kein "storniert"-Hinweis, wenn ein Termin von Wir-Zeit auf Arbeit/Privat
  zurückgestuft oder gelöscht wird — nur Neuanlage und inhaltliche Änderung.
- Keine Echtzeit/kein Backend (siehe Ziel) — bis zu 30 Minuten Verzögerung.
- Keine Zusammenfassung bei vielen gleichzeitigen neuen Terminen (jeder bekommt eine
  eigene Benachrichtigung) — bei der Nutzung zu zweit unkritisch.

## Tests

- Erkennungslogik (neue reine Klasse): komplett neuer Termin → "neu"; Termin mit
  geänderter Zeit/Ort/Titel/Beschreibung → "aktualisiert" mit korrekten Änderungs-
  Details; unveränderter Termin → keine Meldung; eigene Wir-Zeit-Termine → nie
  gemeldet; erster Lauf mit leerem Speicher → alle aktuellen Termine als "neu".
- Gespeicherter Stand wird nach jedem Check unabhängig vom Ergebnis aktualisiert.
- Notification-Aufbau: Titel/Text-Formatierung für "neu" und "aktualisiert",
  stabile ID pro Termin.
- Bestehende Widget-Tests bleiben unverändert grün (Erkennungslogik hängt nicht an
  der Widget-Snapshot-Erzeugung).
