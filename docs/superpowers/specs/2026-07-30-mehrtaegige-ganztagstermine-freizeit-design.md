# Mehrtägige Ganztagstermine und lokale Frei-Zeit-Festlegung

Status: durch den Auftrag vollständig vorgegeben; Basti hat die ganzheitliche Umsetzung ohne
weitere Rückfragen freigegeben.

## Ziel

Selli kann ganztägige Google-Termine mit einem inklusiven Start- und Endtag anlegen. Für jeden
ganztägigen Termin kann Selli lokal festlegen, ob er die gemeinsame Frei-Zeit-Berechnung blockiert.
Diese Festlegung verändert niemals den verbundenen Google-Kalender.

## Bestehendes Verhalten

- Google und der ICS-Parser modellieren das Ende ganztägiger Termine bereits exklusiv als
  Mitternacht nach dem letzten enthaltenen Tag.
- `CalendarViewModel.groupByDay` verteilt mehrtägige Termine bereits auf jeden enthaltenen Tag.
- `DefaultCalendarMergeService.freeBlocks` ignoriert derzeit jeden ganztägigen Termin.
- Lokale Anpassungen werden als `EventCustomization` für ein einzelnes Vorkommen oder eine Serie
  ab einem Vorkommen gespeichert. Occurrence-Anpassungen haben Vorrang vor Serien-Anpassungen.
- Die Anlage schreibt einen echten Termin in den eigenen Google-Kalender; Kategorie und andere
  Selli-spezifische Entscheidungen werden anschließend lokal gespeichert.

## Gewählter Ansatz

Das bestehende Anpassungssystem erhält eine nullable Überschreibung
`blocksSharedFreeTime: Boolean?`. `null` bedeutet, dass Sellis Standard greift; `true` und `false`
sind explizite lokale Entscheidungen. Dadurch bleiben Target-Auflösung, Persistenz, Reset und
Serien-Semantik an einer Stelle.

Alternativen wurden verworfen:

1. Ein separates Repository für Frei-Zeit-Festlegungen würde Event-Keys, Serien-Targets,
   Vorrangregeln und atomare Persistenz duplizieren.
2. Eine Google-Extended-Property wäre nicht rein lokal, würde den echten Kalendereintrag verändern
   und könnte fremde oder ICS-Termine nicht konsistent abdecken.

## Datenmodell und Merge

- `CalendarEvent.blocksSharedFreeTime` enthält den nach dem Merge wirksamen Wert. Sein
  Quell-Standard ist `false` für ganztägige und `true` für getimte Termine.
- `NewCalendarEvent.blocksSharedFreeTime` enthält die lokale Auswahl bei der Anlage. Der
  Modellstandard folgt `isAllDay`, sodass ein neu angelegter Ganztagstermin standardmäßig
  blockiert.
- `EventFieldOverrides.blocksSharedFreeTime` speichert die explizite lokale Festlegung. Das Feld
  zählt als vorhandene Anpassung, erzeugt allein aber kein allgemeines
  „Titel/Zeit angepasst“-Badge.
- Der Customization-Codec ergänzt das nullable Feld additiv im bestehenden Format. Alte
  Version-1-Dateien ohne das Feld werden weiterhin als `null` gelesen.
- Der Merge übernimmt eine explizite Festlegung in den wirksamen Event-Wert. Ohne Festlegung bleibt
  das bisherige Verhalten für importierte Ganztagstermine unverändert.

## Frei-Zeit-Berechnung

Getimte Termine werden unverändert in ihr tatsächliches, auf 9–22 Uhr beschnittenes Intervall
umgerechnet. Ganztägige Termine werden nur berücksichtigt, wenn ihr wirksamer Wert
`blocksSharedFreeTime == true` ist. Da ihr Intervall den ganzen Kalendertag umfasst, blockieren sie
an jedem enthaltenen Tag das gesamte Frei-Zeit-Fenster. Ein nicht blockierender Ganztagstermin
trägt kein gesperrtes Intervall bei.

