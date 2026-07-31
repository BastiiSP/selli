# Widget-Größenvarianten

## Ziel

Das Homescreen-Widget zeigt heute unabhängig von seiner Größe immer nur eine
einzige Information: den nächsten Termin des Partners. Größere Widget-Flächen
bleiben ungenutzt leer. Das Widget soll je nach Größe gestaffelt mehr zeigen —
mehr Fläche heißt mehr nützliche Information, ohne die kompakte Variante zu
verändern.

## Inhalt je Größenstufe

- **Klein** (unverändert): nächster Termin des Partners — Avatar, Personen-Punkt,
  Titel, Zeit. Identisch zur heutigen Darstellung.
- **Mittel**: zusätzlich eine zweite Zeile mit dem nächsten Wir-Zeit-Termin
  (Kategorie `TOGETHER`, unabhängig davon wer ihn angelegt hat).
- **Groß**: zusätzlich eine dritte Zeile mit dem nächsten gemeinsamen freien Slot
  (dieselbe Regel wie in der Tagesansicht: mindestens drei zusammenhängende
  Stunden im Fenster 9–22 Uhr).

Keine Terminliste, keine zusätzliche Personalisierung in dieser Runde — das war
ursprünglich angedacht, wurde aber bewusst vereinfacht (siehe "Zurückgestellt").

## Visuelle Gestaltung

Baut auf der bestehenden Bildsprache auf, keine neuen Illustrationen nötig:

- **Zeile 1** (immer): wie heute — Avatar + Personen-Punkt (Lila/Grün) + "{Partner}
  als Nächstes" + Titel + Zeit.
- **Zeile 2** (ab Mittel): Wir-Zeit-Gradient-Pille (dieselbe Lila-Grün-Optik wie die
  Kategorie-Kennzeichnung in der App) + "Nächste Wir-Zeit" + Titel + Zeit.
- **Zeile 3** (nur Groß): Maskottchen-Pose `mascot_celebrating` (dieselbe "beide
  frei"-Freuden-Pose, die schon im Kalender-Header verwendet wird, `MascotMood.HAPPY`
  in `SelliMascot.kt`) + "Nächster freier Slot" + Tag + Zeitspanne.
- **Leerer Zustand pro Zeile** (kein qualifizierender Termin/Slot im Suchfenster,
  siehe unten): Zeile entfällt nicht ersatzlos, sondern zeigt "Nichts in Sicht" mit
  der bestehenden `mascot_empty_state`-Pose (`MascotMood.EMPTY`) — dieselbe Pose,
  die heute schon für "Partner hat nichts anstehend" verwendet wird.
- **Zeilen sind unabhängig voneinander**, nicht mehr ein einziger Alles-oder-nichts-
  Zustand für die ganze Karte: Heute zeigt die Karte entweder die volle
  "Partner hat nichts anstehend"-Ansicht oder den einen nächsten Termin. Neu: Ist
  Zeile 1 leer (Partner hat 30 Tage lang nichts), aber Zeile 2 oder 3 hätte
  Inhalt (z. B. ein von mir selbst angelegter Wir-Zeit-Termin), werden ab Mittel/
  Groß trotzdem alle für die Größe vorgesehenen Zeilen einzeln gerendert — Zeile 1
  dann mit ihrem eigenen "nichts anstehend"-Hinweis, Zeile 2/3 mit ihrem Inhalt.
  Nur wenn alle für die Größe vorgesehenen Zeilen leer sind, entsteht wieder der
  heutige volle Leerzustand über die ganze Karte.

## Suchfenster

Alle drei Zeilen suchen einheitlich **30 Tage** voraus (ein guter Monat) statt der
heutigen 14 Tage, die nur für die erste Zeile gelten. Bewusst kein größeres Fenster
(z. B. 60 Tage): mehr Tage würden mehr ICS-Anfragen an Mellis Dr.-Plano-Feed nach
sich ziehen (bzw. mehr Tage im internen Bereich verarbeiten) — nach der bereits
dokumentierten HTTP-429-Sperre dort (siehe
`docs/superpowers/investigations/2026-07-20-melli-ics-verschwindet.md`) bewusst
konservativ gewählt. 30 Tage lassen sich bei Bedarf später unkompliziert erhöhen.

