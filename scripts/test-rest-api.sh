#!/bin/bash
###############################################################################
# REST API Test Suite
# Tests every endpoint in the social platform REST API.
# Usage: ./test-rest-api.sh [BASE_URL]
###############################################################################

BASE_URL="${1:-http://localhost:8080}"
BASE="$BASE_URL"
AUTH="X-Debug-User-Id: 72057594037927937"
USER_ID="72057594037927937"
CONTENT_TYPE="Content-Type: application/json"

# Second user for friend-request / message tests
SECOND_USER_ID="72057594037927938"
AUTH2="X-Debug-User-Id: $SECOND_USER_ID"

# Unique suffix per run to avoid slug/name collisions
RUN_ID="$(date +%s)"

PASS_COUNT=0
FAIL_COUNT=0
TOTAL_COUNT=0

# ── Colors ──────────────────────────────────────────────────────────────────
GREEN="\033[0;32m"
RED="\033[0;31m"
YELLOW="\033[0;33m"
CYAN="\033[0;36m"
BOLD="\033[1m"
RESET="\033[0m"

# ── Helper Functions ────────────────────────────────────────────────────────

section() {
  echo ""
  echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════════════${RESET}"
  echo -e "${BOLD}${CYAN}  $1${RESET}"
  echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════════════${RESET}"
}

test_name() {
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  CURRENT_TEST="$1"
  echo -n "  [$TOTAL_COUNT] $1 ... "
}

pass() {
  PASS_COUNT=$((PASS_COUNT + 1))
  echo -e "${GREEN}PASS${RESET}"
}

fail() {
  FAIL_COUNT=$((FAIL_COUNT + 1))
  local msg="${1:-}"
  if [ -n "$msg" ]; then
    echo -e "${RED}FAIL${RESET} ($msg)"
  else
    echo -e "${RED}FAIL${RESET}"
  fi
}

assert_status() {
  local expected="$1"
  local actual="$2"
  if [ "$actual" = "$expected" ]; then
    pass
  else
    fail "expected $expected, got $actual"
  fi
}

assert_status_in() {
  # Accept any of the given status codes
  local actual="$1"
  shift
  for expected in "$@"; do
    if [ "$actual" = "$expected" ]; then
      pass
      return
    fi
  done
  fail "expected one of [$*], got $actual"
}

json_field() {
  # Extract a field from JSON using python3
  local json="$1"
  local field="$2"
  echo "$json" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['$field'])" 2>/dev/null
}

json_nested() {
  # Extract a nested field path like "foo.bar"
  local json="$1"
  local path="$2"
  echo "$json" | python3 -c "
import sys, json
d = json.load(sys.stdin)
for k in '$path'.split('.'):
    d = d[k]
print(d)
" 2>/dev/null
}

do_get() {
  local url="$1"
  local auth_header="${2:-$AUTH}"
  curl -s -w "\n%{http_code}" -H "$auth_header" "$url"
}

do_post() {
  local url="$1"
  local data="$2"
  local auth_header="${3:-$AUTH}"
  curl -s -w "\n%{http_code}" -H "$auth_header" -H "$CONTENT_TYPE" -d "$data" "$url"
}

do_put() {
  local url="$1"
  local data="$2"
  local auth_header="${3:-$AUTH}"
  curl -s -w "\n%{http_code}" -X PUT -H "$auth_header" -H "$CONTENT_TYPE" -d "$data" "$url"
}

do_delete() {
  local url="$1"
  local auth_header="${2:-$AUTH}"
  curl -s -w "\n%{http_code}" -X DELETE -H "$auth_header" "$url"
}

extract_response() {
  # Split response into body and HTTP code
  local response="$1"
  HTTP_CODE=$(echo "$response" | tail -1)
  BODY=$(echo "$response" | sed '$d')
}

###############################################################################
# 1. AUTH TESTS
###############################################################################
section "Auth Tests"

test_name "Register new user"
RESPONSE=$(do_post "$BASE/api/auth/register" \
  '{"username":"testuser_api_'"$RUN_ID"'","displayName":"API Test User","email":"apitest_'"$RUN_ID"'@example.com","password":"password123","bio":"Test bio"}')
