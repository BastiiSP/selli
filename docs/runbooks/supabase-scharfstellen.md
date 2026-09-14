# Runbook: Supabase für das Standort-Feature scharfstellen

Erstellt 26.08.2026. Führt von „kein Supabase-Konto" bis „beide Marker bewegen sich
live auf der Karte". Jeder Schritt hat eine **Prüfung** — erst wenn die stimmt, weiter.
Wiederverwendbar (neues Gerät, neuer Rechner, Projekt neu aufsetzen).

**Enthält keine Secrets.** Alle echten Werte (Client-ID, Keys, E-Mail-Adressen) stehen
in `local.properties` bzw. werden direkt im Dashboard eingetragen — nie in diese Datei,
nie ins Repo, nie in einen Chat.

Referenz: `docs/superpowers/specs/2026-08-26-grundgeruest-standort-design.md`
(Teilaufgabe 3), Migration: `supabase/migrations/20260826120000_selli_locations.sql`.

**Vollständig durchlaufen und bestätigt am 14.09.2026** (Phasen 3–8, live bei Basti).
D2 trat exakt wie hier beschrieben auf — gemerkte Sitzung ohne Token, behoben über
„Konto wechseln → neu anmelden". Phase 8.4/8.5 (Karte, Live-Bewegung) laufen.

---

## Phase 0 — Vorbereitung

### [x] 0.1 Werte bereitlegen

| Was | Wo es herkommt |
|---|---|
| Web-OAuth-Client-ID | `local.properties` → `selli.googleServerClientId` (endet auf `.apps.googleusercontent.com`) |
| Bastis Google-Adresse | die, mit der er sich **in der App** anmeldet |
| Mellis Google-Adresse | dieselbe Regel |
| SHA-1 des Debug-Keystores | siehe 0.2 |

Wichtig: die Allowlist wird gegen das `email`-Claim des Google-ID-Tokens geprüft. Das ist
die Adresse des Google-Kontos, das in der App ausgewählt wird — nicht irgendein Alias.

### [x] 0.2 SHA-1 des Debug-Keystores auslesen

```bash
keytool -list -v -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -storepass android -keypass android | grep SHA1
```

**Prüfung:** eine Zeile `SHA1: XX:XX:…` (20 Byte-Paare). Kommt „keystore file does not
exist": einmal `./gradlew assembleDebug` laufen lassen, dann erneut.

Auf diesem Mac (26.08.2026):
`8A:B2:BB:7A:19:16:0F:DD:5F:C1:9F:AC:AA:E6:D1:D5:B7:21:1C:70`

Beide Handys bekommen dieselbe, hier gebaute APK → **ein** Fingerprint genügt. Wird
irgendwann auf einem anderen Rechner oder mit Release-Keystore gebaut, muss dessen
SHA-1 zusätzlich in den Maps-Key.

### [x] 0.3 Supabase-MCP authentifizieren (empfohlen)

Mit MCP fährt Claude Phase 2 und 3 komplett selbst (`apply_migration`, `execute_sql`,
`get_advisors`) und liest URL und Key direkt aus (`get_project_url`,
`get_publishable_keys`). Du klickst dann nur noch Phase 1, 4 und 5.

**Der gehostete Server ist aktiv** — Stand 26.08.2026 der von Supabase empfohlene Weg,
nicht abgeschaltet:

```bash
curl -s -i -X POST https://mcp.supabase.com/mcp \
  -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"probe","version":"1"}}}' \
  | grep -E "^HTTP|www-authenticate"
```

**Erwartet:** `HTTP/2 401` **plus** ein `www-authenticate: Bearer …
resource_metadata="https://mcp.supabase.com/.well-known/oauth-protected-resource/mcp"`.
Das ist ein intakter OAuth-2.1-geschützter MCP-Server, kein Ausfall. Ein `401` **ohne**
`www-authenticate`, ein `404` oder ein Timeout wären das Alarmzeichen.

Das Plugin `supabase@claude-plugins-official` bringt die Serverdefinition schon mit
(`agents/claude/.mcp.json` → `type: http`, `url: https://mcp.supabase.com/mcp`) und ist
in `~/.claude/settings.json` aktiviert. Es fehlt **nur** das OAuth-Token:

In Claude Code `/mcp` → **supabase** → *Authenticate* → im Browser freigeben.

**Prüfung:** `/mcp` zeigt supabase als *connected*. Danach findet Claude per `ToolSearch`
Tools wie `list_projects`, `apply_migration`, `execute_sql`, `get_advisors`.

