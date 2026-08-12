#!/usr/bin/env bash
# PostToolUse-Hook (Edit|Write): schneller Kompilier-Check nach Änderungen an
# app/src/main/**/*.kt. Selli hat kein CI - das ist der einzige automatische
# Regressions-Fang zwischen den Sessions.
set -euo pipefail

input="$(cat)"
file_path="$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty')"

case "$file_path" in
  */app/src/main/*.kt) ;;
  *) exit 0 ;;
esac

project_dir="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
cd "$project_dir"

if output="$(./gradlew compileDebugKotlin --console=plain -q 2>&1)"; then
  exit 0
fi

# Kompilierung fehlgeschlagen -> Fehler direkt ins Modell-Kontext zurückspielen,
# damit sofort nachgebessert werden kann statt den Fehler erst beim Release-Build
# zu entdecken.
jq -n --arg file "$file_path" --arg log "$(printf '%s' "$output" | tail -c 4000)" '{
  hookSpecificOutput: {
    hookEventName: "PostToolUse",
    additionalContext: ("compileDebugKotlin ist nach der Änderung an \($file) fehlgeschlagen:\n\n\($log)")
  }
}'