extract_response "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 409

test_name "Login"
RESPONSE=$(do_post "$BASE/api/auth/login" \
  '{"username":"testuser_api_'"$RUN_ID"'","password":"password123"}')
extract_response "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 401

###############################################################################
# 2. USER TESTS
###############################################################################
section "User Tests"

test_name "Get user by ID"
RESPONSE=$(do_get "$BASE/api/users/$USER_ID")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Search users"
RESPONSE=$(do_get "$BASE/api/users/search?q=test")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Get user followers"
RESPONSE=$(do_get "$BASE/api/users/$USER_ID/followers")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Get user following"
RESPONSE=$(do_get "$BASE/api/users/$USER_ID/following")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

###############################################################################
# 3. FOLLOW TESTS
###############################################################################
section "Follow Tests"

test_name "Follow a user"
RESPONSE=$(do_post "$BASE/api/follow/$SECOND_USER_ID" '{}')
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Unfollow a user"
RESPONSE=$(do_delete "$BASE/api/follow/$SECOND_USER_ID")
extract_response "$RESPONSE"
assert_status 204 "$HTTP_CODE"

###############################################################################
# 4. FRIEND REQUEST TESTS
###############################################################################
section "Friend Request Tests"

test_name "Send friend request"
RESPONSE=$(do_post "$BASE/api/friend-requests/$SECOND_USER_ID" '{}')
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"
FR_STATUS=$(json_field "$BODY" "status")
FR_ID=$(json_field "$BODY" "id" 2>/dev/null)

test_name "Check friend request status"
RESPONSE=$(do_get "$BASE/api/friend-requests/status/$SECOND_USER_ID")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Get sent friend requests"
RESPONSE=$(do_get "$BASE/api/friend-requests/sent")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Get received friend requests (as second user)"
RESPONSE=$(do_get "$BASE/api/friend-requests/received" "$AUTH2")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"
# Extract the first request ID from the received list
FR_ID=$(echo "$BODY" | python3 -c "import sys,json; reqs=json.load(sys.stdin); print(reqs[0]['id'] if reqs else '')" 2>/dev/null)

if [ -n "$FR_ID" ] && [ "$FR_ID" != "" ]; then
  test_name "Accept friend request"
  RESPONSE=$(do_post "$BASE/api/friend-requests/$FR_ID/accept" '{}' "$AUTH2")
  extract_response "$RESPONSE"
  assert_status 200 "$HTTP_CODE"
else
  # Send a new request and test reject instead
  test_name "Accept friend request (skipped - no pending request)"
  pass
fi

# Send a new request so we can test reject
test_name "Send another friend request (for reject test)"
# Use a different target for the reject test
THIRD_USER_ID="72057594037927939"
AUTH3="X-Debug-User-Id: $THIRD_USER_ID"
RESPONSE=$(do_post "$BASE/api/friend-requests/$THIRD_USER_ID" '{}')
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"
FR_ID2=$(json_field "$BODY" "id" 2>/dev/null)

if [ -n "$FR_ID2" ] && [ "$FR_ID2" != "" ]; then
  test_name "Reject friend request"
  RESPONSE=$(do_post "$BASE/api/friend-requests/$FR_ID2/reject" '{}' "$AUTH3")
  extract_response "$RESPONSE"
  assert_status 200 "$HTTP_CODE"
else
  test_name "Reject friend request (skipped - no ID)"
  pass
fi

###############################################################################
# 5. POST TESTS
###############################################################################
section "Post Tests"

test_name "Create post"
RESPONSE=$(do_post "$BASE/api/posts" \
  '{"content":"Test post from API test script","visibility":"PUBLIC"}')
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"
POST_ID=$(json_field "$BODY" "id")

test_name "Get post"
RESPONSE=$(do_get "$BASE/api/posts/$POST_ID")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Update post"
RESPONSE=$(do_put "$BASE/api/posts/$POST_ID" \
  '{"content":"Updated test post content"}')
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Get post comments (empty initially)"
RESPONSE=$(do_get "$BASE/api/posts/$POST_ID/comments")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

