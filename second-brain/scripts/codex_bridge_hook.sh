#!/usr/bin/env bash
# Second Brain — Codex / Cursor Instant Continuity Hook
# Fetches the latest continuity state and prints the synthesized briefing

BRAIN_URL="${BRAIN_URL:-http://localhost:8080}"
REPO_PATH="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

echo "=== Querying Second Brain for Previous Agent Activity ==="
RESPONSE=$(curl -s "$BRAIN_URL/api/v1/bridge/continuity?repo=$(python3 -c "import urllib.parse; print(urllib.parse.quote('''$REPO_PATH'''))" 2>/dev/null || echo "$REPO_PATH")")

BRIEFING=$(echo "$RESPONSE" | grep -o '"structuredBriefing":"[^"]*' | sed 's/"structuredBriefing":"//' | sed 's/\\n/\n/g' | sed 's/\\"/"/g')

if [ -n "$BRIEFING" ]; then
    echo -e "$BRIEFING"
else
    echo "No prior agent continuity state found for this repository."
fi