## Anlage

Im Anlegen-Sheet bleibt der bisherige Tag für getimte Termine bestehen. Bei aktiviertem
„Ganztägig“ erscheinen zwei Datumswahlen:

- Starttag
- Endtag, inklusive

Der Endtag startet auf dem Starttag und darf nicht davor liegen. Wird der Starttag hinter den
bisherigen Endtag verschoben, zieht der Endtag auf den neuen Starttag nach. Das gespeicherte
Google-Enddatum ist `endDay + 1 Tag` und bleibt damit API-konform exklusiv.

Zusätzlich erscheint nur für Ganztagstermine ein standardmäßig aktivierter Schalter
„Als gemeinsam verplante Zeit werten“. Seine Auswahl wird im `NewCalendarEvent` übergeben.

Nach erfolgreicher Google-Anlage speichert das ViewModel genau eine lokale Customization:

- die Frei-Zeit-Festlegung bei jedem ganztägigen Termin, auch bei explizitem `false`;
- zusätzlich eine Kategorie, falls sie von Sellis automatisch abgeleiteter Kategorie abweicht.

Bei einer neu angelegten Serie zielt die Customization auf die Serie ab ihrem ersten Vorkommen;
bei einem Einzeltermin auf dessen Occurrence-Key. Ein lokaler Speicherfehler wird sichtbar
gemeldet, ohne den bereits erfolgreich angelegten Google-Termin zu löschen.

## Lokales Bearbeiten

Das Bearbeiten-Sheet zeigt bei jedem ganztägigen Termin denselben Schalter mit dem aktuell
wirksamen Wert. Eine Änderung wird als Teil der bestehenden `EventFieldOverrides` gespeichert.
Die vorhandene Auswahl „nur dieses Vorkommen“ oder „dieses und alle folgenden“ bleibt bestehen.
Beim Zusammenführen mit einer bereits gespeicherten Anpassung bleiben Kategorie und
Frei-Zeit-Festlegung erhalten, wenn das jeweilige Feld im aktuellen Bearbeitungsvorgang nicht
geändert wurde.

Es wird ausschließlich `EventCustomizationRepository.save` aufgerufen. Das Bearbeiten ruft weder
`CalendarRepository.createEvent` noch eine Google-Update-API auf.

## Tests

Die Unit-Tests decken mindestens ab:

- Codec-Roundtrip für `true` und `false` sowie Dekodierung einer alten Datei ohne neues Feld;
- Merge-Standard für importierte Ganztagstermine;
- blockierende und nicht blockierende Ganztagstermine;
- einen mehrtägigen blockierenden Ganztagstermin an einem mittleren Tag;
- unverändertes Blockieren getimter Termine;
- Erzeugung eines mehrtägigen Entwurfs mit inklusivem UI-Endtag und exklusivem Google-Ende;
- Anlage mit beiden Schalterzuständen und korrekter lokaler Persistenz;
- Bearbeiten mit lokaler Persistenz, Erhalt bestehender Overrides und ohne Google-Schreibzugriff.

Danach laufen die vollständigen Unit-Tests und `assembleDebug`. Die erfolgreiche Debug-APK wird
nach `selli.apk` im Projekt-Root kopiert.

## Scope- und Owner-Entscheidung

Der Auftrag verlangt ausdrücklich das vollständige End-to-End-Verhalten und Basti hat die
ganzheitliche Umsetzung einschließlich Compose bestätigt. Deshalb umfasst dieser Feature-Branch
ausnahmsweise sowohl Codex-Logikdateien als auch die dafür notwendige, eng begrenzte Verdrahtung in
`CreateEventSheet`, `EditEventSheet` und `CalendarScreen`. Es werden keine visuellen Design-Tokens
oder unabhängigen UI-Konzepte geändert.