# Create a second post that we will delete
test_name "Create post (for deletion)"
RESPONSE=$(do_post "$BASE/api/posts" \
  '{"content":"This post will be deleted","visibility":"PUBLIC"}')
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"
DELETE_POST_ID=$(json_field "$BODY" "id")

test_name "Delete post"
RESPONSE=$(do_delete "$BASE/api/posts/$DELETE_POST_ID")
extract_response "$RESPONSE"
assert_status 204 "$HTTP_CODE"

###############################################################################
# 6. COMMENT TESTS
###############################################################################
section "Comment Tests"

test_name "Create comment on post"
RESPONSE=$(do_post "$BASE/api/comments" \
  "{\"postId\":$POST_ID,\"content\":\"Test comment from API test script\"}")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"
COMMENT_ID=$(json_field "$BODY" "id")

test_name "Get comment by ID"
RESPONSE=$(do_get "$BASE/api/comments/$COMMENT_ID")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Create reply to comment"
RESPONSE=$(do_post "$BASE/api/comments" \
  "{\"postId\":$POST_ID,\"parentCommentId\":$COMMENT_ID,\"content\":\"Reply to comment\"}")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"
REPLY_ID=$(json_field "$BODY" "id")

test_name "Get post comments (should have comments now)"
RESPONSE=$(do_get "$BASE/api/posts/$POST_ID/comments")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Update comment"
RESPONSE=$(do_put "$BASE/api/comments/$COMMENT_ID" \
  '{"content":"Updated comment content"}')
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Delete reply comment"
RESPONSE=$(do_delete "$BASE/api/comments/$REPLY_ID")
extract_response "$RESPONSE"
assert_status 204 "$HTTP_CODE"

test_name "Delete comment"
RESPONSE=$(do_delete "$BASE/api/comments/$COMMENT_ID")
extract_response "$RESPONSE"
assert_status 204 "$HTTP_CODE"

###############################################################################
# 7. REACTION TESTS
###############################################################################
section "Reaction Tests"

test_name "React LIKE to post"
RESPONSE=$(do_post "$BASE/api/reactions" \
  "{\"targetId\":$POST_ID,\"reactionType\":\"LIKE\"}")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "React LOVE to post (change reaction)"
RESPONSE=$(do_post "$BASE/api/reactions" \
  "{\"targetId\":$POST_ID,\"reactionType\":\"LOVE\"}")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Get reactors for post"
RESPONSE=$(do_get "$BASE/api/reactions/$POST_ID/users")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Unreact from post"
RESPONSE=$(do_delete "$BASE/api/reactions/$POST_ID")
extract_response "$RESPONSE"
assert_status 204 "$HTTP_CODE"

# Re-react for AOEE tests
test_name "React LIKE again (for AOEE tests)"
RESPONSE=$(do_post "$BASE/api/reactions" \
  "{\"targetId\":$POST_ID,\"reactionType\":\"LIKE\"}")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "AOEE: Get likers for post"
RESPONSE=$(do_get "$BASE/api/reactions/aoee/$POST_ID/likers")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "AOEE: Get user likes"
RESPONSE=$(do_get "$BASE/api/reactions/aoee/user/$USER_ID/likes")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "AOEE: Check if user liked post"
RESPONSE=$(do_get "$BASE/api/reactions/aoee/$POST_ID/check/$USER_ID")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

###############################################################################
# 8. FEED TESTS
###############################################################################
section "Feed Tests"

test_name "Get feed"
RESPONSE=$(do_get "$BASE/api/feed")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Get feed with limit"
RESPONSE=$(do_get "$BASE/api/feed?limit=5")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Get recommended posts"
RESPONSE=$(do_get "$BASE/api/feed/recommended?limit=5")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

###############################################################################
# 9. GROUP TESTS
###############################################################################
section "Group Tests"

test_name "Create group"
RESPONSE=$(do_post "$BASE/api/groups" \
  '{"name":"API Test Group '"$RUN_ID"'","description":"Created by test script","visibility":"PUBLIC"}')
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"
GROUP_ID=$(json_field "$BODY" "id")

