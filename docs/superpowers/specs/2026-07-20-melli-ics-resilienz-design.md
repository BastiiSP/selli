# Melli-ICS-Resilienz Design

## Ziel und Befund

Mellis Dr.-Plano-ICS-Termine sollen bei transienten Netzwerk- oder Serverfehlern nicht bereits nach einem einzelnen fehlgeschlagenen Voll-Download lautlos verschwinden. Der bestehende Datenfluss bestätigt die leitende Hypothese als plausibelste Erklärung: Jede Navigation lädt den vollständigen Feed, der HTTP-Zugriff hat keine projektspezifischen Resilienzregeln, und der Merge-Service ersetzt gewöhnliche Quellfehler durch eine leere Eventliste. Ohne Mellis Gerät und Server-Telemetrie bleibt die Ursache dennoch eine begründete Hypothese statt einer 100-prozentigen Reproduktion.

## Architektur

`OkHttpIcsCalendarRepository` bleibt für URL, Client und Parser konstruktorinjizierbar. Sein Default-Client erhält explizite Connect-/Read-/Write-/Call-Timeouts und OkHttp-Verbindungswiederholung. Zusätzlich wiederholt das Repository höchstens dreimal nur `IOException` sowie HTTP 429 und 5xx; andere HTTP-4xx-Fehler und `CancellationException` werden unmittelbar weitergereicht. Eine injizierbare suspendierende Backoff-Funktion macht Zeitverhalten deterministisch testbar.

Der Domain-Vertrag ergänzt `MergedCalendar(events, errors)` und `SourceLoadError(displayName, message)`. Eine Default-Methode in `CalendarMergeService` erhält bestehende Implementierungen. `DefaultCalendarMergeService` lädt die drei Quellen weiterhin isoliert und parallel, sammelt neben erfolgreichen Events pro fehlgeschlagener Quelle einen deutsch benannten Fehler und loggt die Originalexception. Authentifizierungs- und Abbruchsignale bleiben Kontrollfluss und werden nicht in Statusfehler umgewandelt. `mergedEvents` delegiert auf dieselbe Statuslogik, sodass bestehende Aufrufer und `freeBlocks` kompatibel bleiben.

## Tests und Grenzen

MockWebServer-Tests prüfen transienten HTTP-Fehler mit Erfolg im zweiten Versuch, nicht wiederholbares HTTP 4xx und unveränderte Cancellation. Merge-Tests prüfen partielle Erfolge mit exakt den fehlgeschlagenen Quellen sowie eine leere Fehlerliste bei vollständigem Erfolg. Assertions vergleichen bei Coroutine-Grenzen Exceptiontyp und Nachricht, nicht Instanzidentität. UI-Verdrahtung, Caching und Request-Deduplizierung sind ausdrücklich außerhalb dieses Fixes.
