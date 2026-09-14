# Design: Kostentracking

Erarbeitet mit Basti am 14.09.2026 über den `superpowers:brainstorming`-Skill.
Erster von zwei geplanten neuen Bereichen (zweiter: "Ideen" – Aktivitäten/
Rezepte, eigene Spec folgt separat).

> **Nachtrag nach dem zweiten Brainstorming:** Die Tab-Reihenfolge unten
> („vierter Tab") ist durch das zweite Feature überholt. Finale Reihenfolge:
> `Kalender | Ideen | Wir | Kosten | Standort` — Kosten ist damit der
> **vierte von fünf** Tabs, nicht der vierte von vieren. Inhaltlich ändert
> das nichts an diesem Feature.

## Ziel

Basti und Melli teilen sich gemeinsame Ausgaben (Einkauf, Restaurant, Tanken,
Geschenke, Abos – unstrukturiert, keine Kategorien). Selli soll den laufenden
Saldo ("wer schuldet wem wie viel") jederzeit zeigen und einen manuellen
Ausgleich ermöglichen. **Bewusst keine Banking-Anbindung** – reines,
einfaches Erfassen und Aufteilen, keine echten Zahlungen über die App.

## Entscheidungen aus dem Brainstorming

- **Umfang:** alle gemeinsamen Ausgaben unstrukturiert, keine Kategorien.
- **Aufteilung:** immer fest 50/50, kein Sonderfall pro Ausgabe (YAGNI –
  kann bei echtem Bedarf später ergänzt werden).
- **Ausgleich:** laufender Saldo, manuell per Tap auf "Ausgleichen" –
  sofort wirksam (Vertrauensbasis wie beim bestehenden
  "Wir-Zeit"-/Löschungs-Feature), kein Bestätigungs-Handshake.
- **Navigation:** neuer, vierter, gleichwertiger Tab: `Kalender | Wir |
  Kosten | Standort`.
- **Bearbeiten/Löschen:** beide dürfen jede Ausgabe bearbeiten/löschen,
  nicht nur die eigene.
- **Benachrichtigung:** ja, bei jeder neuen Ausgabe (wiederverwendet die
  bestehende Wir-Zeit-Benachrichtigungs-Infrastruktur).
- **Sync:** kein Realtime-Websocket (anders als beim Standort-Feature) –
  Nachladen beim Öffnen des Tabs + Pull-to-Refresh reicht, da Kosten nicht
  sekundengenau aktuell sein müssen.
- **Währung:** EUR, keine Mehrwährungsunterstützung (kein Bedarf).

## Architektur & Datenmodell

Gleiches Muster wie das bestehende Standort-Feature: neue Supabase-Tabellen,
abgesichert über die schon vorhandene Allowlist (`public.selli_members` +
`public.is_selli_member()`, security definer). Kein neuer
Auth-Mechanismus, keine neue Infrastruktur.

### Tabelle `expenses`

| Spalte | Typ | Bedeutung |
|---|---|---|
| `id` | uuid, PK | |
| `amount` | numeric(10,2) | positiv, EUR-Cents – Validierung in der UI (Betragsfeld) |
| `description` | text | Freitext |
| `paid_by` | text, check `in ('BASTI','MELLI')` | wer hat bezahlt |
| `spent_at` | date | Datum der Ausgabe, manuell wählbar (Default: heute) |
| `created_at` | timestamptz, default `now()` | wann eingetragen |
| `settlement_id` | uuid, nullable, FK → `settlements.id` | `null` = noch offen |

### Tabelle `settlements`

| Spalte | Typ | Bedeutung |
|---|---|---|
| `id` | uuid, PK | |
| `settled_by` | text, check `in ('BASTI','MELLI')` | wer hat ausgeglichen |
| `settled_at` | timestamptz, default `now()` | |
| `balance_snapshot` | numeric(10,2) | Saldo zum Zeitpunkt des Ausgleichs, signiert (siehe Saldo-Berechnung) |

**Warum `settlement_id` statt Datums-Cutoff:** Beim Ausgleichen werden alle
aktuell offenen (`settlement_id IS NULL`) Ausgaben auf die neue
Settlement-`id` gestempelt. Robust gegen nachträglich zurückdatierte
Ausgaben – ein reiner Datumsvergleich könnte durch Backdating verrutschen.

### Saldo-Berechnung (reine, testbare Funktion — Codex-Territorium)

Analog zu `LocationCadence.kt`/`WirZeitCountdown`: keine Android-Abhängigkeit,
gut isoliert testbar.

```
saldo = (Summe amount, wo paid_by = BASTI AND settlement_id IS NULL) / 2
      − (Summe amount, wo paid_by = MELLI AND settlement_id IS NULL) / 2
```

Vorzeichenkonvention: `saldo > 0` → Melli schuldet Basti `saldo` €.
`saldo < 0` → Basti schuldet Melli `abs(saldo)` €. `saldo == 0` →
ausgeglichen.

### RLS

- `expenses`: `select`/`insert`/`update`/`delete` für `authenticated`,
  Policy-Bedingung `is_selli_member()` (gleiches Muster wie `locations`).
- `settlements`: nur `select`/`insert` für `authenticated` (Settlements
  werden nie nachträglich bearbeitet, nur neu angelegt).
- `selli_members` bleibt unangetastet, keine neuen Grants dort.

### Client-seitiges Caching

Kein Room nötig — Supabase ist direkt die Quelle der Wahrheit (anders als
die lokalen Kalender-Overrides). ViewModel hält den geladenen Zustand,
Refresh beim Tab-Öffnen + Pull-to-Refresh, kein Dauer-Abo.

## UI/Screens

**Neuer vierter Tab "Kosten"** in `SelliBottomBar`/`SelliDestination`
(Icon aus `material-icons-core` — App bindet bewusst kein
`material-icons-extended` ein, beim Umsetzen gegen tatsächlich verfügbare
Icons prüfen, z. B. `Icons.Default.AttachMoney` falls vorhanden, sonst
Alternative aus dem Core-Set).

Aufbau von oben nach unten:

1. **Saldo-Karte** im bestehenden Look (abgerundete Karte, Personenfarben):
   z. B. "Melli schuldet dir 41,25 €" in der Farbe der schuldenden Person,
   oder "Ausgeglichen 🎉" bei Saldo 0. Direkt darunter ein
   **"Ausgleichen"**-Button, ausgeblendet wenn Saldo bereits 0 (kein
   Sonderfall im Code nötig, da der Button dann schlicht nicht existiert).
2. **FAB "+"** unten rechts (wie im Kalender) → Anlegen-Sheet: Betrag,
   Beschreibung, Datum (Default heute), "Bezahlt von" (Default: eigene
   Person, umschaltbar für stellvertretendes Eintragen).
3. **Liste aller Ausgaben**, neueste oben, Kartenstil analog
   `SharedEventCard` (Betrag, Beschreibung, Datum, Personen-Pill für
   "bezahlt von"). Nach jedem Ausgleich erscheint eine dezente
   Trennzeile in der Liste: „— Ausgeglichen am DD.MM., Saldo war X € —"
   (wie ein Kontoauszug-Umbruch).
4. **Antippen einer Ausgabe** öffnet ein Aktionen-Sheet (Bearbeiten/
   Löschen), gleiches Muster wie `EventActionsSheet`, nur mit
   Kosten-Feldern.
5. **Leerzustand** (keine Ausgaben): Maskottchen + Hinweistext, analog
   `NoSharedEventsHint`.

## Benachrichtigung

Wiederverwendung der bestehenden Wir-Zeit-Benachrichtigungs-Infrastruktur
(periodischer Check im 30-Minuten-Widget-Hintergrund-Job) — neuer
Check-Typ statt neuer Hintergrund-Mechanismus. Neue Ausgabe löst
Benachrichtigung an die andere Person aus ("Basti hat 24,50 € für
'Einkauf' eingetragen"), Antippen öffnet direkt den Kosten-Tab (gleiches
Deep-Link-Muster wie bei Wir-Zeit-Terminen).

## Randfälle

- **Betrag-Validierung:** nur positive Beträge, max. 2 Nachkommastellen,
  kein Freitext-Betrag.
- **Bearbeiten/Löschen einer bereits abgerechneten Ausgabe:** bleibt
  möglich (beide dürfen alles bearbeiten), ändert aber den historischen
  `balance_snapshot` des Settlements **nicht** nachträglich. UI zeigt
  beim Bearbeiten einen Hinweis „Teil einer bereits ausgeglichenen
  Abrechnung" statt es zu verstecken oder zu verbieten.
- **Ausgleichen ohne offene Ausgaben:** Button ist dann ausgeblendet
  (siehe UI-Abschnitt), kein Sonderfall im Code.
- **Kein Supabase-Zugriff (offline/nicht konfiguriert):** gleiches
  Verhalten wie im Standort-Tab — Hinweis statt Absturz, bestehendes
  `isConfigured`-Flag wiederverwendbar (Supabase-Projekt existiert schon).
- **Zwei gleichzeitige Einträge:** unkritisch, jede Ausgabe ist ein
  eigenständiger Datensatz, keine Konfliktmöglichkeit.

## Testing

- **Saldo-Berechnung:** Unit-Tests mit verschiedenen Kombinationen (nur
  Basti zahlt, nur Melli, gemischt, nach Ausgleich zählen nur neue
  Ausgaben) — analog zum Testmuster für `LocationCadence`/
  `WirZeitCountdown`.
- **Ausgleichen-Logik:** Test, dass alle offenen Einträge korrekt auf die
  neue `settlement_id` gestempelt werden und `balance_snapshot` stimmt.
- **Repository/RLS:** Struktur-Tests wo möglich, echte RLS-Wirkung nur am
  Gerät/gegen die echte Supabase-Instanz prüfbar (kein Mock-Postgres im
  Projekt, wie beim Standort-Feature).
- **UI:** kein Compose-UI-Test-Framework im Projekt etabliert — bleibt
  wie gewohnt manueller Gerätetest, Claudian-Verifikation über Diffs +
  Build/Tests, Basti bestätigt final am Gerät.

## Owner-Split (für den Implementierungsplan)

- **Codex:** `data/` (Supabase-Repository für Ausgaben/Settlements),
  `domain/` (Saldo-Berechnung, Settlement-Logik), Migration, Tests für
  beides.
- **Claude:** `ui/expenses/` (neue Screens/Sheets), `SelliBottomBar`/
  `SelliDestination`-Erweiterung um den vierten Tab, Verdrahtung zur
  bestehenden Benachrichtigungs-Infrastruktur (neuer Check-Typ, Owner
  je nachdem in welcher Datei dieser Check aktuell liegt — beim
  Implementierungsplan gegen `LSP`/bestehenden Code prüfen).

## Bewusst nicht Teil dieses Scopes (YAGNI)

- Keine Kategorien/Tags für Ausgaben.
- Kein individuelles Aufteilungsverhältnis pro Ausgabe (immer 50/50).
- Keine Mehrwährungsunterstützung.
- Keine Banking-/Zahlungs-Anbindung jeglicher Art.
- Kein Bestätigungs-Handshake beim Ausgleichen.
- Kein Realtime-Websocket-Sync.