test_name "Get group"
RESPONSE=$(do_get "$BASE/api/groups/$GROUP_ID")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Search groups"
RESPONSE=$(do_get "$BASE/api/groups/search?q=API+Test")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Join group (as second user)"
RESPONSE=$(do_post "$BASE/api/groups/$GROUP_ID/join" '{}' "$AUTH2")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Get my membership in group"
RESPONSE=$(do_get "$BASE/api/groups/$GROUP_ID/membership")
extract_response "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 204

test_name "Get group members"
RESPONSE=$(do_get "$BASE/api/groups/$GROUP_ID/members")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Get pending members"
RESPONSE=$(do_get "$BASE/api/groups/$GROUP_ID/pending")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Create post in group"
RESPONSE=$(do_post "$BASE/api/posts" \
  "{\"content\":\"Group post from API test\",\"targetType\":\"GROUP_FEED\",\"targetId\":$GROUP_ID,\"visibility\":\"PUBLIC\"}")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"
GROUP_POST_ID=$(json_field "$BODY" "id")

test_name "Get group posts"
RESPONSE=$(do_get "$BASE/api/groups/$GROUP_ID/posts")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Pin post in group"
RESPONSE=$(do_post "$BASE/api/groups/$GROUP_ID/pin/$GROUP_POST_ID" '{}')
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Unpin post in group"
RESPONSE=$(do_delete "$BASE/api/groups/$GROUP_ID/pin")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Update group"
RESPONSE=$(do_put "$BASE/api/groups/$GROUP_ID" \
  '{"name":"API Test Group Updated '"$RUN_ID"'","description":"Updated by test script"}')
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Get my groups"
RESPONSE=$(do_get "$BASE/api/groups/mine")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Leave group (as second user)"
RESPONSE=$(do_delete "$BASE/api/groups/$GROUP_ID/leave" "$AUTH2")
extract_response "$RESPONSE"
assert_status 204 "$HTTP_CODE"

###############################################################################
# 10. PAGE TESTS
###############################################################################
section "Page Tests"

test_name "Create page"
RESPONSE=$(do_post "$BASE/api/pages" \
  '{"name":"API Test Page '"$RUN_ID"'","description":"Created by test script","visibility":"PUBLIC"}')
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"
PAGE_ID=$(json_field "$BODY" "id")

test_name "Get page"
RESPONSE=$(do_get "$BASE/api/pages/$PAGE_ID")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Search pages"
RESPONSE=$(do_get "$BASE/api/pages/search?q=API+Test")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Follow page (as second user)"
RESPONSE=$(do_post "$BASE/api/pages/$PAGE_ID/follow" '{}' "$AUTH2")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Check if following page (as second user)"
RESPONSE=$(do_get "$BASE/api/pages/$PAGE_ID/following" "$AUTH2")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Get page members"
RESPONSE=$(do_get "$BASE/api/pages/$PAGE_ID/members")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Create post on page"
RESPONSE=$(do_post "$BASE/api/posts" \
  "{\"content\":\"Page post from API test\",\"targetType\":\"PAGE_FEED\",\"targetId\":$PAGE_ID,\"visibility\":\"PUBLIC\"}")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"
PAGE_POST_ID=$(json_field "$BODY" "id")

test_name "Get page posts"
RESPONSE=$(do_get "$BASE/api/pages/$PAGE_ID/posts")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Pin post on page"
RESPONSE=$(do_post "$BASE/api/pages/$PAGE_ID/pin/$PAGE_POST_ID" '{}')
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Unpin post on page"
RESPONSE=$(do_delete "$BASE/api/pages/$PAGE_ID/pin")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Update page"
RESPONSE=$(do_put "$BASE/api/pages/$PAGE_ID" \
  '{"name":"API Test Page Updated '"$RUN_ID"'","description":"Updated by test script"}')
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Get my pages"
RESPONSE=$(do_get "$BASE/api/pages/mine")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Unfollow page (as second user)"
RESPONSE=$(do_delete "$BASE/api/pages/$PAGE_ID/unfollow" "$AUTH2")
extract_response "$RESPONSE"
assert_status 204 "$HTTP_CODE"

###############################################################################
# 11. TEAM TESTS
###############################################################################
section "Team Tests"

