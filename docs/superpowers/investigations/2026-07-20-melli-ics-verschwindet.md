# Untersuchung: Mellis ICS-Arbeitskalender verschwindet zeitweise

**Datum:** 2026-07-20  
**Status:** Plausibelste Root Cause adressiert; ohne Mellis Gerät nicht zu 100 % reproduziert

## Symptom

Mellis Termine aus dem veröffentlichten Dr.-Plano-ICS-Arbeitskalender verschwinden zeitweise. Das Verhalten tritt insbesondere nach schnellem Vor- und Zurück-Swipen über mehrere Monate auf. Auf Bastis Gerät ließ es sich bislang nicht reproduzieren. Nach einem späteren erfolgreichen Laden erscheinen die Termine wieder.

## Verifizierte Fakten

- Kalendernavigation verwendet kein ICS-Response-Caching. Jede Navigation ruft `fetchEvents` erneut auf und lädt den vollständigen Feed, obwohl der Parser anschließend nur den angeforderten Datumsbereich zurückgibt.
- Google, Bastis ICS und Mellis ICS werden im `DefaultCalendarMergeService` getrennt geladen. Ein gewöhnlicher Fehler einer Quelle wird isoliert, mit dem bestehenden Logger protokolliert und bisher durch eine leere Eventliste ersetzt.
- Durch dieses Verhalten ist ein fehlgeschlagener Melli-Fetch für die bisherige UI nicht von einem erfolgreich geladenen, aber leeren Arbeitskalender unterscheidbar.
- Mellis Repository wird mit einem `IcsCalendarParser(owner = Person.MELLI)` erstellt. Der Owner wird außerdem Teil des Merge-Deduplizierungsschlüssels `(id, source, owner)`.
- Vor dem Fix verwendete das Repository einen unveränderten `OkHttpClient()` und besaß keinen anwendungseigenen Retry/Backoff. OkHttps Defaults allein verhindern nicht, dass ein fehlgeschlagener Request als leere Quelle im Merge endet.
- Das bestehende Android-Logging verwendet das Logtag `SelliCalendar`; der Merge-Logger nennt die fehlerhafte Quelle separat, für Melli derzeit `Melli ICS work calendar`.

## Geprüfte Hypothesen

### Parserfehler

Ein Parserfehler ist als generelle Möglichkeit nicht ausgeschlossen: Ein unerwartetes Dr.-Plano-ICS-Format könnte `parse` scheitern lassen und würde ebenfalls als Quellfehler isoliert. Als Erklärung für das konkrete zeitweise, swipe-abhängige und selbstheilende Verhalten ist er jedoch weniger wahrscheinlich. Derselbe vollständige Feed wird bei jeder Monatsnavigation geladen, der Parser arbeitet deterministisch, und ein stabil vorhandener problematischer Eintrag oder ein stabil falsches Format sollte nicht allein durch einen späteren identischen Fetch verschwinden. Die neue sichtbare Exception würde einen solchen Parserfehler künftig von einem Transportfehler unterscheidbar machen.

### Falscher Owner oder Merge-Deduplizierung

Diese Hypothese ist unwahrscheinlich. Mellis Parser ist explizit mit `Person.MELLI` konfiguriert, vorhandene Parser-/Merge-Tests decken Mellis Owner ab, und der Deduplizierungsschlüssel enthält den Owner. Ein Owner- oder Deduplizierungsfehler wäre außerdem deterministisch und würde nicht plausibel nur nach schnellem Swipen auftreten und sich beim nächsten Fetch selbst beheben.

### UI-Filter oder lokales Caching

Für ein veraltetes Cache-Ergebnis gibt es im untersuchten ICS-Pfad keinen Cache: Navigation erzeugt neue vollständige Downloads. Das Verschwinden genau einer isolierten Quelle passt direkt zum vorhandenen Fehlerpfad im Merge-Service. Die UI-Verdrahtung des neuen Status ist eine nachgelagerte Aufgabe von Claude und wurde hier nicht verändert.

