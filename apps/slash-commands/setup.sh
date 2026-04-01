#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
ADMIN_USER="72057594037927937"

echo "=== Registering Slash Commands App ==="
RESULT=$(curl -s -X POST "$BASE_URL/api/app-registry/apps" \
  -H "X-Debug-User-Id: $ADMIN_USER" -H "X-Tenant-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Slash Commands",
    "slug": "slash-commands",
    "description": "/joke and /stock commands for all users",
    "webhookUrl": "http://localhost:5051/webhook",
    "appType": "USER",
    "permissions": ["READ_POSTS", "WRITE_COMMENTS", "SLASH_COMMANDS"]
  }')

APP_ID=$(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))")
API_KEY=$(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('apiKey',''))")

echo "  App ID: $APP_ID"
echo "  API Key: $API_KEY"

# Install org-wide (for all users on tenant 1)
echo ""
echo "=== Installing org-wide ==="
curl -s -X POST "$BASE_URL/api/app-registry/install" \
  -H "X-Debug-User-Id: $ADMIN_USER" -H "X-Tenant-Id: 1" \
  -H "Content-Type: application/json" \
  -d "{\"appId\": $APP_ID, \"installType\": \"ORG\", \"targetId\": 1}"

echo ""
echo "=== Done ==="
echo "Start the app with:"
echo "  APP_ID=$APP_ID API_KEY=$API_KEY python3 app.py"
echo ""
echo "Or with Docker:"
echo "  SLASH_CMD_APP_ID=$APP_ID SLASH_CMD_API_KEY=$API_KEY docker compose -f docker-compose.apps.yml up -d slash-commands"
