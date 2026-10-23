# Standort: Normalbetrieb und Live-Freigabe – Design

Stand: 2026-10-23. Baut auf `2026-08-26-grundgeruest-standort-design.md` und der
Sitzungsreparatur (`719c7bc`) auf. Ersetzt dort den Abschnitt „Tracking-Taktung"
(adaptive Stillstand/Bewegung-Taktung entfällt im Normalbetrieb).

## Ziel

Zwei klar erkennbare Modi für die eigene Position:

1. **Normalbetrieb** – immer aktiv, sparsam, regelmäßig.
2. **Live-Freigabe** – ausdrücklich gestartet, befristet (15 Min / 1 Std / 8 Std),
   jederzeit vorzeitig beendbar, danach automatisch zurück in den Normalbetrieb.

Jede Person startet und beendet ausschließlich die eigene Freigabe; beide können
gleichzeitig live teilen. Selli behauptet keine WhatsApp-identische Technik und keine
Ende-zu-Ende-Verschlüsselung: Positionen liegen per TLS übertragen in Supabase und sind
über RLS auf die zwei Konten der Allowlist begrenzt.

## Entscheidung: Technik im Normalbetrieb

**Bestehender Foreground-Service bleibt, aber mit einer einzigen sparsamen Anfrage.**

| Variante | Bewertung |
| --- | --- |
| WorkManager, periodisch 15 min | Mindestintervall 15 min, aber Doze-Wartungsfenster und App-Standby-Buckets verschieben Läufe auf 15 min bis Stunden. Hintergrund-Standortabfragen aus einem Worker bekommen nur gedrosselte bzw. zwischengespeicherte Fixes („wenige Male pro Stunde"). Nicht verlässlich genug für „regelmäßig". |
| AlarmManager + Einzelabfrage | Exakte Alarme brauchen ab Android 14 `SCHEDULE_EXACT_ALARM` (standardmäßig verweigert); inexakte Alarme werden gebündelt. Zusätzlich dieselbe Hintergrund-Drosselung. |
| **Foreground-Service `location`, Priorität BALANCED, 15 min** | Vom Hintergrund-Standort-Limit ausgenommen, behält Netzzugriff auch im Doze, Benachrichtigung existiert bereits (Kanal `LOW`). Kosten entstehen durch die Messung, nicht durch den Dienst selbst; BALANCED nutzt WLAN/Funkzelle statt GPS. |

Werte (Normalbetrieb):

| Parameter | Wert | Begründung |
| --- | --- | --- |
| Priorität | `PRIORITY_BALANCED_POWER_ACCURACY` | Kein GPS-Dauerbetrieb; Genauigkeit typ. Häuserblock (≈ 40–100 m, je nach WLAN-Dichte). |
| Intervall | 15 min | Untere Grenze des gewünschten Bereichs; genug Frische für „wo ist X ungefähr". |
| Mindestabstand Fixes | 10 min | Fremd-Fixes (passiv) lösen nicht öfter Uploads aus. |
| Distanzfilter | 0 m | Auch im Stillstand wird gemessen und hochgeladen, sonst wäre „steht zu Hause" nicht von „Handy aus" zu unterscheiden. |
| Bündelung | keine | Bei 15 min ist Bündeln kaum ein Gewinn, würde aber die Frische halbieren. |
| Upload | jeder Fix | ≈ 4 kleine Upserts pro Stunde. |

Akku-Annahme (nicht gemessen): Größenordnung ≤ 0,5 %/h. Prüfbar am Gerät mit
`adb shell dumpsys location` (aktive Anfrage, Intervall, Priorität) und
`adb shell dumpsys batterystats --charged com.prehmus.selli`.

Betriebssystemgrenzen: Im tiefen Doze (Gerät liegt lange still, Bildschirm aus) kann
Android Fixes auch für Foreground-Services verzögern. Das ist unkritisch, weil die
Position dann tatsächlich unverändert ist – die Anzeige bleibt aber ehrlich: Das
Messalter läuft weiter und kippt nach 45 min auf „veraltet". Manche Hersteller beenden
Foreground-Services trotzdem; vorhanden bleiben `START_STICKY`, `onTaskRemoved` und
der Boot-Receiver.

## Entscheidung: Live-Freigabe

Gleicher Dienst, andere Anfrage. Kein zweiter Dienst, kein zweiter Client.

| Parameter | Wert | Begründung |
| --- | --- | --- |
| Priorität | `PRIORITY_HIGH_ACCURACY` | GPS für Bewegung auf Straßenebene. |
| Messintervall | 10 s | Bei Fußweg ≈ 14 m, bei 50 km/h ≈ 140 m zwischen Messungen – Bewegung bleibt sichtbar. GPS ist unterhalb von ≈ 30 s ohnehin dauerhaft an; 5 s statt 10 s brächte kaum Erkenntnis, aber doppelte Uploads. |
| Schnellster Fix | 5 s | |
| Distanzfilter / Bündelung | 0 m / keine | Zeitnah statt gebündelt. |
| Upload | siehe Regel unten | Begrenzt Funk- und Datenlast im Stillstand. |

Upload-Regel im Live-Modus (reine Funktion, getestet):

1. Noch nichts hochgeladen → hochladen.
2. Letzter Upload ≥ 60 s her → hochladen (Herzschlag, hält „gemessen vor …" frisch).
3. Letzter Upload < 10 s her → nicht hochladen.
4. Genauigkeit unbekannt oder > 100 m → nicht hochladen (ungenaue Fixes zählen nicht als
   Bewegung; der Herzschlag überträgt sie trotzdem, sichtbar als „ungenau").
5. Zurückgelegt ≥ max(20 m, Genauigkeit) → hochladen.
6. Sonst nicht.

Ergebnis: im Auto alle 10 s, zu Fuß ≈ alle 15 s, im Stillstand alle 60 s. Beim Partner
kommt eine Änderung über Realtime binnen Sekunden an; fällt Realtime aus, pollt der
Feed während einer aktiven Live-Freigabe alle 15 s (sonst 60 s).

Akku-Annahme (nicht gemessen): spürbar, Größenordnung 3–8 %/h je nach Gerät, Empfang
und Bewegung (GPS dauerhaft an, Mobilfunk durch häufige Uploads oft aktiv). Datenmenge
grob ≤ 1 MB/h. Deshalb nur ausdrücklich und befristet.

## Zustand und Endzeit

- Quelle der Wahrheit für die **eigene** Freigabe ist ein lokaler Speicher
  (`SharedPreferences`) mit `startedAt` und `endsAt` als absolute Wanduhr-Zeitpunkte.
- Aktiv genau dann, wenn `now < endsAt`. Ein als abgelaufen erkannter Eintrag wird
  gelöscht und kann nicht zurückkehren. Ein Eintrag mit `endsAt > now + 8 h + 1 min`
  (Uhr zurückgestellt) gilt als ungültig und wird ebenfalls gelöscht.
- Der Dienst liest den Zustand bei jedem Start (auch `START_STICKY`-Neustart, Boot,
  `onTaskRemoved`) und prüft die Endzeit bei jedem Fix sowie über einen Ticker
  (spätestens alle 30 s, bzw. genau zur Endzeit). Wird nach Ablauf noch ein Fix
  geliefert, wird zuerst auf Normalbetrieb geschaltet und dann mit Normalbetriebs-
  Regeln hochgeladen.
- Bekannte Grenze: Schläft die CPU im tiefen Doze, kann das *Herunterschalten der
  GPS-Taktung* bis zum nächsten Aufwachen nachlaufen. Die Anzeige ist davon unabhängig,
  weil beide Geräte die absolute Endzeit vergleichen.

## Datenmodell

Migration `supabase/migrations/20261023120000_selli_location_live_share.sql`:

```sql
alter table public.locations add column if not exists live_until timestamptz;
```

- `live_until` = Ende der eigenen Live-Freigabe, `null` = keine. Die Anzeige wertet
  zusätzlich `live_until > now` aus, eine liegengebliebene Endzeit wird also nie
  „wieder aktiv".
- `updated_at` bleibt der **Messzeitpunkt** (`Location.time`), nicht der Upload-Zeitpunkt.
- Bestehende RLS (nur eigene Zeile schreiben, beide lesen) und Grants decken die Spalte ab.

**Rückwirkungsschutz für die Anmeldereparatur:** Der Positions-Upsert enthält
`live_until` nicht. Die Spalte wird ausschließlich über ein separates
`update … set live_until = … where user_id = auth.uid()` geschrieben. Ist die Migration
noch nicht eingespielt, scheitert nur die Live-Kennzeichnung (sichtbarer Fehler in der
Live-Karte); Normalbetrieb, Lesen und Realtime laufen unverändert weiter. Lesen ist
tolerant (`live_until` fehlt → `null`).

Abgleich („gewünscht vs. veröffentlicht"): gewünscht = `endsAt` der aktiven Freigabe
oder `null`; veröffentlicht = zuletzt erfolgreich geschriebener Wert. Der Dienst gleicht
nach Moduswechsel, nach jedem erfolgreichen Positions-Upload und bei
`CloudAccessState.CONNECTED` ab, serialisiert über denselben Mutex wie die Uploads. Der
zu schreibende Wert wird erst *beim Senden* aus dem lokalen Zustand berechnet, damit ein
verspäteter Versuch eine beendete Freigabe nicht wieder setzt. Betrifft das Update keine
Zeile (noch keine Position vorhanden), gilt es als nicht veröffentlicht und wird nach dem
ersten Upsert wiederholt.

## Anzeige

Frische (Messalter, nicht Upload-Alter):

| Lage | Schwelle | Anzeige |
| --- | --- | --- |
| Messung < 2 min | – | „gerade eben" bzw. „vor 40 s" |
| Live aktiv, Messung ≥ 2 min | veraltet | „Live-Signal veraltet · vor 6 Min." |
| Normal, Messung < 45 min (3 Intervalle) | aktuell | „vor 20 Min." |
| Normal, Messung ≥ 45 min | veraltet | „veraltet · vor 2 Std." |

Ungenau: Genauigkeit > 100 m → Zusatz „ungenau (± 300 m)" und Genauigkeitskreis.

Karte: vorhandene Avatar-Pins, Personenfarben, Anker 0,92, lineare 1,2-s-Bewegung.
Live-Personen erhalten einen „LIVE"-Hinweis am Marker und eine Zeile in der
Live-Karte unten: „Melli teilt live · noch 52 Min. · gemessen vor 12 s".

Eigene Steuerung (Bedienprinzip WhatsApp): Knopf „Live-Standort teilen" → Auswahl
15 Min / 1 Std / 8 Std mit Akku-Hinweis → aktive Karte mit Restzeit, Endzeit und
„Beenden". Die Dienst-Benachrichtigung zeigt im Live-Modus Restzeit/Endzeit und eine
Aktion „Beenden".

Berechtigungen: Ohne genaue Standortberechtigung ist der Start gesperrt (Hinweis).
Ohne „Immer erlauben" läuft eine im Vordergrund gestartete Freigabe bei gesperrtem
Bildschirm weiter; ein Neustart des Dienstes aus dem Hintergrund ist dann jedoch nicht
erlaubt – die Karte weist darauf hin. Der Dienststart fängt
`SecurityException`/`ForegroundServiceStartNotAllowedException` ab, statt abzustürzen.

## Nicht Teil dieser Änderung

- Keine Historie/Spur, keine Distanzanzeige, kein Ein/Aus-Schalter für den Normalbetrieb.
- Keine Push-Benachrichtigung an den Partner beim Start einer Freigabe.
- Gerätetests mit beiden Konten und gesperrtem Bildschirm stehen aus.
