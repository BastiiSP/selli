# Termin entfernen (Löschen/Ausblenden vereinheitlicht)

## Ziel

Aktuell gibt es im Termin-Aktionen-Sheet zwei getrennte Aktionen: "Ausblenden" (für
jeden Termin, rein lokal) und "Endgültig löschen" (nur für eigene Google-Termine,
`canDelete = event.source == CalendarSource.GOOGLE_OWN`). Termine von Melli oder aus
dem Arbeitskalender lassen sich nicht "entfernen" im Sinne von Basti — nur ausblenden,
was ihm nicht als vollwertige Lösung auffiel. Zusätzlich gibt es für **Wir-Zeit**-Termine,
die die Partnerin angelegt hat, gar keinen Weg, sie loszuwerden, obwohl es sich
inhaltlich um einen gemeinsamen Termin handelt.

Dieses Feature vereinheitlicht "Termin entfernen" für **jeden** Termin — die tatsächliche
Wirkung hängt vom Termintyp ab, aber die Bedienung fühlt sich für Basti/Melli einheitlich an.

## Verhalten je Terminart

| Fall | Beispiel | Aktion |
|---|---|---|
| 1. Eigener Termin (`GOOGLE_OWN`), keine Wir-Zeit | Bastis privater Termin | **Echtes Löschen** über die Google Calendar API — bereits vorhanden (`GoogleCalendarEventDeletion`), unverändert. |
| 2. Wir-Zeit, selbst angelegt (`GOOGLE_OWN` + `category == TOGETHER`) | Basti hat die Wir-Zeit erstellt | **Echtes Löschen** — bereits vorhanden, storniert automatisch auch bei Melli (Google storniert Einladungen an Teilnehmer beim Löschen durch den Organisator). Keine Änderung nötig. |
| 3. Wir-Zeit, vom Partner angelegt (`GOOGLE_PARTNER` + `category == TOGETHER`) | Melli hat die Wir-Zeit erstellt, Basti ist eingeladen | **Neu — Lösch-Anfrage:** Basti kann als Teilnehmer nicht für alle löschen (siehe "Technische Einschränkung" unten). Stattdessen: (a) sofortiges lokales Ausblenden bei Basti (bestehender Mechanismus), (b) Markierung `selli:deleteRequestedBy=<Person>` in `extendedProperties.shared` auf Bastis eigener Teilnehmer-Kopie des Termins, (c) Melli bekommt beim nächsten Hintergrund-Sync eine Push- und In-App-Benachrichtigung, die sie zum Termin führt, wo sie ihn wie gewohnt selbst final löschen kann. |
| 4. Alles andere | Mellis privater Termin, jeder Arbeitskalender-Eintrag (ICS, egal von wem) | **Nur lokales Ausblenden** — bestehender Mechanismus (`hideSelectedEvent`), unverändert. Kein Sync zwischen den Geräten (bewusste Entscheidung, siehe unten). |

**Bewusst kein neues Backend:** Fall 4 wurde explizit auf reines lokales Ausblenden
begrenzt, weil ein echtes "für beide ausblenden" bei Terminen ohne Teilnehmer-Beziehung
(z. B. Mellis privater Termin, den Basti nur über die Kalenderfreigabe lesen kann) keinen
bestehenden Google-eigenen Kanal hat, über den eine App der anderen etwas mitteilen könnte
— das würde ein eigenes kleines Backend erfordern. Fall 3 funktioniert dagegen *ohne*
Backend, weil die Teilnehmer-Einladung selbst schon ein geteiltes Feld bereitstellt
(`extendedProperties.shared`, dasselbe Muster wie die bestehende `selli:shared`-Markierung).

## UI-Änderungen

- **`EventActionsSheet`**: Der bestehende `canDelete`-Gate (`event.source == GOOGLE_OWN`)
  wird um Fall 3 erweitert. Die Aktion heißt weiterhin "Endgültig löschen" für Fall 1/2,
  aber **"Löschen anfragen"** (oder ähnlich, Wortlaut ist Ausführungsdetail) für Fall 3 —
  wichtig ist, dass der Wortlaut ehrlich kommuniziert, dass der Termin nicht sofort für
  immer weg ist, sondern eine Anfrage an die Partnerin geschickt wird. Für Fall 4 bleibt
  nur der bestehende "Ausblenden"-Button sichtbar, kein neuer Löschen-Button (kein
  potenziell irreführender "Löschen"-Text für etwas, das technisch nur ein Ausblenden ist).
  Serien-Scope-Auswahl (Vorkommen/ganze Serie) funktioniert für Fall 3 nach demselben
  Muster wie beim bestehenden Löschen-Dialog.