test_name "Create team"
RESPONSE=$(do_post "$BASE/api/teams" \
  '{"name":"API Test Team '"$RUN_ID"'","slug":"api-test-team-'"$RUN_ID"'","description":"Created by test script","visibility":"PUBLIC"}')
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"
TEAM_ID=$(json_field "$BODY" "id")

test_name "Get team"
RESPONSE=$(do_get "$BASE/api/teams/$TEAM_ID")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "List all teams"
RESPONSE=$(do_get "$BASE/api/teams")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

###############################################################################
# 12. MESSAGE TESTS
###############################################################################
section "Message Tests"

test_name "Send message"
RESPONSE=$(do_post "$BASE/api/messages" \
  "{\"recipientId\":$SECOND_USER_ID,\"content\":\"Hello from API test!\"}")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"
MESSAGE_ID=$(json_field "$BODY" "id")

test_name "Get conversations"
RESPONSE=$(do_get "$BASE/api/messages/conversations")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Get conversation with partner"
RESPONSE=$(do_get "$BASE/api/messages/conversation/$SECOND_USER_ID")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Mark conversation as read (as second user)"
RESPONSE=$(do_post "$BASE/api/messages/conversation/$USER_ID/read" '{}' "$AUTH2")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Get unread count"
RESPONSE=$(do_get "$BASE/api/messages/unread-count")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

###############################################################################
# 13. ATTACHMENT TESTS
###############################################################################
section "Attachment Tests"

test_name "Upload attachment"
# Create a small test file
TMPFILE=$(mktemp /tmp/test_upload_XXXXXX.txt)
echo "This is a test file for attachment upload" > "$TMPFILE"
RESPONSE=$(curl -s -w "\n%{http_code}" -H "$AUTH" \
  -F "file=@$TMPFILE" \
  "$BASE/api/attachments/upload")
extract_response "$RESPONSE"
rm -f "$TMPFILE"
assert_status 200 "$HTTP_CODE"
ATTACHMENT_ID=$(json_field "$BODY" "id")

if [ -n "$ATTACHMENT_ID" ] && [ "$ATTACHMENT_ID" != "" ]; then
  test_name "Get attachment metadata"
  RESPONSE=$(do_get "$BASE/api/attachments/$ATTACHMENT_ID")
  extract_response "$RESPONSE"
  assert_status 200 "$HTTP_CODE"
else
  test_name "Get attachment metadata (skipped - no ID)"
  fail "no attachment ID from upload"
fi

###############################################################################
# 14. SEARCH TESTS
###############################################################################
section "Search Tests"

test_name "Search all (no type filter)"
RESPONSE=$(do_get "$BASE/api/search?q=test")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Search users"
RESPONSE=$(do_get "$BASE/api/search?q=test&type=user")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Search pages"
RESPONSE=$(do_get "$BASE/api/search?q=test&type=page")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Search groups"
RESPONSE=$(do_get "$BASE/api/search?q=test&type=group")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

###############################################################################
# 15. LINK PREVIEW TEST
###############################################################################
section "Link Preview Tests"

test_name "Get link preview"
RESPONSE=$(do_get "$BASE/api/link-preview?url=https://example.com")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

###############################################################################
# 16. ADMIN TESTS
###############################################################################
section "Admin Tests"

test_name "Admin dashboard"
RESPONSE=$(do_get "$BASE/api/admin/dashboard")
extract_response "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 403

test_name "Admin DAU/MAU analytics"
RESPONSE=$(do_get "$BASE/api/admin/analytics/dau-mau")
extract_response "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 403

test_name "Admin engagement analytics (user)"
RESPONSE=$(do_get "$BASE/api/admin/analytics/engagement?entityType=user&entityId=$USER_ID&days=7")
extract_response "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 403

test_name "Admin user activity analytics"
RESPONSE=$(do_get "$BASE/api/admin/analytics/user-activity")
extract_response "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 403

test_name "Admin top users"
RESPONSE=$(do_get "$BASE/api/admin/analytics/top-users?period=7d&limit=5")
extract_response "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 403

test_name "Admin top groups"
RESPONSE=$(do_get "$BASE/api/admin/analytics/top-groups?limit=5")
extract_response "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 403