Der Server fragt diese Scopes an: `projects:read/write`, `database:read/write`,
`analytics:read`, `storage:*`, `edge_functions:*`, `secrets:read`. Das ist **kontoweiter**
Zugriff, nicht projektbezogen — also auch auf alle anderen Projekte der Organisation.
Bewusst so entschieden am 26.08.2026 (Konto/Org SPTech). Wer das enger will, nimmt
statt OAuth den lokalen Server mit `--project-ref <ref>` (Tabelle oben).

**Fällt MCP aus**, gibt es zwei Auswege — beide optional, die Anleitung läuft ohne:

| Weg | Befehl | Wofür |
|---|---|---|
| Lokaler MCP-Server (npm, aktiv gepflegt: `0.11.0`, 20.08.2026) | `claude mcp add supabase -- npx -y @supabase/mcp-server-supabase@latest --access-token <PAT> --project-ref <ref>` | braucht einen Personal Access Token aus dem Dashboard; erlaubt `--read-only` und Projekt-Scoping |
| Nur SQL | Dashboard → SQL Editor | reicht für alles in dieser Anleitung |

Die Supabase **CLI** ist hier nicht installiert und wird nicht gebraucht — die Migration
ist eine einzelne SQL-Datei, `supabase db push` wäre nur der Umweg.

#### Fehler: `{"message":"Unrecognized client_id"}` im Browser

Aufgetreten am 26.08.2026. **Nicht** der Server ist defekt, sondern
die lokal gecachte OAuth-Client-Registrierung: Claude Code hat sich irgendwann per
Dynamic Client Registration angemeldet, Supabase kennt diesen Client nicht mehr, und der
kaputte Cache wird bei jedem Versuch erneut benutzt. Neu klicken hilft deshalb nie.

Abgrenzung, falls unklar (beide Prüfungen dauern Sekunden):

```bash
# 1) Registrierung serverseitig möglich? -> 400 mit Feldnamen = ja, Endpunkt lebt
curl -s -X POST https://api.supabase.com/platform/oauth/apps/register \
  -H "Content-Type: application/json" -d '{}' --max-time 12
# 2) Kennt der Server die client_id aus der Fehler-URL? -> 422 = nein, Cache ist schuld
curl -s -o /dev/null -w "%{http_code}\n" \
  "https://api.supabase.com/v1/oauth/authorize?response_type=code&client_id=<AUS-DER-URL>&redirect_uri=http%3A%2F%2Flocalhost%3A1234%2Fcallback&code_challenge=abc&code_challenge_method=S256"
```

**Fix** — Credentials löschen, dann neu anmelden:

```bash
claude mcp list | grep -i supabase          # Servername ermitteln
claude mcp logout "plugin:supabase:supabase"
claude mcp login  "plugin:supabase:supabase"   # --no-browser für SSH/headless
```

Der Server heißt **`plugin:supabase:supabase`**, nicht `supabase` — `claude mcp get
supabase` läuft ins Leere und ist kein Hinweis auf ein Problem.

---

## Phase 1 — Supabase-Projekt anlegen

### [x] 1.1 Projekt erstellen

<https://supabase.com/dashboard> → **New project**

| Feld | Wert |
|---|---|
| Organization | **SPTech** (Entscheidung 26.08.2026) |
| Name | `selli` |
| Region | **Central EU (Frankfurt) / `eu-central-1`** |
| Plan | Free |
| Database Password | generieren, in den Passwortmanager — wird für diese App nie gebraucht, aber für `psql`/CLI |

**Prüfung:** Projekt steht nach 1–3 Minuten auf *Active/Healthy* (nicht mehr
*Setting up*). Project Settings → General zeigt eine **Reference ID**.

Stand 26.08.2026: Ref `pfccqmnrtgkpbciyjhah`, `eu-central-1`, Postgres 17, ACTIVE_HEALTHY.
(Die Ref ist kein Geheimnis — sie steckt in jeder Project-URL.)

### [x] 1.2 Project URL und Client-Key notieren

Project Settings → **API Keys**:

- **Project URL** → `https://<ref>.supabase.co`
- **Publishable key** → `sb_publishable_…`

**Prüfung:** URL enthält genau die Reference ID aus 1.1.

> **Beides kann vorhanden sein — nachsehen, nicht annehmen.** Verbreitet ist die Angabe,
> neu angelegte Projekte bekämen seit 01.11.2025 keinen `anon`-Key mehr. Für das am
> 26.08.2026 angelegte Selli-Projekt stimmt das **nicht**: `get_publishable_keys` liefert
> sowohl einen Legacy-`anon`-Key (`type: legacy`, `disabled: false`) als der
> Publishable Key. Beide funktionieren; genommen wurde der Publishable Key, weil die
> Legacy-JWT-Keys Ende 2026 auslaufen.