- **`CustomizationManagerSheet`**: Jede Zeile (`CustomizationRow`) bekommt zusätzlich ein
  Mülleimer-Icon, das dieselbe kontextabhängige Aktion auslöst wie oben (Fall 1/2: sofort
  löschen mit Bestätigung; Fall 3: Lösch-Anfrage senden; Fall 4: kein Mülleimer-Icon, da
  "Einblenden" bereits die einzige sinnvolle Gegenaktion zum Ausblenden ist). Dafür muss
  die Zeile zusätzlich zum `CalendarEvent` (bzw. dessen `source`/`category`) Zugriff haben
  — aktuell bekommt `CustomizationRow` nur das `EventCustomization`-Objekt ohne diese
  Information.

## Technische Einschränkung (Google Calendar Organisator/Teilnehmer)

Nur der Organisator eines Google-Calendar-Termins kann ihn über die API so löschen, dass
er für alle Teilnehmer storniert wird. Ein Teilnehmer kann über die API nur die eigene
Teilnahme ändern (z. B. absagen) — das entfernt den Termin nicht aus dem Kalender des
Organisators. Deshalb kein Versuch, für Fall 3 ein "echtes Löschen für beide" ohne
Schreibrechte auf Mellis Kalender zu erzwingen (das würde eine ACL-Erweiterung auf
"Änderungen vornehmen" bedeuten — bewusst nicht gewollt, siehe Verlauf im Vault).

**Offener technischer Punkt, keine Design-Frage:** Ob ein eingeladener Teilnehmer
`extendedProperties.shared` auf seiner eigenen Kopie eines fremden Termins per
`calendar.events().patch(calendarId="primary", eventId=<eigene Instanz-ID>)`
erfolgreich schreiben kann, ist im bestehenden Code nirgends belegt (alle bisherigen
Schreibzugriffe, inkl. `GoogleCalendarEventSharing`, laufen ausschließlich im
Organisator-Kontext auf `GOOGLE_OWN`-Terminen). Muss als Erstes in der Umsetzung per
kurzem Test verifiziert werden. **Fallback, falls es nicht funktioniert:** Der Schreibversuch
schlägt fehl (HTTP 403), Selli fängt das ab, führt das lokale Ausblenden trotzdem aus und
zeigt Basti einen Hinweis ("Bitte Melli direkt bitten, den Termin zu löschen") statt
abzustürzen oder den Fehler zu verschlucken.

## Technische Umsetzung

- **Markierung setzen**: `GoogleCalendarEventSharing` bekommt eine neue Methode analog zu
  `setPartnerAttendance`, die statt eines Boolean-Flags einen Key-Value-Eintrag in
  `extendedProperties.shared` schreibt (`selli:deleteRequestedBy` = Name der anfragenden
  Person). Nutzt denselben `get()` → Feld setzen → `update()`-Ablauf wie
  `updateSelliSharedMarker`, aber generischer (nicht hart auf `selli:shared` verdrahtet).
- **Erkennung beim Partner**: `PartnerSharedEventChangeDetector` bekommt eine dritte
  `SharedEventChange`-Variante (`sealed interface`, bisher `New`/`Updated`) für
  "Löschung angefragt" — erkannt, wenn ein Termin neu die `selli:deleteRequestedBy`-
  Markierung trägt, die beim letzten bekannten `SharedEventFingerprint` noch fehlte.
  `SharedEventFingerprint` muss dafür um dieses Feld erweitert werden.