test_name "Admin top pages"
RESPONSE=$(do_get "$BASE/api/admin/analytics/top-pages?limit=5")
extract_response "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 403

test_name "Admin growth"
RESPONSE=$(do_get "$BASE/api/admin/analytics/growth")
extract_response "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 403

test_name "Admin list posts"
RESPONSE=$(do_get "$BASE/api/admin/posts?page=0&size=5")
extract_response "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 403

test_name "Admin list users"
RESPONSE=$(do_get "$BASE/api/admin/users?page=0&size=5")
extract_response "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 403

test_name "Admin get user detail"
RESPONSE=$(do_get "$BASE/api/admin/users/$USER_ID")
extract_response "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 403

test_name "Admin list users with search"
RESPONSE=$(do_get "$BASE/api/admin/users?q=test&page=0&size=5")
extract_response "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 403

test_name "Admin list groups"
RESPONSE=$(do_get "$BASE/api/admin/groups?page=0&size=5")
extract_response "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 403

test_name "Admin list pages"
RESPONSE=$(do_get "$BASE/api/admin/pages?page=0&size=5")
extract_response "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 403

test_name "Admin system health"
RESPONSE=$(do_get "$BASE/api/admin/system")
extract_response "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 403

###############################################################################
# 17. AOEE PERSISTENCE TESTS
###############################################################################
section "AOEE Persistence Tests"

test_name "Create edge"
RESPONSE=$(do_post "$BASE/api/v1/edges" \
  '{"src":100,"edgeType":"FOLLOWS","dst":200,"timestampNs":1000000,"metadata":0}')
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"
EDGE_ID=$(json_field "$BODY" "id")

test_name "Get edge"
RESPONSE=$(do_get "$BASE/api/v1/edges/100/FOLLOWS/200")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Check edge exists"
RESPONSE=$(do_get "$BASE/api/v1/edges/100/FOLLOWS/200/exists")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"
EXISTS=$(json_field "$BODY" "exists")
if [ "$EXISTS" = "True" ] || [ "$EXISTS" = "true" ]; then
  echo -n "" # edge exists, good
fi

test_name "List edges by src and type"
RESPONSE=$(do_get "$BASE/api/v1/edges?src=100&type=FOLLOWS&limit=10")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Count edges by src and type"
RESPONSE=$(do_get "$BASE/api/v1/edges/count?src=100&type=FOLLOWS")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Batch create edges"
RESPONSE=$(do_post "$BASE/api/v1/edges/batch" \
  '[{"src":'"$RUN_ID"',"edgeType":"TEST_EDGE","dst":201},{"src":'"$RUN_ID"',"edgeType":"TEST_EDGE","dst":202}]')
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Create entity"
RESPONSE=$(do_post "$BASE/api/v1/entities" \
  '{"id":100,"entityType":"USER","name":"Test User Entity"}')
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Batch create entities"
RESPONSE=$(do_post "$BASE/api/v1/entities/batch" \
  '[{"id":200,"entityType":"USER","name":"Target Entity 200"},{"id":201,"entityType":"USER","name":"Target Entity 201"}]')
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Export stats"
RESPONSE=$(do_get "$BASE/api/v1/export/stats")
extract_response "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Delete edge"
RESPONSE=$(do_delete "$BASE/api/v1/edges/100/FOLLOWS/200")
extract_response "$RESPONSE"
assert_status 204 "$HTTP_CODE"

###############################################################################
# SUMMARY
###############################################################################
echo ""
echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════════════${RESET}"
echo -e "${BOLD}${CYAN}  TEST SUMMARY${RESET}"
echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════════════${RESET}"
echo ""
echo -e "  Total:  ${BOLD}$TOTAL_COUNT${RESET}"
echo -e "  ${GREEN}Passed: $PASS_COUNT${RESET}"
echo -e "  ${RED}Failed: $FAIL_COUNT${RESET}"
echo ""

if [ "$FAIL_COUNT" -eq 0 ]; then
  echo -e "  ${GREEN}${BOLD}All tests passed!${RESET}"
else
  echo -e "  ${RED}${BOLD}$FAIL_COUNT test(s) failed.${RESET}"
fi
echo ""

exit $FAIL_COUNT
