# Asset-Produktion (einmalig, kein Laufzeit-Feature)

Erzeugt die Ghibli-inspirierten Maskottchen-Posen, Avatare und das App-Icon-Foreground
per OpenAI-Bildgenerierung — siehe Designkonzept in `../../CLAUDE.md`.

## Setup

1. Eigenen OpenAI-API-Key besorgen (platform.openai.com) — **niemals ins Repo oder in einen
   Chat einfügen**.
2. Lokal setzen, z. B. nur für die aktuelle Terminal-Session:
   ```bash
   export OPENAI_API_KEY="sk-..."
   ```

## Ausführen

```bash
cd tools/generate-assets
python3 generate_assets.py                 # alle Assets
python3 generate_assets.py mascot_idle      # nur ein einzelnes Asset
```

Ergebnisse landen in `output/` (gitignored, nicht Teil des Repos).

## Danach

1. Ergebnisse in `output/` sichten, beste Variante(n) auswählen (ggf. mehrfach mit
   angepasstem Prompt in `generate_assets.py` neu generieren)
2. Ausgewählte Dateien manuell nach `app/src/main/res/drawable-nodpi/` (oder passende
   Dichte-Ordner) kopieren und dort sinnvoll benennen (z. B. `mascot_empty_state.png`)
3. `ic_launcher_foreground.xml` (aktuell ein Platzhalter-Kreis) durch das finale,
   in `app_icon_foreground.png` erzeugte Motiv ersetzen bzw. darauf referenzieren
4. In Compose (Owner: Claude) einbinden, wo im Designkonzept beschrieben (Ladeanimation,
   Leerzustand, reagierendes Element im Kalender-Header, Distanz-Feature)
