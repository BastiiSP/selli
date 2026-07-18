#!/usr/bin/env python3
"""
Einmaliges Produktionswerkzeug für die Selli-Illustrationen (Maskottchen, Avatare, App-Icon).

Kein Laufzeit-Feature der App — läuft nur hier lokal, um statische Bilddateien zu erzeugen,
die anschließend manuell (nach Sichtung/Auswahl) unter app/src/main/res/drawable-.../ einsortiert
werden. Siehe Designkonzept in CLAUDE.md ("Asset-Produktion").

Setup (siehe README.md für Details):
    cp .env.example .env && $EDITOR .env   # eigenen Key eintragen, nie committen/chatten
    set -a && source .env && set +a
    python3 tools/generate-assets/generate_assets.py            # alle Assets
    python3 tools/generate-assets/generate_assets.py mascot_empty_state   # nur ein Asset

Ausgabe landet in tools/generate-assets/output/ (gitignored) — von dort aus manuell
die besten Ergebnisse auswählen und ins Projekt übernehmen.
"""

import base64
import json
import os
import sys
import urllib.request
import urllib.error

API_URL = "https://api.openai.com/v1/images/generations"
MODEL = os.environ.get("SELLI_IMAGE_MODEL", "gpt-image-1")
OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "output")

# Gemeinsamer Stil-Anker für alle Prompts, damit die Ergebnisse zusammenpassen.
STYLE_ANCHOR = (
    "A single small original character, inspired by the whimsical soot-sprite aesthetic "
    "of Studio Ghibli films (not a copy of any copyrighted character) — round, soft, "
    "dark charcoal-black body, big round curious eyes, stubby little limbs, endearing "
    "and a bit clumsy. Flat, warm, hand-illustrated children's-book style. "
    "Clean isolated subject, transparent background, no text, no watermark."
)

ASSETS = {
    "mascot_idle": (
        f"{STYLE_ANCHOR} The creature stands in a neutral, friendly resting pose, "
        "slightly tilted head, one small hand raised as if waving hello."
    ),
    "mascot_loading": (
        f"{STYLE_ANCHOR} The creature is busily gathering small glowing calendar-page "
        "icons into its arms, mid-motion, playful energy, as if collecting little events."
    ),
    "mascot_empty_state": (
        f"{STYLE_ANCHOR} The creature sits alone looking around curiously with a gentle, "
        "content expression — calm and cozy, evoking 'nothing planned today, and that's fine'."
    ),
    "mascot_celebrating": (
        f"{STYLE_ANCHOR} The creature jumps slightly with both little arms raised up in "
        "joyful celebration, eyes shaped as happy little arcs — for a 'both of us are free' moment."
    ),
    "mascot_traveling": (
        f"{STYLE_ANCHOR} The creature walks along a small winding dotted path carrying a "
        "tiny bindle/travel bag over its shoulder, mid-stride, as if journeying between two places."
    ),
    "app_icon_foreground": (
        f"{STYLE_ANCHOR} The creature centered and simplified for use as an app icon "
        "foreground — bold, simple silhouette, minimal fine detail, reads clearly at small sizes, "
        "neutral idle pose facing forward."
    ),
    "avatar_basti": (
        "A small round illustrated avatar portrait in the same hand-illustrated children's-book "
        "style as a warm original soot-sprite-inspired mascot character (not a copy of any "
        "copyrighted character) — a friendly, simplified, gender-neutral stylized human face "
        "portrait (not photorealistic), with a soft green color accent (#4C9A6A) worked into "
        "hair or clothing, centered, isolated, transparent background, no text."
    ),
    "avatar_melli": (
        "A small round illustrated avatar portrait in the same hand-illustrated children's-book "
        "style as a warm original soot-sprite-inspired mascot character (not a copy of any "
        "copyrighted character) — a friendly, simplified, gender-neutral stylized human face "
        "portrait (not photorealistic), with a soft purple color accent (#8E6BBF) worked into "
        "hair or clothing, centered, isolated, transparent background, no text."
    ),
}

SIZE = "1024x1024"


def generate(name: str, prompt: str) -> None:
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        sys.exit("OPENAI_API_KEY ist nicht gesetzt. Siehe Docstring/README für Setup.")

    payload = {
        "model": MODEL,
        "prompt": prompt,
        "size": SIZE,
        "background": "transparent",
        "n": 1,
    }
    request = urllib.request.Request(
        API_URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )

    print(f"→ Generiere '{name}' ...")
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            body = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        sys.exit(f"Fehler bei '{name}': HTTP {error.code} — {detail}")

    image_b64 = body["data"][0]["b64_json"]
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    out_path = os.path.join(OUTPUT_DIR, f"{name}.png")
    with open(out_path, "wb") as file:
        file.write(base64.b64decode(image_b64))
    print(f"  ✓ gespeichert: {out_path}")


def main() -> None:
    requested = sys.argv[1:] or list(ASSETS.keys())
    unknown = [name for name in requested if name not in ASSETS]
    if unknown:
        sys.exit(f"Unbekannte Asset-Namen: {unknown}. Verfügbar: {list(ASSETS.keys())}")

    for name in requested:
        generate(name, ASSETS[name])


if __name__ == "__main__":
    main()