## Technische Umsetzung

- **Größensteuerung**: `SizeMode.Single` wird zu `SizeMode.Responsive` mit drei
  festen Größen-Buckets (klein/mittel/groß), Glance wählt beim Rendern automatisch
  den nächstliegenden Bucket zur tatsächlich vom Nutzer gewählten Widget-Größe.
- **`WidgetSnapshot`** wächst um zwei optionale Felder: nächster Wir-Zeit-Termin
  und nächster freier Slot (Tag + Start/Ende). Beide bleiben `null`, wenn im
  30-Tage-Fenster nichts qualifiziert.
- **`WidgetRefreshWorker`** berechnet beide zusätzlichen Werte aus derselben,
  bereits geladenen 30-Tage-Terminliste — kein zusätzlicher Netzwerk-Request.
- **Freie-Zeit-Berechnung über mehrere Tage**: Die bestehende
  `CalendarMergeService.freeBlocks(day)`-Methode lädt ihre Termine intern selbst
  pro Tag neu (siehe bestehenden Code). Für die Suche über 30 Tage wird das
  umgebaut: Die reine Berechnungslogik pro Tag (Tagesfenster → blockierte
  Intervalle → freie Blöcke) wird aus einer bereits vorliegenden Terminliste
  ausgelagert, damit sie für mehrere Tage wiederverwendet werden kann, ohne pro
  Tag erneut zu laden. Die bestehende `freeBlocks(day)`-Methode bleibt für die
  Tagesansicht unverändert nutzbar (ruft intern nur noch die ausgelagerte Logik
  auf), das Widget nutzt daneben die neue Mehrtages-Variante mit den ohnehin schon
  geladenen Daten.
- **Auswahl "nächster Wir-Zeit-Termin"**: erster Termin mit `category == TOGETHER`
  und Start in der Zukunft, unabhängig vom `owner` (im Gegensatz zur Zeile 1, die
  nur Termine des Partners zeigt — Wir-Zeit betrifft ja beide).

## Zurückgestellt (Backlog, nicht Teil dieser Runde)

Bastis Zusatzidee, das Widget komplett personalisierbar zu machen (frei wählbar,
welche Zeilen angezeigt werden, über einen eigenen Konfigurations-Screen), ist
spürbar aufwändiger als die drei festen Stufen (grob nochmal vergleichbarer
Aufwand oben drauf: neuer Konfigurations-Screen, pro Widget gespeicherte Auswahl,
`WidgetCard` müsste diese Auswahl statt der festen Stufen auswerten). Bewusst nicht
Teil dieser Spec — als Idee vermerkt, bei Bedarf nach etwas Nutzung der festen
Stufen gezielt nachbrainstormen.

## Tests

- Auswahl "nächster Wir-Zeit-Termin": findet frühesten TOGETHER-Termin unabhängig
  vom Owner, ignoriert vergangene, `null` wenn keiner im 30-Tage-Fenster.
- Mehrtages-Freie-Zeit-Berechnung: liefert denselben frühesten qualifizierenden
  Slot wie die bestehende Tages-Methode für den ersten Tag mit einer Lücke,
  überspringt vollständig blockierte Tage korrekt, `null` wenn 30 Tage lang nichts
  qualifiziert.
- Bestehende `freeBlocks(day)`-Tests bleiben unverändert grün (reiner interner
  Umbau, keine Verhaltensänderung für die Tagesansicht).
- Widget-Rendering je Bucket: kleine Größe zeigt nur Zeile 1 (unverändert unter
  bestehenden Tests), mittlere zusätzlich Zeile 2, große zusätzlich Zeile 3;
  „Nichts in Sicht"-Zustand pro Zeile.
