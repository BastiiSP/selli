# Feed-Caching zur Entlastung (Folge-Fix zu Punkt 4 vom 20.07.2026)

Status: von Basti bestätigt, bereit für Handover-Prompt.

Kontext: Am 21.07.2026 wurde die Root Cause aus
`docs/superpowers/investigations/2026-07-20-melli-ics-verschwindet.md` live bestätigt: Dr.
Planos Server antwortet mit `HTTP 429`, weil jede Navigation in Selli aktuell alle drei
Kalenderquellen (Google, Bastis Outlook-ICS, Mellis Dr.-Plano-ICS) komplett neu lädt. Der
bereits gemergte Fix (Timeouts/Retry, sichtbarer Fehler) macht das Problem sichtbar, senkt aber
nicht die Abruf-Häufigkeit selbst. Dieser Fix schließt die Lücke.

## Was

Ein kurzlebiger, quellenweiser Cache für alle drei Kalenderquellen. Innerhalb der Cache-Zeit
liefert eine erneute Anfrage für dieselbe Quelle den zuletzt erfolgreich geladenen Stand statt
eine neue Netzwerk-Anfrage auszulösen.

## Warum

Automatisches Nachladen bei jeder Navigation (Ansicht wechseln, vor/zurück blättern, App
öffnen) ist die identifizierte Ursache der Serversperre. Ein Cache mit ausreichendem
Sicherheitsabstand verhindert, dass reines Navigieren jemals wieder in eine Sperre läuft —
unabhängig davon, wie schnell oder häufig navigiert wird.

## Anforderungen

- **Geltungsbereich:** Alle drei Quellen (Google, Bastis ICS, Mellis ICS) — einheitliches
  Verhalten, kein Sonderfall nur für ICS.
- **Cache-Dauer:** 15 Minuten pro Quelle. Automatisches Nachladen durch Navigation passiert
  während dieser Zeit nicht erneut für dieselbe Quelle.
- **Manuelles Aktualisieren umgeht den Cache immer:** Pull-to-Refresh und der „Erneut"-Button
  aus dem Fehler-Banner (Punkt 3 vom 20.07.2026) lösen **immer** eine echte Netzwerk-Anfrage
  aus, unabhängig vom Cache-Alter. Das ist bewusst so gewünscht — wer wirklich aktuelle Daten
  will, bekommt sie sofort.
- **Kein sichtbarer Hinweis auf das Cache-Alter** — der Cache arbeitet vollständig im
  Hintergrund, für Basti/Melli ändert sich nur, dass Navigation seltener auf das Netzwerk
  wartet.
- **Fehlerverhalten bleibt unverändert:** Schlägt eine Quelle fehl (z. B. weiterhin gesperrt),
  gilt weiterhin die bestehende Logik aus Punkt 4 (sichtbares Fehler-Banner, keine stille
  Leere). Ein einmal erfolgreich gecachter Stand wird durch einen späteren fehlgeschlagenen
  automatischen Fetch nicht überschrieben — die zuletzt bekannten Termine bleiben sichtbar.
- **Cache-Schlüssel/Struktur liegt bei der Umsetzung:** Ob pro angefragtem Zeitraum (Google) oder
  pro rohem Feed-Inhalt (ICS, da Google-API bereits zeitraum-scoped abfragt, ICS aber immer den
  vollen Feed lädt und erst clientseitig filtert) gecacht wird, entscheidet die Umsetzung —
  Hauptsache, das Ergebnis ist: keine zweite Netzwerk-Anfrage an dieselbe Quelle innerhalb von
  15 Minuten außer bei explizitem manuellen Aktualisieren.
- **In-Memory reicht** — kein Bedarf, den Cache über App-Neustarts hinweg zu erhalten (ein
  Neustart darf ruhig einmal frisch laden).

## Owner-Hinweis

Reine Datenschicht-/Logik-Änderung (Repository-/Merge-Service-Ebene) — komplett Codex, keine
UI-Datei betroffen.
