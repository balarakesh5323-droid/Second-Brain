#!/usr/bin/env bash
# Second Brain — Claude Code Automatic Activity Bridge Hook
# Intercepts agent activity, uncommitted working-tree changes, test runs, and errors,
# and streams them automatically to Second Brain.

BRAIN_URL="${BRAIN_URL:-http://localhost:8080}"
AGENT_NAME="claude-code"
ACTION_TYPE="${1:-COMMAND_EXEC}"
COMMAND_STR="$2"
STATUS_CODE="${3:-0}"
ERROR_MSG="$4"

REPO_PATH="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
REPO_NAME="$(basename "$REPO_PATH")"

# Capture uncommitted git diff stat
DIFF_STAT=""
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    DIFF_STAT="$(git diff --stat 2>/dev/null)"
fi

# Capture modified files
MODIFIED_FILES=()
while IFS= read -r file; do
    [[ -n "$file" ]] && MODIFIED_FILES+=("\"$file\"")
done < <(git status --porcelain 2>/dev/null | awk '{print $2}')

FILES_JSON="[$(IFS=,; echo "${MODIFIED_FILES[*]}")]"

# Construct JSON payload
PAYLOAD=$(cat <<EOF
{
  "agentName": "$AGENT_NAME",
  "actionType": "$ACTION_TYPE",
  "repositoryPath": "$REPO_PATH",
  "command": "$COMMAND_STR",
  "errorMessage": $([ -n "$ERROR_MSG" ] && printf '%s' "$ERROR_MSG" | jq -R . || echo "null"),
  "workingTreeDiff": $(printf '%s' "$DIFF_STAT" | jq -R . 2>/dev/null || echo "\"$DIFF_STAT\""),
  "filesChanged": $FILES_JSON
}
EOF
)

# Stream asynchronously to Second Brain Bridge
curl -s -X POST "$BRAIN_URL/api/v1/bridge/activity" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD" >/dev/null 2>&1 &