Der Key gehört trotz seines Namens in **`selli.supabaseAnonKey`** — das Feld heißt
historisch so, transportiert aber einfach „der Client-Key". Kein Codeeingriff nötig
(Umbenennen wäre Gradle-/`data/`-Territorium, also Codex, und bringt nichts).

**Warum das mit supabase-kt 3.1.4 trägt** (am entpackten AAR verifiziert, 26.08.2026):
`KtorSupabaseHttpClient` setzt den Key ausschließlich als `apikey`-Header, nirgends wird
er als JWT geparst. `AccessTokenKt.resolveAccessToken` fällt ohne Session auf
`getSupabaseKey()` zurück — `Authorization: Bearer` und `apikey` tragen dann **denselben**
Wert, und genau das erlaubt Supabase für Publishable Keys ausdrücklich („You cannot send
a publishable key in the `Authorization: Bearer` header, except if the value exactly
equals the `apikey` header"). Nach dem Anmelden steht im `Authorization`-Header das
Nutzer-JWT und im `apikey`-Header der Publishable Key — die Standardkombination.

**Der `sb_secret_…`-Key kommt nie in die App.** Die Absicherung macht RLS.

### [x] 1.3 Key vorab gegen die API testen

Bevor irgendetwas gebaut wird — beweisen, dass der Key vom Gateway akzeptiert wird. Der
Aufruf benutzt genau das Header-Muster von supabase-kt ohne Session (`apikey` und
`Authorization: Bearer` mit **demselben** Wert):

```bash
cd ~/Workspace/selli
URL=$(grep '^selli.supabaseUrl=' local.properties | cut -d= -f2-)
KEY=$(grep '^selli.supabaseAnonKey=' local.properties | cut -d= -f2-)
curl -s -H "apikey: $KEY" -H "Authorization: Bearer $KEY" "$URL/rest/v1/locations?select=user_id"; echo
curl -s -o /dev/null -w "auth/v1/settings: %{http_code}\n" -H "apikey: $KEY" "$URL/auth/v1/settings"
```

**Der HTTP-Statuscode taugt hier nicht als Kriterium** — gültiger und ungültiger Key
liefern beide `401`. Entscheidend ist der Body:

| Antwort auf `/rest/v1/locations` | Bedeutung |
|---|---|
| `{"code":"42501",…,"message":"permission denied for table locations"}` | **richtig.** Key akzeptiert, zur Rolle `anon` aufgelöst, PostgREST hat die Anfrage ausgeführt und RLS/Grants greifen wie geplant (`anon` hat auf `locations` bewusst kein `SELECT`) |
| `{"message":"Invalid API key","hint":…}` | Key falsch kopiert oder deaktiviert |
| `[]` | Key gültig, aber `anon` hätte `SELECT` — dann wurde zu breit gegrantet, siehe 2.3 |

`auth/v1/settings` muss `200` liefern. Kommt dort `401`, passt die URL nicht zum Key.

> Nebenbefund: `GET /rest/v1/` (die Wurzel, OpenAPI-Spec) antwortet auch mit gültigem
> Key `401`. Das ist kein Fehler und kein Prüfkriterium — immer gegen eine konkrete
> Tabelle testen.

---

## Phase 2 — Migration ausführen

### [x] 2.1 SQL einspielen

SQL Editor → **New query** → kompletten Inhalt von
`supabase/migrations/20260826120000_selli_locations.sql` einfügen → **Run**.

```bash
pbcopy < supabase/migrations/20260826120000_selli_locations.sql   # in die Zwischenablage
```

Die Migration ist idempotent (`create table if not exists`, `drop policy if exists`) —
mehrfaches Ausführen ist unschädlich.

**Prüfung:** „Success. No rows returned".

### [x] 2.2 Objekte verifizieren (eine Abfrage, eine Zeile)

```sql
select
  (select count(*) from pg_tables
     where schemaname = 'public'
       and tablename in ('locations','selli_members'))                     as tabellen,
  (select relrowsecurity from pg_class
     where oid = 'public.locations'::regclass)                             as rls_locations,
  (select relrowsecurity from pg_class
     where oid = 'public.selli_members'::regclass)                         as rls_members,
  (select count(*) from pg_policies
     where schemaname='public' and tablename='locations')                  as policies_locations,
  (select count(*) from pg_policies
     where schemaname='public' and tablename='selli_members')              as policies_members,
  (select relreplident from pg_class
     where oid = 'public.locations'::regclass)                             as replica_identity,
  (select count(*) from pg_publication_tables
     where pubname='supabase_realtime'
       and schemaname='public' and tablename='locations')                  as realtime,
  (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
     where n.nspname='public' and p.proname='is_selli_member'
       and p.prosecdef)                                                    as funktion_secdef;
```

**Erwartet — exakt so:**

| Spalte | Soll | Bedeutung wenn falsch |
|---|---|---|
| `tabellen` | `2` | Migration nicht (vollständig) gelaufen |
| `rls_locations` | `true` | ohne RLS wäre die Tabelle offen — sofort stoppen |
| `rls_members` | `true` | dito, Allowlist wäre lesbar |
| `policies_locations` | `3` | select/insert/update — bei `<3` fehlt Schreiben oder Lesen |
| `policies_members` | `0` | **muss 0 sein**, das ist Absicht |
| `replica_identity` | `f` | ohne `full` liefert Realtime keine alten Werte |
| `realtime` | `1` | ohne Publication kein `postgres_changes` → nur 60-s-Polling |
| `funktion_secdef` | `1` | ohne `security definer` kann die Policy die Allowlist nicht lesen |

### [x] 2.3 Data-API-Rechte prüfen

Zweites, von RLS **unabhängiges** Tor: RLS regelt *welche Zeilen*, Grants regeln, ob die
Tabelle über PostgREST überhaupt erreichbar ist. Beides muss stimmen.

> Am 26.08.2026 ist genau das eingetreten: nach der Migration hatte `authenticated` auf
> `public.locations` nur `REFERENCES, TRIGGER, TRUNCATE` — **kein** SELECT/INSERT/UPDATE.
> Die Karte wäre leer geblieben, obwohl Policies und Allowlist korrekt waren. Der
> passende `grant` steht seitdem **in der Migrationsdatei**, ein Neuaufsetzen läuft also
> nicht mehr in diese Falle. Die Prüfung bleibt trotzdem drin.

```sql
select table_name, grantee,
       string_agg(privilege_type, ', ' order by privilege_type) as rechte
from information_schema.role_table_grants
where table_schema = 'public'
  and table_name in ('locations','selli_members')
  and grantee in ('anon','authenticated')
  and privilege_type in ('SELECT','INSERT','UPDATE','DELETE')
group by table_name, grantee
order by table_name, grantee;
```

**Erwartet: genau eine Zeile** — `locations` / `authenticated` / `INSERT, SELECT, UPDATE`.
Nichts für `anon`, nichts auf `selli_members`, nirgends `DELETE`.

Fehlt sie: `grant select, insert, update on public.locations to authenticated;`

### [x] 2.4 Advisors laufen lassen

Über MCP `get_advisors` (type `security`) oder Dashboard → Advisors. Drei Meldungen sind
**erwartet und kein Fehler**:

| Meldung | Bewertung |
|---|---|
| `rls_enabled_no_policy` auf `selli_members` (INFO) | Absicht — nur `is_selli_member()` liest die Allowlist |
| `authenticated_security_definer_function_executable` für `is_selli_member` (WARN) | unvermeidbar: RLS-Policy-Ausdrücke werden mit den Rechten der aufrufenden Rolle ausgewertet, `authenticated` **muss** EXECUTE haben |
| beide Meldungen zu `public.rls_auto_enable` (WARN) | gehört Supabase, nicht Selli: ein Event-Trigger (`returns event_trigger`), der auf neuen Tabellen in `public` automatisch RLS einschaltet. Als Event-Trigger-Funktion praktisch nicht per RPC aufrufbar — **nicht anfassen** |

Behoben wurde dagegen `anon_security_definer_function_executable` für
`is_selli_member` — Postgres vergibt EXECUTE auf neue Funktionen per Default an `PUBLIC`,
damit stand `/rest/v1/rpc/is_selli_member` auch unangemeldet offen. Der `revoke`/`grant`
steht jetzt in der Migration.

### [x] 2.5 Die ganze Kette in einer Abfrage beweisen

Stärker als alle Struktur-Checks: Rolle und JWT simulieren und wirklich lesen. Läuft in
einer Transaktion und wird zurückgerollt, hinterlässt also nichts.

```sql
begin;
insert into public.selli_members (email, person) values ('probe@example.com','BASTI');
select set_config('request.jwt.claims', '{"role":"authenticated","email":"probe@example.com"}', true);
set local role authenticated;
select public.is_selli_member() as gate,
       (select count(*) from public.locations) as sichtbare_zeilen;
rollback;
```

**Erwartet:** `gate = true` und **kein** Fehler. Damit sind Grant, Policy,
`security definer`-Funktion und Allowlist-Logik in einem Durchgang bewiesen.
`sichtbare_zeilen` ist `0`, solange noch keine Position hochgeladen wurde.

Und die Gegenprobe, dass `anon` ausgeschlossen ist:

```sql
begin; set local role anon; select public.is_selli_member(); rollback;
```

**Erwartet:** Fehler `42501 permission denied for function is_selli_member`.

---

## Phase 3 — Allowlist füllen

> **Die wahrscheinlichste Stolperfalle des ganzen Vorgangs.** Ohne diese zwei Zeilen
> blockiert RLS alles, und zwar lautlos: die Karte bleibt leer, es kommt keine
> Fehlermeldung.

### [x] 3.1 Die zwei echten Adressen eintragen

```sql
insert into public.selli_members (email, person) values
  ('BASTIS-ECHTE-ADRESSE', 'BASTI'),
  ('MELLIS-ECHTE-ADRESSE', 'MELLI')
on conflict (email) do update set person = excluded.person;
```

### [x] 3.2 Inhalt prüfen — inklusive Tippfehler-Falle

```sql
select person, email, length(email) as zeichen from public.selli_members order by person;
```

**Erwartet:** genau zwei Zeilen, `BASTI` und `MELLI`, und `zeichen` passt zur
tatsächlichen Adresslänge. Ist `zeichen` um 1–2 zu groß → mitkopiertes Leerzeichen:

```sql
update public.selli_members set email = trim(email);
```

### [x] 3.3 Das RLS-Gate echt testen

`select public.is_selli_member();` **allein liefert im SQL-Editor `false`** — dort liegt
kein Nutzer-JWT an. Das ist erwartet und **kein** Fehler. Richtig getestet wird so
(simuliert das JWT einer Person):

```sql
select public.is_selli_member() as darf_zugreifen
from (select set_config('request.jwt.claims', '{"email":"BASTIS-ECHTE-ADRESSE"}', true)) as _;
```

**Erwartet:** `true`. Dasselbe mit Mellis Adresse → `true`. Und als Gegenprobe:

```sql
select public.is_selli_member() as darf_zugreifen
from (select set_config('request.jwt.claims', '{"email":"fremder@example.com"}', true)) as _;
```

**Erwartet:** `false`. Kommt hier `true` heraus, ist die Absicherung kaputt — stoppen.

---

## Phase 4 — Google-Provider in Supabase

### [x] 4.1 Provider aktivieren

Authentication → **Sign In / Providers** → **Google**:

| Feld | Wert |
|---|---|
| Enable Sign in with Google | **an** |
| Client IDs / Authorized Client IDs | Wert von `selli.googleServerClientId` |
| Client Secret | **leer lassen** — für ID-Token-Anmeldung nicht nötig |
| Skip nonce checks | an (empfohlen, siehe unten) |

Zu *Skip nonce checks*: die App baut ihr Google-Credential ohne Nonce
(`GetGoogleIdOption.Builder().setServerClientId(...)`, kein `.setNonce()`), das ID-Token
enthält also kein `nonce`-Claim. Der Schalter ist die dokumentierte Einstellung für
genau diesen Fall und kann hier nichts kaputt machen.

**Beobachtung 14.09.2026:** Im echten Projekt stand der Schalter zu diesem Zeitpunkt
**aus**, die Anmeldung lief trotzdem fehlerfrei (`Supabase-Realtime: Connected to
realtime websocket!`). Vermutlich prüft Supabase den Nonce nur, wenn das Token
überhaupt einen mitbringt — ohne `nonce`-Claim gibt es nichts zu vergleichen. Die
Empfehlung "an" bleibt trotzdem sinnvoll (spart eine mögliche Fehlerquelle), ist aber
laut diesem Befund keine Voraussetzung.

Zur Client-ID: es muss die **Web**-OAuth-Client-ID sein (die, die
`setServerClientId(...)` bekommt). Steht dort eine Android-Client-ID, passt das
`aud`-Claim des Tokens nicht und die Anmeldung scheitert.

### [x] 4.2 Prüfen — ohne Dashboard, direkt gegen die API

```bash
cd ~/Workspace/selli
URL=$(grep '^selli.supabaseUrl=' local.properties | cut -d= -f2-)
KEY=$(grep '^selli.supabaseAnonKey=' local.properties | cut -d= -f2-)
curl -s "$URL/auth/v1/settings" -H "apikey: $KEY" | python3 -m json.tool | grep -i google
```

(Setzt Phase 6 voraus — sonst die Werte einmalig direkt in die Shell-Variablen setzen.)

**Erwartet:** `"google": true`.

### [x] 4.3 Prüfen, dass der ID-Token-Endpunkt scharf ist

```bash
curl -s -X POST "$URL/auth/v1/token?grant_type=id_token" \
  -H "apikey: $KEY" -H "Content-Type: application/json" \
  -d '{"provider":"google","id_token":"absichtlich-kaputt"}'
```

| Antwort | Bedeutung |
|---|---|
| Meldung über ein ungültiges/nicht parsebares Token | **richtig** — Provider ist aktiv und verarbeitet ID-Tokens |
| `Unsupported provider: provider is not enabled` | 4.1 nicht gespeichert |
| `404` | falsche Project URL |

### [x] 4.4 Optional: Angriffsfläche verkleinern

Authentication → Sign In / Providers → **Email** deaktivieren. Nicht nötig (die
Allowlist blockiert Fremde ohnehin), aber es verhindert, dass überhaupt fremde
`auth.users`-Zeilen entstehen können.

---

## Phase 5 — Google Cloud Console: Maps SDK for Android

Dasselbe Cloud-Projekt, in dem der Places-Key liegt.

### [x] 5.1 API aktivieren

<https://console.cloud.google.com> → richtiges Projekt → APIs & Services → **Library**
→ „Maps SDK for Android" → **Enable**.

**Prüfung:** APIs & Services → **Enabled APIs** listet „Maps SDK for Android".
Erscheint ein Billing-Banner: das Cloud-Projekt braucht ein Rechnungskonto. Da der
Places-Key schon läuft (Places braucht ebenfalls Billing), sollte das erledigt sein.

### [x] 5.2 Key erstellen

APIs & Services → Credentials → **Create credentials → API key**, benennen:
`Selli Maps (Android)`.

Empfehlung: **eigener Key**, nicht den Places-Key erweitern — dann kann man ihn
unabhängig einschränken und notfalls rotieren.

**Erst unbeschränkt testen** (Phase 6+7), dann 5.3. So bleibt bei einer grauen Karte
offen, ob der Key falsch oder nur zu streng ist.

### [x] 5.3 Key einschränken (nach erfolgreichem Kartentest)

- **Application restrictions** → *Android apps* → Add:
  - Package name: `com.prehmus.selli`
  - SHA-1: der Fingerprint aus 0.2
- **API restrictions** → *Restrict key* → nur „Maps SDK for Android"

**Prüfung:** App neu starten (Restriktionen greifen serverseitig, kein Rebuild nötig),
Karte lädt weiterhin Tiles. Wird sie grau → Fingerprint oder Paketname falsch, siehe
Diagnose D4.

---

## Phase 6 — local.properties

### [x] 6.1 Drei Zeilen ergänzen

> Stand 26.08.2026: `selli.supabaseUrl` und `selli.supabaseAnonKey` sind bereits
> eingetragen und in 1.3 gegen die API getestet. Offen ist nur `selli.mapsApiKey`
> aus 5.2.

```properties
selli.supabaseUrl=https://<ref>.supabase.co
selli.supabaseAnonKey=sb_publishable_...
selli.mapsApiKey=...
```

Werte aus 1.2 (URL, Publishable Key) und 5.2 (Maps-Key). Keine Anführungszeichen, keine
Leerzeichen um das `=`, kein Kommentar hinter dem Wert — `local.properties` nimmt alles
bis zum Zeilenende als Wert.

Nicht committen — `local.properties` ist über `.gitignore` ausgeschlossen, dazu wacht
`warn-local-properties.sh`.

### [x] 6.2 Prüfen, ohne die Werte auszugeben

```bash
cd ~/Workspace/selli
grep -c '^selli\.supabaseUrl=..*'      local.properties   # 1
grep -c '^selli\.supabaseAnonKey=..*'  local.properties   # 1
grep -c '^selli\.mapsApiKey=..*'       local.properties   # 1
```

**Erwartet:** dreimal `1`. Eine `0` heißt: Zeile fehlt oder Wert ist leer.

---

## Phase 7 — Neu bauen

> `selli.mapsApiKey` geht **nicht** über `BuildConfig`, sondern über
> `manifestPlaceholders["MAPS_API_KEY"]` ins Manifest. Ein Neustart der App reicht
> nicht — es muss wirklich neu gebaut werden.

### [x] 7.1 Bauen

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # falls "Unable to locate a Java Runtime"
```

In Claude Code: `/release-build` (Tests → `assembleDebug` → Kopie nach `selli.apk`).

**Prüfung:** Tests grün (303+), `selli.apk` im Projekt-Root frisch datiert:
`ls -l selli.apk`.

### [x] 7.2 Prüfen, dass die Werte im Artefakt landeten

```bash
grep -E 'SUPABASE_URL|SUPABASE_ANON_KEY' \
  app/build/generated/source/buildConfig/debug/com/prehmus/selli/BuildConfig.java \
  | sed -E 's/= ".+"/= <gesetzt>/'
```

**Erwartet:** beide Zeilen enden auf `= <gesetzt>;`. Steht dort `= "";` → Phase 6
prüfen und neu bauen. (Das `sed` verhindert, dass der Key im Terminal/Transkript steht.)

```bash
find app/build -name AndroidManifest.xml -path '*merged*' | head -1 \
  | xargs grep -o 'com.google.android.geo.API_KEY" android:value="[^"]\{10,\}"' \
  | sed -E 's/android:value="[^"]+"/android:value=<gesetzt>/'
```

**Erwartet:** eine Trefferzeile. Kein Treffer → der Placeholder ist leer geblieben.

### [x] 7.3 Installieren

`selli.apk` auf **beide** Geräte (WhatsApp o. ä.), installieren, alte Version
überschreiben.

---

## Phase 8 — Gerätetest (beide Personen)

> **Pflichtschritt, sonst scheitert alles Folgende lautlos:** das rohe Google-ID-Token
> wird nur beim **Anmelden** gespeichert (`GoogleCalendarDataRepository.signIn()`), nicht
> beim Wiederherstellen einer gemerkten Sitzung. Wer die App mit gemerkter Anmeldung aus
> dem alten Build weiterbenutzt, hat **kein** Token → Supabase-Anmeldung scheitert.
> Zusätzlich läuft das Token nach ~1 h ab und wird nicht erneuert.
>
> Also, pro Gerät: **Profil → Konto wechseln → neu anmelden**, und den **Standort-Tab
> danach direkt öffnen** (innerhalb einer Stunde). Ist die Supabase-Sitzung einmal
> gekoppelt, erneuert supabase-kt sie selbst — das Google-Token ist dann irrelevant.

### [x] 8.1 Pro Gerät: neu anmelden

Beim Anmelden die **richtige Person** wählen (Basti bzw. Melli). Die Spalte `person`
wird vom Client behauptet, nicht aus der Allowlist abgeleitet — wählen beide dieselbe,
stehen zwei Zeilen mit gleichem `person`-Wert in der Tabelle.

**Prüfung** (SQL-Editor, nachdem *beide* sich angemeldet und den Standort-Tab geöffnet
haben):

```sql
select email, created_at, last_sign_in_at from auth.users order by created_at;
```

**Erwartet:** genau zwei Zeilen mit den zwei echten Adressen. Null Zeilen → Phase 4
falsch oder Token fehlt (Diagnose D2). Eine Zeile → die zweite Person hat den
Standort-Tab noch nicht geöffnet; „nur einer sichtbar" ist bis dahin der **erwartete**
Zustand.

### [x] 8.2 Standortberechtigung erteilen

Beim ersten Öffnen des Standort-Tabs: Standort erlauben → **präzise**. Für dauerhaftes
Tracking zusätzlich Systemeinstellungen → Berechtigungen → Standort → **„Immer
zulassen"** (Android fragt Hintergrundstandort nie im ersten Dialog).

**Prüfung:** In der Statusleiste liegt die stille Benachrichtigung „Selli teilt deinen
Standort". Per adb:

```bash
adb shell dumpsys package com.prehmus.selli | grep -A1 ACCESS_BACKGROUND_LOCATION
```
→ `granted=true`.

### [x] 8.3 Positionen kommen an

```sql
select person, user_id, round(latitude::numeric,4) as lat, round(longitude::numeric,4) as lon,
       is_moving, updated_at, now() - updated_at as alter
from public.locations order by person;
```

**Erwartet:** zwei Zeilen, `BASTI` und `MELLI`, unterschiedliche `user_id`, `alter`
höchstens ~5 Minuten (Stillstandstakt). Keine Zeile → Diagnose D2/D3.

### [x] 8.4 Karte

**Erwartet:** Kartentiles in warmen Selli-Tönen (nicht grau, nicht Google-Standard),
zwei Pins in Lila und Grün mit Avatar-Gesicht, darunter „gerade jetzt" /
„vor N Min.". Grau → D4. Google-Standardfarben → `map_style_selli.json` greift nicht.

### [x] 8.5 Live-Bewegung

Eine Person fährt/geht los (Bewegungserkennung schaltet auf 30-s-GPS-Takt). Auf dem
anderen Gerät bei geöffnetem Tab: **Marker wandert gleichmäßig über 1,2 s** zur neuen
Position, Frische springt auf „gerade jetzt".

**Prüfung Realtime vs. Polling:** kommt die Bewegung binnen Sekunden → `postgres_changes`
läuft. Kommt sie in ~60-s-Sprüngen → Realtime scheitert, der Polling-Fallback trägt;
Logcat auf Quelle „Supabase-Standort-Realtime" prüfen (D1) und Phase 2.2
`realtime`-Spalte gegenprüfen.

---

## Diagnose

Logcat-Filter für alle Supabase-Fehler der App:

```bash
adb logcat -s SelliCalendar:E
```

Die Quellen heißen „Supabase-Anmeldung", „Supabase-Sitzung",
„Supabase-Standort-Anmeldung", „Supabase-Standorte laden", „Supabase-Standort-Realtime",
„Supabase-Standort-Polling", „Supabase-Standort-Upload".

Serverseitig: Dashboard → Logs → **Auth** (Anmeldung), **Postgres**/**API** (RLS,
Rechte), **Realtime**.

### D1 — Karte bleibt leer, keine Fehlermeldung

`observeLocations()` liefert bei jedem Fehler eine leere Liste statt zu werfen — die UI
kann den Unterschied nicht zeigen. Der Fehler steht **nur** im Logcat. In dieser
Reihenfolge prüfen: Allowlist (3.2/3.3) → ID-Token (8.1) → Grants (2.3) → Provider (4.2).

### D2 — „Kein Google-ID-Token für die Supabase-Anmeldung verfügbar"

Genau der Fall aus Phase 8: gemerkte Sitzung ohne gespeichertes Token, oder Token älter
als eine Stunde. **Profil → Konto wechseln → neu anmelden → Standort-Tab sofort öffnen.**

### D3 — Anmeldung scheitert (Logcat-Quelle „Supabase-Anmeldung")

| Meldung | Ursache |
|---|---|
| `Unsupported provider` | Google-Provider nicht aktiv → 4.1 |
| `Invalid audience` / `aud`-Fehler | falsche Client-ID (Android statt Web) → 4.1 |
| `token expired` / `invalid JWT` | ID-Token abgelaufen → D2 |
| `nonce` im Fehlertext | *Skip nonce checks* aus → 4.1 |

Gegenprobe in Dashboard → Logs → Auth: dort steht der Grund der Ablehnung im Klartext.

### D4 — Karte grau, keine Tiles

```bash
adb logcat | grep -i "Maps Android API"
```

Die Meldung nennt Paketname und SHA-1, die Google gesehen hat — genau damit 5.3
vergleichen. **Achtung: diese Logzeile enthält den API-Key im Klartext — nicht
unredigiert in einen Chat oder ein Ticket kopieren.**

Häufig: Maps SDK for Android nicht aktiviert (5.1) · Key auf Places API beschränkt
(5.3, API restrictions) · SHA-1 eines anderen Rechners · `selli.mapsApiKey` gesetzt,
aber nicht neu gebaut (7.2).

### D5 — „permission denied for table locations"

Data-API-Grants fehlen → 2.3.

### D6 — leere Liste, aber Logcat zeigt nichts

Standort-Tab zeigt einen Hinweiszustand statt einer Karte → `isConfigured` ist false,
also fehlen `selli.supabaseUrl`/`selli.supabaseAnonKey` im Build → 6.2 und 7.2.

### D7 — Nach Tagen Pause geht nichts mehr

Free-Tier-Projekte werden nach ~7 Tagen ohne Aktivität pausiert. Dashboard →
**Restore/Resume**. Im laufenden Betrieb irrelevant (es wird dauerhaft geschrieben),
aber relevant, wenn zwischen Anlegen und Testen Tage liegen.

---

## Was ausdrücklich **nicht** nötig ist

- kein `service_role`/secret key in der App
- kein Google **Client Secret** in Supabase
- keine Supabase CLI, kein `SUPABASE_ACCESS_TOKEN`
- keine Storage-Buckets, keine Edge Functions
- keine zweite Migration — `locations` und `selli_members` sind das ganze Schema
