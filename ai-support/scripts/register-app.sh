#!/bin/bash
set -euo pipefail

# Register the AI Support app with WorkSphere social platform
# and install it on the support pages

SOCIAL_BASE="${SOCIAL_BASE:-http://localhost:8088}"
AI_SUPPORT_BASE="${AI_SUPPORT_BASE:-http://localhost:8090}"
AUTH="X-Debug-User-Id: 72057594037927937"

echo "=== Registering AI Support App with WorkSphere ==="
echo "  Social platform: $SOCIAL_BASE"
echo "  AI Support app:  $AI_SUPPORT_BASE"
echo ""

# Step 1: Register the app
echo "[1/4] Registering app..."
REGISTER_RESULT=$(curl -s -H "$AUTH" -H "Content-Type: application/json" \
  -d '{
    "name": "AI Support Bot",
    "slug": "ai-support-bot",
    "description": "AI-powered support assistant that answers questions using knowledge bases",
    "webhookUrl": "'"$AI_SUPPORT_BASE"'/api/webhook",
    "appType": "PAGE",
    "permissions": ["read:posts", "write:comments", "write:cases"]
  }' \
  "$SOCIAL_BASE/api/app-registry/apps")

APP_ID=$(echo "$REGISTER_RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('app',{}).get('id',''))" 2>/dev/null)
API_KEY=$(echo "$REGISTER_RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('apiKey',''))" 2>/dev/null)

if [ -z "$APP_ID" ] || [ "$APP_ID" = "None" ]; then
  echo "  WARN: Registration may have failed or app already exists"
  echo "  Response: $REGISTER_RESULT"
  echo "  Trying to find existing app..."
  # Try to get from the slug
  APP_ID=$(echo "$REGISTER_RESULT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('id', d.get('app',{}).get('id','')))" 2>/dev/null)
fi

echo "  App ID: $APP_ID"
echo "  API Key: ${API_KEY:0:20}..."

# Step 2: Create support pages in social app
echo ""
echo "[2/4] Creating support pages..."

for PAGE_DATA in \
  '{"name":"Amiga Support","description":"Get help with Commodore Amiga computers","visibility":"PUBLIC"}' \
  '{"name":"Gowin FPGA Support","description":"Get help with Gowin FPGA development","visibility":"PUBLIC"}' \
  '{"name":"Atari 8-bit Support","description":"Get help with Atari 8-bit computers","visibility":"PUBLIC"}' \
  '{"name":"Geek Help","description":"General tech help - we will route you to the right place","visibility":"PUBLIC"}'; do
  NAME=$(echo "$PAGE_DATA" | python3 -c "import sys,json; print(json.load(sys.stdin)['name'])")
  RESULT=$(curl -s -H "$AUTH" -H "Content-Type: application/json" -d "$PAGE_DATA" "$SOCIAL_BASE/api/pages")
  PAGE_ID=$(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)
  echo "  Page '$NAME': $PAGE_ID"
done

# Step 3: Install app on each page
echo ""
echo "[3/4] Installing app on pages..."
# Get page IDs
PAGES=$(curl -s -H "$AUTH" "$SOCIAL_BASE/api/pages/search?q=Support" 2>/dev/null)
GEEK_PAGES=$(curl -s -H "$AUTH" "$SOCIAL_BASE/api/pages/search?q=Geek" 2>/dev/null)

echo "$PAGES" | python3 -c "
import sys, json
pages = json.load(sys.stdin)
for p in pages:
    print(f'  Installing on: {p[\"name\"]} (ID: {p[\"id\"]})')
" 2>/dev/null

# Step 4: Link knowledge sets to social pages
echo ""
echo "[4/4] Linking knowledge sets to social pages..."
echo "  (This maps social page IDs to AI Support knowledge set IDs)"
echo ""
echo "  MANUAL STEP NEEDED:"
echo "  Update knowledge sets with social page IDs via:"
echo "  curl -X PUT http://localhost:8090/api/knowledge/sets/{ksId} -d '{\"socialPageId\": PAGE_ID}'"
echo ""

echo "=== Registration Complete ==="
echo ""
echo "  App ID:  $APP_ID"
echo "  API Key: $API_KEY"
echo ""
echo "  Configure AI Support app with these credentials:"
echo "  In application.yml or environment:"
echo "    aisupport.social-app.api-key=$API_KEY"
echo "    aisupport.social-app.app-id=$APP_ID"