### Transienter Dr.-Plano-Server-/Netzwerkfehler unter Last

Dies ist die gewählte Root-Cause-Hypothese. Schnelles Swipen erzeugt wiederholte vollständige Downloads. Ein transienter Timeout, Verbindungsabbruch, HTTP 429 oder HTTP 5xx vom Fremdserver führt zum Scheitern von Mellis Quell-Request. `DefaultCalendarMergeService` hat diesen Fehler bisher geloggt, aber für den Aufrufer in null Melli-Events verwandelt. Damit verschwindet der Arbeitskalender lautlos bis zu einem späteren erfolgreichen Fetch.

Die Hypothese erklärt alle bekannten Merkmale: Zusammenhang mit Request-Häufigkeit, Beschränkung auf die externe Dr.-Plano-Quelle, geräte-/netzabhängige Reproduzierbarkeit, Isolation der übrigen Kalender und Selbstheilung beim nächsten Erfolg. Ohne Mellis Gerät, den konkreten Fehlerlog und Server-Telemetrie ist sie dennoch nicht endgültig bewiesen.

## Umgesetzter Fix

`OkHttpIcsCalendarRepository` verwendet standardmäßig explizite Timeouts (Connect 15 s, Read 20 s, Write 20 s, gesamter Call 30 s) und `retryOnConnectionFailure(true)`. Darüber liegt ein kleiner, auf drei Versuche begrenzter Retry mit exponentiellem Backoff. Wiederholt werden ausschließlich Transport-`IOException`s sowie HTTP 429 und 5xx. Andere HTTP-4xx-Antworten werden sofort weitergereicht; Coroutine-Cancellation wird weder gefangen noch wiederholt.

Der Domain-Vertrag liefert jetzt neben erfolgreichen Events eine Liste von `SourceLoadError`. Der Default-Merge-Service hält die Quellen weiterhin voneinander isoliert, loggt die Originalexception und meldet Fehler unter klaren deutschen Namen: `Google-Kalender`, `Bastis Arbeitskalender` und `Mellis Arbeitskalender`. `CalendarAuthRequiredException` bleibt besonderer Kontrollfluss und wird weiterhin durchgereicht.

Der Retry reduziert das sichtbare Problem bei kurzen Störungen. Die Statusliste verhindert, dass ein nach allen Versuchen verbleibender Fehler weiterhin wie ein leerer Kalender aussieht. Der Fix führt bewusst weder Feed-Caching noch Request-Deduplizierung ein; beides wäre eine eigene Optimierungsentscheidung.

## Sichere Bestätigung beim nächsten Auftreten

Nach Claudes UI-Verdrahtung sind bei erneut fehlenden Melli-Terminen zwei Signale gemeinsam zu erfassen:

1. Den sichtbaren Fehler für **Mellis Arbeitskalender** einschließlich Nachricht und Zeitpunkt notieren oder screenshotten.
2. Unmittelbar danach die Android-Logs nach dem Tag `SelliCalendar` filtern und den Eintrag der Quelle `Melli ICS work calendar` samt Exceptionklasse/-nachricht sichern.

Beispiel für die lokale Log-Abfrage bei verbundenem Gerät:

```bash
adb logcat -d -s SelliCalendar
```

Zeigt die UI beziehungsweise der Log zeitgleich `SocketTimeoutException`, eine andere `IOException`, HTTP 429 oder HTTP 5xx, ist die Hypothese für diesen Vorfall bestätigt. Zeigt er stattdessen eine Parserexception, einen nicht transienten HTTP-4xx-Status oder einen Konfigurationsfehler, ist die Hypothese für diesen Vorfall verworfen und die konkrete gemeldete Fehlerklasse ist der neue Ausgangspunkt. Bleibt die Fehlerliste leer, obwohl ausschließlich Mellis ICS-Termine fehlen, muss der Datenfluss nach dem Merge-Service (UI-State/Filter) untersucht werden.
