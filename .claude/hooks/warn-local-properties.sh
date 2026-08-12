#!/usr/bin/env bash
# PreToolUse-Hook (Edit|Write): local.properties enthält echte Secrets
# (selli.googleServerClientId, selli.icsFeedUrl, selli.melliIcsFeedUrl,
# selli.placesApiKey). Datei ist bewusst nicht versioniert (siehe CLAUDE.md /
# AGENTS.md "Keine Secrets im Code"). Kein Hard-Block, weil lokale
# Config-Änderungen (neuer Feed-Link, neuer API-Key) ein legitimer Workflow
# sind - aber Basti soll jede Änderung bewusst bestätigen statt dass sie
# unbemerkt durchläuft.
set -euo pipefail

input="$(cat)"
file_path="$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty')"

case "$file_path" in
  */local.properties) ;;
  *) exit 0 ;;
esac

jq -n '{
  hookSpecificOutput: {
    hookEventName: "PreToolUse",
    permissionDecision: "ask",
    permissionDecisionReason: "local.properties enthält echte Secrets (selli.googleServerClientId, selli.icsFeedUrl, selli.melliIcsFeedUrl, selli.placesApiKey) und ist nicht versioniert. Werte nie in Commits, PRs, Logs oder Chat-Ausgaben landen lassen."
  }
}'
