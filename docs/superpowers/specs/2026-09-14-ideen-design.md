# Design: "Ideen" – Ordner mit abhakbaren Punkten für Aktivitäten & Rezepte

Erarbeitet mit Basti am 14.09.2026 über den `superpowers:brainstorming`-Skill.
Zweiter von zwei geplanten neuen Bereichen (erster:
[Kostentracking](2026-09-14-kostentracking-design.md)).

## Ziel

Basti und Melli sammeln gemeinsame Ideen für die Zukunft – Rezepte,
Aktivitäten, Reise-Ideen – in selbst angelegten Ordnern. Jeder Ordner
enthält abhakbare Punkte (Freitext, optional mit Link). Ersetzt keine
bestehende To-Do-App, sondern ist bewusst schlank für genau diesen
Zweck: gemeinsame Vorhaben sammeln und bei Erledigung abhaken.

## Entscheidungen aus dem Brainstorming

- **Navigation:** fünfter Tab, Name **"Ideen"** (statt "Notizen" – drückt
  besser aus, dass es um gemeinsame Vorhaben/Wünsche geht, nicht um
  freie Notizen). Finale Tab-Reihenfolge über beide neuen Features
  hinweg: `Kalender | Ideen | Wir | Kosten | Standort` — **Wir bleibt
  in der Mitte als Startziel**, wie im ursprünglichen Grundgerüst
  festgelegt.
- **Struktur:** genau eine Ebene – Ordner → Punkte, keine Unterordner
  (YAGNI, kein genannter Bedarf).
- **Punkt-Inhalt:** ein Punkttyp, Freitext mit optionalem Link (keine
  zwei getrennten Punkttypen).
- **Link-Vorschau:** automatisch geladen (Seitentitel + Bild), nicht nur
  die rohe URL.
- **Abhaken:** Punkt verschwindet aus der offenen Liste in einen
  aufklappbaren "Erledigt"-Archiv-Bereich (nicht durchgestrichen stehen
  lassen, nicht sofort gelöscht).
- **Bearbeiten/Löschen:** beide dürfen alles bearbeiten/löschen –
  konsistent zum Kostentracking.
- **Benachrichtigung:** ja, bei jedem neuen Punkt (nicht bei leerer
  Ordner-Anlage).

## Architektur & Datenmodell

Gleiches Muster wie Kostentracking/Standort: neue Supabase-Tabellen,
abgesichert über die bestehende Allowlist (`selli_members`/
`is_selli_member()`).

### Tabelle `note_folders`

| Spalte | Typ | Bedeutung |
|---|---|---|
| `id` | uuid, PK | |
| `name` | text | Ordnername, frei vergeben |
| `created_by` | text, check `in ('BASTI','MELLI')` | |
| `created_at` | timestamptz, default `now()` | |

### Tabelle `note_items`

| Spalte | Typ | Bedeutung |
|---|---|---|
| `id` | uuid, PK | |
| `folder_id` | uuid, FK → `note_folders.id`, **`on delete cascade`** | |
| `text` | text | Freitext-Inhalt des Punkts |
| `url` | text, nullable | optionaler Link |
| `preview_title` | text, nullable | beim Anlegen geladener Seitentitel |
| `preview_image_url` | text, nullable | beim Anlegen geladenes Vorschaubild |
| `is_checked` | bool, default `false` | |
| `checked_at` | timestamptz, nullable | |
| `created_by` | text, check `in ('BASTI','MELLI')` | |
| `created_at` | timestamptz, default `now()` | |

### Link-Vorschau: client-seitig, mit Fallback

Beim Anlegen eines Punkts mit URL lädt die App die Seite selbst und
liest `<title>`/`og:title` und `og:image` aus. Neue, kleine Abhängigkeit:
**Jsoup** (leichtgewichtiger HTML-Parser) — die App hat mit dem eigenen
RFC-5545-ICS-Parser schon Präzedenz für "kleine Parsing-Aufgabe selbst
lösen statt schwere Library". Timeout ~5s: schlägt das Laden fehl oder
dauert zu lange, wird der Punkt trotzdem gespeichert, nur ohne Vorschau
(`preview_title`/`preview_image_url` bleiben `null`, UI zeigt dann die
reine URL als Fallback-Link).

### RLS

Beide Tabellen: `select`/`insert`/`update`/`delete` für `authenticated`
über `is_selli_member()` — identisches Muster zu `expenses`/`locations`.
`on delete cascade` auf `note_items.folder_id` erledigt das Aufräumen
serverseitig, wenn ein Ordner gelöscht wird.

### Sync

Kein Realtime-Websocket (wie beim Kostentracking) – Nachladen beim
Tab-Öffnen + Pull-to-Refresh, kein Room, Supabase ist direkt die Quelle
der Wahrheit.

## UI/Screens

**Fünfter Tab "Ideen"** in `SelliBottomBar`/`SelliDestination`, an
zweiter Position (`Kalender | Ideen | Wir | Kosten | Standort`).

**Ebene 1 – Ordner-Übersicht:**
- Liste aller Ordner als Karten: Name + Anzahl offener Punkte (z. B.
  "Rezepte · 4 offen")
