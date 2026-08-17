#!/usr/bin/env bash
# Second Brain — Automated Agent Hooks Installer
# Configures Git hooks and agent bridge scripts for the current repository.

set -e

REPO_PATH="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
HOOK_DIR="$REPO_PATH/.git/hooks"

echo "Installing Second Brain Agent Hooks into: $REPO_PATH"

if [ -d "$HOOK_DIR" ]; then
    cat << 'EOF' > "$HOOK_DIR/post-commit"
#!/usr/bin/env bash
BRAIN_URL="${BRAIN_URL:-http://localhost:8080}"
REPO_PATH="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
COMMIT_MSG="$(git log -1 --pretty=%B 2>/dev/null)"
COMMIT_HASH="$(git rev-parse HEAD 2>/dev/null)"

PAYLOAD=$(cat <<JSON
{
  "agentName": "git-commit-hook",
  "actionType": "GIT_COMMIT",
  "repositoryPath": "$REPO_PATH",
  "command": "git commit -m \"$COMMIT_MSG\"",
  "notes": "Commit $COMMIT_HASH: $COMMIT_MSG"
}
JSON
)

curl -s -X POST "$BRAIN_URL/api/v1/bridge/activity" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD" >/dev/null 2>&1 &
EOF
    chmod +x "$HOOK_DIR/post-commit"
    echo "✓ Installed .git/hooks/post-commit"
fi

chmod +x "$(dirname "$0")/claude_code_hook.sh"
chmod +x "$(dirname "$0")/codex_bridge_hook.sh"

echo "✓ Second Brain Agent Bridge Hooks installed successfully!"
echo "Usage:"
echo "  • Background Watcher: brain watch --agent claude-code"
echo "  • Codex Onboarding:   ./scripts/codex_bridge_hook.sh"
