# Wir-Zeit-Countdown

## Ziel

Der Kalender-Header zeigt im ausgeklappten Zustand aktuell die freien
Zeitblöcke des betrachteten Tages (`FreeTimeSection` in `MascotHeader.kt`),
und das große Widget eine "nächster freier Slot"-Zeile (`NextFreeSlotRow`).
Beide werden in der Praxis kaum genutzt. Stattdessen soll ein emotional
stärkerer Fokus rein: ein Countdown bis zur nächsten gemeinsamen Wir-Zeit,
visuell als kleine "lebendige" Wanderweg-Szene im Header — das Maskottchen
nähert sich sichtbar Schritt für Schritt dem nächsten Treffen.

## Umfang

- **Ersetzt**: `FreeTimeSection` im Kalender-Header (`MascotHeader.kt`).
- **Ersetzt**: `NextFreeSlotRow` im großen Widget (vereinfachte, nicht-live
  Variante).
- **Betrifft nicht**: die Wir-Zeit-Benachrichtigungslogik (bleibt
  unverändert) und die Widget-Zeilen 1/2 (nächster Partner-Termin, nächste
  Wir-Zeit).
- Der zuvor diskutierte, verworfene Backlog-Punkt "Vorschläge für nächste
  Treffen" wird durch dieses Feature **nicht** wiederbelebt — reiner
  Countdown, keine Terminvorschläge.

## Verhalten im Kalender-Header

- Datenquelle: bestehender `NextSharedEventSelector.select(events, now)`
  (`domain/widget/NextSharedEventSelector.kt`) — keine neue Selektionslogik,
  nur ein neuer Konsument.
- **Eingeklappt** (`LayoutPreferencesState.headerCollapsed == true`): zeigt
  nur eine einzeilige Text-Kurzform, z. B. "noch 3 Tage, 4 Std., 22 Min. bis
  zur nächsten Wir-Zeit" — keine Illustration.
- **Ausgeklappt** (`headerCollapsed == false`): zeigt die volle
  Wanderweg-Szene anstelle von `FreeTimeSection`. Ersetzt die Composable an
  derselben Stelle in `MascotHeader.kt`.
- Tippen auf den Countdown (in beiden Zuständen) navigiert zum Tag/Termin
  der nächsten Wir-Zeit (analog zum bestehenden `openDeepLinkedEvent`-Muster
  aus der Wir-Zeit-Benachrichtigung).
- Aktualisierung: **minütlich**, über eine `LaunchedEffect`-Coroutine, die
  nur läuft, solange die Composable in der Komposition ist — kein
  Hintergrundlauf, kein messbarer Akku-Unterschied zu selteneren
  Intervallen (reine Vordergrund-UI, keine Wakelocks/Alarme nötig).

## Fortschrittsberechnung ("wie weit ist das Maskottchen gelaufen")

- **0 %-Referenzpunkt**: Zeitpunkt, an dem der Termin angelegt/bekannt
  wurde (Google-`Event.created`).
- **100 %-Referenzpunkt**: Start des Termins.
- `Fortschritt = (now - created) / (start - created)`, geklemmt auf `[0, 1]`.
- Bei Serien zählt das `created` der Serie, nicht des Einzelvorkommens —
  sonst würde der Fortschritt bei jedem Vorkommen wieder auf 0 % springen.
- Neues Feld `created: LocalDateTime?` auf `CalendarEvent`
  (`domain/model/Models.kt`), befüllt in
  `GoogleCalendarEventMapper.toCalendarEvent()` aus `event.created` (im
  Google-SDK bereits vorhanden, bisher nur ungenutzt). Für ICS-Importe
  (Outlook-/Dr.-Plano-Feed) gibt es kein verlässliches Äquivalent → `null`.
- **Fallback ohne `created`** (ICS-Termine, alte/manuelle Importe):
  Fortschritt fest auf 0 % (Maskottchen am Weganfang) — kein Absturz, keine
  Über-/Unterlauf-Anzeige.