- **FAB "+"** → Anlegen-Sheet mit nur einem Namensfeld
- Antippen → Navigation in die Ordner-Detailansicht (Ebene 2)
- Menü/Long-Press pro Ordner-Karte → Umbenennen/Löschen (Löschen mit
  rotem Bestätigungsdialog, der die Gesamtzahl offener + erledigter
  Punkte nennt, die mitgelöscht werden)
- Leerzustand: Maskottchen + "Noch keine Ordner"

**Ebene 2 – Ordner-Detail:**
- Kopfzeile: Ordnername + Zurück
- **Offene Punkte:** Checkbox + Text; hat der Punkt eine URL, darunter
  eine kleine Vorschau-Karte (Bild + Seitentitel, oder nur die URL als
  Fallback bei fehlgeschlagener Vorschau) – antippbar, öffnet den Link
  im Browser. Sehr lange Texte werden mehrzeilig mit Ellipsis gekürzt
  (wie Termin-Titel), sehr lange URLs zeigen nur die Domain.
- **FAB "+"** → Anlegen-Sheet: Textfeld + optionales URL-Feld (kurzer
  Ladezustand während die Vorschau geholt wird)
- Abhaken → Punkt verschwindet sofort aus der offenen Liste
- **Aufklappbarer "Erledigt (N)"-Bereich** unten: archivierte Punkte,
  von dort wieder aufhakbar oder endgültig löschbar
- Antippen des Text-Bereichs (nicht der Link-Vorschau) → Aktionen-Sheet
  (Bearbeiten/Löschen), gleiches Muster wie bei Kosten/Kalender
- Leerzustand: Maskottchen + "Noch keine Punkte"

## Benachrichtigung

Wiederverwendung der bestehenden Wir-Zeit-Benachrichtigungs-
Infrastruktur — neuer Check-Typ im 30-Minuten-Widget-Hintergrund-Job.
Neuer Punkt löst Benachrichtigung an die andere Person aus ("Melli hat
'Pasta Carbonara' zu Rezepte hinzugefügt"), Antippen öffnet direkt den
passenden Ordner (Deep-Link, gleiches Muster wie Wir-Zeit/Kosten). Eine
leere Ordner-Anlage (ohne Punkte) löst **keine** Benachrichtigung aus.

## Randfälle

- **Link-Vorschau schlägt fehl/URL ungültig:** Punkt wird trotzdem
  gespeichert, nur ohne Vorschau-Karte.
- **Ordner löschen mit offenen + erledigten Punkten:** Bestätigungsdialog
  nennt die Gesamtzahl, `on delete cascade` räumt beides weg.
- **Zwei Personen bearbeiten gleichzeitig:** unkritisch, kein gemeinsam
  bearbeitetes Feld, kein Konfliktfall.
- **Kein Supabase-Zugriff:** gleiches Verhalten wie Standort/Kosten –
  Hinweis statt Absturz, bestehendes `isConfigured`-Flag wiederverwendbar.

## Testing

- **Repository (`note_folders`/`note_items`):** Struktur-Tests wo
  möglich, echte RLS-Wirkung nur am Gerät/gegen die echte
  Supabase-Instanz (wie bei Kosten/Standort).
- **Link-Vorschau-Parsing:** reine, isolierte Funktion (HTML → Titel +
  Bild-URL, mit Timeout/Fallback) – unit-testbar mit gespeicherten
  HTML-Test-Fixtures, ohne echte Netzwerkaufrufe im Test.
- **Abhaken/Archivieren-Logik:** Unit-Tests, dass `is_checked`/
  `checked_at` korrekt gesetzt werden und offene/erledigte Listen sich
  korrekt trennen.
- **UI:** kein Compose-UI-Test-Framework im Projekt – manueller
  Gerätetest, Claudian-Verifikation über Diffs + Build/Tests, Basti
  bestätigt final am Gerät.

## Owner-Split (für den Implementierungsplan)

- **Codex:** `data/` (Supabase-Repository für Ordner/Punkte,
  Link-Vorschau-Parsing mit Jsoup), `domain/` (Abhaken/Archivieren-
  Logik), Migration, Tests für beides.
- **Claude:** `ui/ideen/` (neue Screens/Sheets für beide Ebenen),
  `SelliBottomBar`/`SelliDestination`-Erweiterung um den fünften Tab
  samt neuer Reihenfolge, Verdrahtung zur bestehenden
  Benachrichtigungs-Infrastruktur (neuer Check-Typ).

## Bewusst nicht Teil dieses Scopes (YAGNI)

- Keine Unterordner/verschachtelte Struktur.
- Keine getrennten Punkt-Typen (Text vs. Link) – ein Typ mit optionalem
  Link deckt beides ab.
- Kein Realtime-Websocket-Sync.
- Kein Sortieren/Umordnen von Ordnern oder Punkten per Drag & Drop –
  chronologisch (neueste zuerst), wie beim Kostentracking.
- Keine geteilten Berechtigungen pro Ordner (z. B. "nur für mich
  sichtbar") – alles ist für beide sichtbar, wie der Rest der App.
