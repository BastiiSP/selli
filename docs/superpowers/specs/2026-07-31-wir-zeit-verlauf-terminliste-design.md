# Wir-Zeit als Verlauf in der Terminliste

## Ziel

Ein lang laufender Wir-Termin (ganztägig oder mehrstündig, z. B. "Homeoffice bei
Melli") geht in der Terminliste (`DayDetail`) heute optisch unter, weil er als
genauso große Einzelzeile zwischen kurzen Terminen steht und beim Scrollen leicht
übersehen wird — obwohl er eigentlich den ganzen Zeitraum prägt, in dem andere,
kürzere Termine (z. B. ein Arbeitstermin) parallel laufen. Termine, die sich
zeitlich mit einem Wir-Termin überschneiden, sollen deshalb sichtbar als
zusammengehörige Gruppe dargestellt werden statt als lose Einzelzeilen.

Betroffen ist ausschließlich die Terminliste (`DayDetail.kt`), die unverändert
unter allen drei Kalenderansichten (Monat/Woche/Tag) läuft. Die Wochen-/
Tagesansicht mit ihrem 24h-Zeitstrahl (`TimelineLayout.kt`) ist nicht Teil dieser
Spec — dort positionieren sich überlappende Termine bereits heute automatisch
nebeneinander nach ihrer echten Zeitspanne.

## Wann eine Gruppe entsteht

Ein Termin mit Kategorie "Wir-Zeit" (`TOGETHER`) bildet eine Gruppe mit jedem
anderen Termin desselben Tages, dessen Zeitspanne sich mit seiner überschneidet —
unabhängig von Besitzer:in oder Kategorie des überschnittenen Termins. Termine
ohne Überschneidung zu einem Wir-Termin bleiben normale Einzelzeilen wie heute.

**Randfall — zwei sich überschneidende Wir-Termine:** Sehr seltener Fall (z. B.
wenn ein zweiter Termin nachträglich ebenfalls auf "Wir-Zeit" umkategorisiert
wird und zufällig denselben Zeitraum trifft). Der zeitlich früher beginnende
Wir-Termin wird zur Gruppen-Kopfzeile, der andere erscheint als ganz normales
eingerücktes Gruppenmitglied darunter — kein zweiter, verschachtelter Balken.

## Darstellung

Ein Gruppen-Eintrag in der Liste besteht aus zwei Teilen:

1. **Der Wir-Termin selbst** — exakt wie eine heutige Terminkarte: volle Breite,
   Lila-Grün-Verlauf-Farbkapsel, Titel, Zeit, Kategorie-Chip. Keine visuelle
   Sonderbehandlung.
2. **Direkt darunter**: eine `Row` aus einem durchgehenden Lila-Grün-Balken
   (links, über die volle Höhe der Gruppenmitglieder) und, leicht nach rechts
   eingerückt, einer Spalte mit den überschnittenen Terminen — jeder davon in
   seiner bisherigen Darstellung (eigene Farbkapsel/Personenfarbe, Titel, Zeit,
   Kategorie-Chip), nur schmaler durch die Einrückung.

Der Balken ist Teil desselben Layouts wie die eingerückten Karten (eine
gemeinsame `Row` mit fester Balkenbreite + Karten-Spalte), nicht eine separat
positionierte Zeichnung über mehrere Listeneinträge hinweg — dadurch bleibt er
beim Scrollen automatisch exakt synchron mit den Karten, ohne eigene
Scroll-Offset-Berechnung.

Termine ohne Überschneidung stehen chronologisch einsortiert weiterhin als ganz
normale Einzelzeilen zwischen den Gruppen-Einträgen. Der Gruppen-Eintrag selbst
sortiert sich anhand des Start des Wir-Termins ein — ein überschnittener Termin,
der etwas früher beginnt als der Wir-Termin (z. B. 8:30 Uhr bei einem ab 9 Uhr
laufenden Wir-Termin), steht trotzdem unter dessen Kopfzeile statt strikt davor.
Der Wir-Termin bleibt bewusst immer der Anker der Gruppe, unabhängig von den
genauen Einzel-Startzeiten seiner Mitglieder. Innerhalb der eingerückten Spalte
sind die überschnittenen Termine untereinander chronologisch sortiert.

## Interaktion

- **Wir-Termin antippen**: öffnet sein bestehendes, unverändertes Aktionen-Sheet
  — keine Sonderversion für gruppierte Termine.
- **Überschnittenen Termin antippen**: öffnet ebenfalls sein bestehendes,
  unverändertes Aktionen-Sheet, komplett unabhängig vom Wir-Termin.
- **Keine Gruppierungs-eigene Zustandsverwaltung**: Die Gruppen entstehen bei
  jedem Rendern frisch aus den aktuellen, bereits vorhandenen Termindaten (rein
  abgeleitet, nicht gespeichert). Jede Änderung — Zeit, Kategorie, Ausblenden,
  Löschen, an einem beliebigen beteiligten Termin — wirkt sich automatisch beim
  nächsten Neuzeichnen aus:
  - Ändert sich die Zeit eines überschnittenen Termins so, dass er den
    Wir-Termin nicht mehr trifft, wird er wieder eine normale Einzelzeile.
  - Verliert der Wir-Termin seine Kategorie "Wir-Zeit" (umkategorisiert,
    ausgeblendet oder gelöscht), löst sich die ganze Gruppe auf — alle vorher
    zusammengefassten Termine erscheinen wieder einzeln.
  - Kein manuelles Aufräumen oder Migrations-Schritt nötig.

## Technische Umsetzung

- Eine reine, Compose-freie Gruppierungs-Funktion (Termine eines Tages →
  Liste aus `EinzelTermin`/`Gruppe(wirTermin, überschnitteneTermine)`) — analog
  zu bestehenden reinen Hilfsfunktionen wie `groupByDay`, isoliert unit-testbar.
- `DayDetail`s `LazyColumn` rendert pro Listenelement entweder die bestehende
  `EventCard` (unverändert) oder einen neuen `EventGroupCard`-Composable
  (Wir-Termin-Karte + Balken-Row mit eingerückten `EventCard`s).
- Überschneidung: einfacher Intervall-Vergleich (`start < andererEnd &&
  ende > andererStart`), keine neue Datenquelle nötig.

## Tests

- Gruppierungsfunktion: kein Wir-Termin an dem Tag → alle Einzelzeilen
  unverändert; ein Wir-Termin ohne Überschneidung → bleibt Einzelzeile; ein
  Wir-Termin mit einer/mehreren Überschneidungen → korrekte Gruppe mit allen
  und nur den wirklich überschneidenden Terminen; zwei überschneidende
  Wir-Termine → früherer wird Kopf, anderer normales Gruppenmitglied;
  chronologische Reihenfolge zwischen Gruppen und Einzelzeilen bleibt erhalten.
- Nach Zeit-/Kategorieänderung eines beteiligten Termins liefert die
  Gruppierungsfunktion (mit den aktualisierten Termindaten erneut aufgerufen)
  das korrekt aufgelöste bzw. neu zusammengesetzte Ergebnis.