- **Benachrichtigung**: `SharedEventNotifier.notifyChanges(...)` bekommt einen neuen Text
  für diesen Change-Typ ("Basti möchte '<Titel>' löschen"). Tippen auf die Benachrichtigung
  nutzt denselben bestehenden Deeplink-Mechanismus (`EXTRA_DAY`/`EXTRA_SOURCE`/
  `EXTRA_EVENT_ID` → `openDeepLinkedEvent`) wie die bestehenden Wir-Zeit-Benachrichtigungen
  — springt zum Termin, öffnet das `EventActionsSheet`, wo Melli (als Organisatorin,
  `canDelete = true`) den bereits vorhandenen "Endgültig löschen"-Button antippt. Kein
  neuer, komplexerer Direkt-Aktions-Button auf der System-Notification nötig (kein neues
  `BroadcastReceiver`-Muster) — ein Tipp zusätzlich zum Öffnen der App ist ein vertretbarer
  Kompromiss gegenüber der Komplexität einer echten One-Tap-Notification-Action.
- **Sofortiges lokales Ausblenden bei der anfragenden Person**: Reine Wiederverwendung des
  bestehenden `hideSelectedEvent`/`EventCustomizationRepository`-Wegs — kein neuer Code
  nötig, nur ein zusätzlicher Aufruf direkt nach dem (versuchten) Schreiben der Markierung.
- **`CustomizationRow`/`CustomizationManagerSheet`**: Aufrufer (`CalendarScreen.kt`) muss
  zu jeder `EventCustomization` das zugehörige `CalendarEvent` mitliefern (Lookup über
  `target`/`EventKey` gegen `uiState.eventsByDay`), damit die Zeile weiß, welche der drei
  Aktionen (löschen/anfragen/nichts) neben "Einblenden"/"Zurücksetzen" sinnvoll ist.

## Randfälle

- **Melli hat den Termin bereits gelöscht, bevor Bastis Anfrage ankommt** (z. B. beide
  handeln parallel): Bastis Markierungs-Schreibversuch schlägt fehl (Termin/Kalender-Eintrag
  existiert nicht mehr) — abfangen, lokales Ausblenden lief ja schon vorher, kein Fehlerdialog
  nötig, da das Ergebnis (Termin weg) ohnehin erreicht ist.
- **Melli reagiert nie auf die Anfrage**: Der Termin bleibt für Basti dauerhaft ausgeblendet
  (bestehendes Ausblenden-Verhalten, jederzeit über "Einblenden" im Verwaltungsscreen
  rückgängig zu machen), der reale Google-Termin bleibt unangetastet in ihrem Kalender
  bestehen. Kein automatisches Nachfassen/Erinnern in dieser Runde (YAGNI, kann bei Bedarf
  später ergänzt werden).
- **Arbeitskalender-Termin "löschen"**: Da ICS-Feeds reine Nur-Lese-Abrufe sind, ist über
  Selli nie eine echte Löschung im Ursprungssystem (Outlook/Dr. Plano) möglich — fällt
  konsequent unter Fall 4 (nur Ausblenden), UI zeigt konsequent nur "Ausblenden", nie
  "Löschen".
- **Serie vs. Einzeltermin bei der Lösch-Anfrage**: Analog zum bestehenden Löschen-Dialog
  (`DeletionScope.SINGLE_OCCURRENCE`/`THIS_AND_FOLLOWING`) — die Markierung wird auf der
  jeweils gewählten Instanz (Einzelvorkommen oder Serien-Master) gesetzt.

## Tests

- `PartnerSharedEventChangeDetector`: neuer Testfall für die "Löschung angefragt"-Erkennung
  (Fingerprint ohne Markierung → mit Markierung), keine Regression für `New`/`Updated`.
- `GoogleCalendarEventSharing` (bzw. die neue Methode): Unit-Test mit einem Fake/Mock der
  Calendar-API-Aufrufkette, prüft dass der richtige Key/Value gesetzt und `update()` mit
  den richtigen Parametern aufgerufen wird.
- `EventActionsSheet`: kein automatisierter UI-Test in diesem Projekt üblich (siehe
  bisherige Praxis) — Gerätetest deckt die drei sichtbaren Zustände ab (eigener Termin,
  Wir-Zeit vom Partner, alles andere).
- Randfall "Schreibversuch schlägt fehl" (403 o. ä.): Unit-Test, dass das lokale Ausblenden
  trotzdem passiert und kein Absturz/unbehandelte Exception auftritt.