- **Randfall `created == start`** (spontan angelegter Termin "für jetzt
  gleich"): Division durch 0 vermeiden, Fortschritt direkt auf 100 % klemmen.

## Zusatzzustände

- **Heute-Zustand**: Termin-Tag erreicht → eigene "Angekommen"-Illustration
  statt Wanderweg. Wiederverwendet `MascotMood.HAPPY` / `mascot_celebrating`
  (dieselbe Pose wie die bestehende "beide frei"-Freude-Anzeige).
- **Leer-Zustand**: kein zukünftiger Treffer vom Selector → eigene,
  ruhigere Illustration statt Wanderweg (z. B. wartend/Ausschau haltend).
  Welche konkrete Pose (neue Illustration vs. Wiederverwendung) ist ein
  Ausführungsdetail, kein Show-Stopper für diese Spec.

## Visuelle Gestaltung (Wanderweg-Szene)

- Übernimmt direkt die bestehende Header-Hintergrundfläche — `selliGradient()`
  ist schon heute der Hintergrund der äußeren `MascotHeader`-Box (unabhängig
  vom Collapse-Zustand, siehe `MascotHeader.kt`). Keine zusätzliche,
  abgegrenzte Karte/Box innerhalb des Headers, sondern Himmel/Weg/Maskottchen
  bespielen direkt diese Fläche.
- Kräftige Variante (nicht die abgeschwächte/dezente): der Himmel ist der
  reguläre `selliGradient()` selbst, keine aufgehellte Alternativfarbe.
- Ebenen (Ansatz "geschichtete lebende Illustration" — statische Bilder +
  Compose-Bewegung, **kein** neues Animations-Framework):
  1. Himmel: `selliGradient()` (unverändert).
  2. Wolken: 2–3 einfache statische Formen, die per Offset-Loop langsam
     driften.
  3. Pfad: eine geschwungene Linie/Form über die Breite des Headers.
  4. Ziel-Symbol: Herz in warmem, kontrastierendem Ton am Ende des Pfads.
  5. Maskottchen: Position entlang des Pfads interpoliert nach dem
     berechneten Fortschritt (0–100 %), dazu leichte Idle-Bewegung (Wippen).
- Neue statische Illustrations-Assets nötig (Produktion wie gehabt: OpenAI-
  Bildgenerierung + `rembg`-Freistellung über `tools/generate-assets/`):
  Wolken-/Pfad-/Ziel-Grafiken als wiederverwendbare Ebenen, ggf. eine neue
  Maskottchen-Gehpose, falls `mascot_pushing` nicht passt (`mascot_traveling`
  bleibt wie bisher für das separate v3-Distanz-Feature reserviert). Konkrete
  Bild-Assets sind Ausführungsdetail, keine Design-Entscheidung dieser Spec.

## Widget (großes Widget, ersetzt `NextFreeSlotRow`)

- Kein Live-Ticken möglich — Glance-Widgets aktualisieren sich nur beim
  bestehenden 30-Minuten-`WidgetRefreshWorker`-Lauf oder manuellem Refresh.
  Zeigt entsprechend den Stand von der letzten Aktualisierung, nicht live.
- Vereinfachte, nicht-animierte Darstellung: Text ("noch 3 Tage, 4 Std.") +
  ein einzelnes Maskottchen-Icon (gleiches Muster wie die bestehenden
  Widget-Zeilen), **keine** Wanderweg-Grafik, kein Fortschrittsbalken.
- Gleicher Heute-/Leer-Zustand wie im Header, hier rein textuell/Icon-basiert.
- Datenquelle: derselbe `NextSharedEventSelector`, den `WidgetRefreshWorker`
  bereits für die "nächste Wir-Zeit"-Zeile (mittel) berechnet — kein
  zusätzlicher Netzwerk-Request, keine zweite Selector-Instanz nötig, Zeile 3
  nutzt einfach denselben bereits ermittelten Termin.

## Randfälle

- Termin ohne `created` (ICS-Import, alte Daten): Fortschritt 0 %, kein
  Fehler (siehe oben).
- Wechsel der nächsten Wir-Zeit während man hinschaut (Partner verschiebt/
  löscht sie live): der nächste Merge-Refresh liefert automatisch den neuen
  Termin, der Fortschritt berechnet sich für dessen `created`/Start neu —
  kein Sonderfall im Code nötig, da rein aus den aktuellen Selector-Daten
  abgeleitet.

## Zurückgestellt (Backlog, nicht Teil dieser Runde)

- Echte Vektor-/Lottie-Animation (`lottie-compose`) statt geschichteter
  statischer Bilder — als spätere Aufwertung vorgemerkt, falls sich der
  jetzige Ansatz bewährt. Würde einen neuen Animationsproduktions-Workflow
  brauchen, passt nicht zum bisherigen "einmalig erzeugte Standbilder"-Prinzip
  der App.
- Sekündliches statt minütliches Ticken — bewusst nicht gewählt (wirkt
  unruhiger bei mehrtägigen Countdowns, kein technischer oder Akku-Vorteil
  gegenüber minütlich).
- Eigene Tap-Animation beim Antippen des Countdowns — Ausführungsdetail,
  kein Blocker für die erste Version.

## Tests

- Fortschrittsberechnung (reine Funktion): Normalfall, `created` fehlt
  → 0 %, `created == start` → 100 % (keine Division durch 0), `now` vor
  `created` → geklemmt auf 0 %, `now` nach `start` → geklemmt auf 100 %.
- `GoogleCalendarEventMapper`: `created`-Feld wird korrekt aus
  `Event.created` übernommen; ICS-Mapper liefert weiterhin `null`.
- Widget: Zeile 3 zeigt Text/Icon korrekt für vorhandene/leere/heutige
  Wir-Zeit, keine Regression für Zeilen 1/2.
- Header-UI (Compose-Ticking, Illustrationspositionierung) eher Smoke-/
  Gerätetest, wie bei bisherigen Widget-/Header-Änderungen üblich.
