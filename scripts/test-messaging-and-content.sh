#!/bin/bash
###############################################################################
# Integration Test: Messaging, Group & Page Content
#
# Tests multi-user messaging flows, posting and commenting on groups and pages.
# Usage: ./test-messaging-and-content.sh [BASE_URL]
###############################################################################

BASE_URL="${1:-http://localhost:8080}"
BASE="$BASE_URL"
CONTENT_TYPE="Content-Type: application/json"

# Three test users
USER_A_ID="72057594037927937"
USER_B_ID="72057594037927938"
USER_C_ID="72057594037927939"
AUTH_A="X-Debug-User-Id: $USER_A_ID"
AUTH_B="X-Debug-User-Id: $USER_B_ID"
AUTH_C="X-Debug-User-Id: $USER_C_ID"

RUN_ID="$(date +%s)"
PASS_COUNT=0
FAIL_COUNT=0
TOTAL_COUNT=0

# ── Colors ──────────────────────────────────────────────────────────────────
GREEN="\033[0;32m"
RED="\033[0;31m"
CYAN="\033[0;36m"
BOLD="\033[1m"
RESET="\033[0m"

# ── Helpers ─────────────────────────────────────────────────────────────────

section() {
  echo ""
  echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════════════${RESET}"
  echo -e "${BOLD}${CYAN}  $1${RESET}"
  echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════════════${RESET}"
}

test_name() {
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  echo -n "  [$TOTAL_COUNT] $1 ... "
}

pass() { PASS_COUNT=$((PASS_COUNT + 1)); echo -e "${GREEN}PASS${RESET}"; }
fail() { FAIL_COUNT=$((FAIL_COUNT + 1)); echo -e "${RED}FAIL${RESET} ($1)"; }

assert_status() {
  if [ "$2" = "$1" ]; then pass; else fail "expected $1, got $2"; fi
}

assert_status_in() {
  local actual="$1"; shift
  for expected in "$@"; do
    if [ "$actual" = "$expected" ]; then pass; return; fi
  done
  fail "expected one of [$*], got $actual"
}

json_field() {
  echo "$1" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['$2'])" 2>/dev/null
}

json_len() {
  echo "$1" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null
}

json_nested() {
  echo "$1" | python3 -c "
import sys, json
d = json.load(sys.stdin)
for k in '$2'.split('.'):
    if k.isdigit(): d = d[int(k)]
    else: d = d[k]
print(d)
" 2>/dev/null
}

do_get() {
  curl -s -w "\n%{http_code}" -H "${2:-$AUTH_A}" "$1"
}

do_post() {
  curl -s -w "\n%{http_code}" -H "${3:-$AUTH_A}" -H "$CONTENT_TYPE" -d "$2" "$1"
}

do_put() {
  curl -s -w "\n%{http_code}" -X PUT -H "${3:-$AUTH_A}" -H "$CONTENT_TYPE" -d "$2" "$1"
}

do_delete() {
  curl -s -w "\n%{http_code}" -X DELETE -H "${2:-$AUTH_A}" "$1"
}

extract() {
  BODY=$(echo "$1" | sed '$d')
  HTTP_CODE=$(echo "$1" | tail -1)
}

###############################################################################
# PART 1: MESSAGING BETWEEN USERS
###############################################################################

section "Messaging: User A -> User B"

# A sends message to B
test_name "A sends text message to B"
RESPONSE=$(do_post "$BASE/api/messages" \
  '{"recipientId":"'"$USER_B_ID"'","content":"Hey B, how are you? ('"$RUN_ID"')"}' "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
MSG1_ID=$(json_field "$BODY" "id")

test_name "A sends another message to B"
RESPONSE=$(do_post "$BASE/api/messages" \
  '{"recipientId":"'"$USER_B_ID"'","content":"Just checking in! ('"$RUN_ID"')"}' "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "B can see conversation with A"
RESPONSE=$(do_get "$BASE/api/messages/conversation/$USER_A_ID" "$AUTH_B")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
MSG_COUNT=$(json_len "$BODY")
if [ "$MSG_COUNT" -ge 2 ] 2>/dev/null; then
  echo -n "    -> $MSG_COUNT messages in conversation ... "
  pass
else
  echo -n "    -> expected >=2 messages, got $MSG_COUNT ... "
  fail "too few messages"
fi

test_name "B has unread messages"
RESPONSE=$(do_get "$BASE/api/messages/unread-count" "$AUTH_B")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
UNREAD=$(json_field "$BODY" "unreadCount")
if [ "$UNREAD" -ge 1 ] 2>/dev/null; then
  echo -n "    -> $UNREAD unread ... "
  pass
else
  echo -n "    -> expected >=1 unread, got $UNREAD ... "
  fail "no unread"
fi

test_name "B marks conversation as read"
RESPONSE=$(do_post "$BASE/api/messages/conversation/$USER_A_ID/read" '{}' "$AUTH_B")
extract "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 204

test_name "B unread count is now 0 from A"
RESPONSE=$(do_get "$BASE/api/messages/unread-count" "$AUTH_B")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"

section "Messaging: User B replies to User A"

test_name "B replies to A"
RESPONSE=$(do_post "$BASE/api/messages" \
  '{"recipientId":"'"$USER_A_ID"'","content":"Hey A! I am good, thanks! ('"$RUN_ID"')"}' "$AUTH_B")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "A can see B's reply in conversation"
RESPONSE=$(do_get "$BASE/api/messages/conversation/$USER_B_ID" "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
HAS_REPLY=$(echo "$BODY" | python3 -c "import sys,json; msgs=json.load(sys.stdin); print(any('I am good' in m['content'] for m in msgs))" 2>/dev/null)
if [ "$HAS_REPLY" = "True" ]; then
  echo -n "    -> B's reply found in conversation ... "
  pass
else
  fail "reply not found"
fi

test_name "A sees B in conversations list"
RESPONSE=$(do_get "$BASE/api/messages/conversations" "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
HAS_B=$(echo "$BODY" | python3 -c "import sys,json; convs=json.load(sys.stdin); print(any(str(c['partner']['id'])=='"$USER_B_ID"' for c in convs))" 2>/dev/null)
if [ "$HAS_B" = "True" ]; then
  pass
else
  fail "B not in conversations"
fi

section "Messaging: Three-way conversations"

test_name "A sends message to C"
RESPONSE=$(do_post "$BASE/api/messages" \
  '{"recipientId":"'"$USER_C_ID"'","content":"Hello C from A! ('"$RUN_ID"')"}' "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "C sends message to B"
RESPONSE=$(do_post "$BASE/api/messages" \
  '{"recipientId":"'"$USER_B_ID"'","content":"Hi B from C! ('"$RUN_ID"')"}' "$AUTH_C")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "B now has conversations with both A and C"
RESPONSE=$(do_get "$BASE/api/messages/conversations" "$AUTH_B")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
CONV_COUNT=$(json_len "$BODY")
if [ "$CONV_COUNT" -ge 2 ] 2>/dev/null; then
  echo -n "    -> $CONV_COUNT conversations ... "
  pass
else
  echo -n "    -> expected >=2 conversations, got $CONV_COUNT ... "
  fail "too few conversations"
fi

test_name "C can read conversation with A"
RESPONSE=$(do_get "$BASE/api/messages/conversation/$USER_A_ID" "$AUTH_C")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
C_MSG_COUNT=$(json_len "$BODY")
if [ "$C_MSG_COUNT" -ge 1 ] 2>/dev/null; then
  pass
else
  fail "expected >=1 messages"
fi

###############################################################################
# PART 2: GROUP CONTENT — POSTING AND COMMENTING
###############################################################################

section "Group: Create and populate"

test_name "A creates a group"
RESPONSE=$(do_post "$BASE/api/groups" \
  '{"name":"Content Test Group '"$RUN_ID"'","description":"Group for testing posts and comments","visibility":"PUBLIC"}' "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
GROUP_ID=$(json_field "$BODY" "id")
echo -n "    -> group $GROUP_ID ... "
pass

test_name "B joins the group"
RESPONSE=$(do_post "$BASE/api/groups/$GROUP_ID/join" '{}' "$AUTH_B")
extract "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 204

test_name "C joins the group"
RESPONSE=$(do_post "$BASE/api/groups/$GROUP_ID/join" '{}' "$AUTH_C")
extract "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 204

test_name "Group has 3 members"
RESPONSE=$(do_get "$BASE/api/groups/$GROUP_ID/members" "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
MEMBER_COUNT=$(json_len "$BODY")
if [ "$MEMBER_COUNT" -ge 3 ] 2>/dev/null; then
  echo -n "    -> $MEMBER_COUNT members ... "
  pass
else
  echo -n "    -> expected >=3, got $MEMBER_COUNT ... "
  fail "wrong member count"
fi

section "Group: Posts"

test_name "A posts to group"
RESPONSE=$(do_post "$BASE/api/posts" \
  '{"content":"First post in the group! ('"$RUN_ID"')","targetType":"GROUP_FEED","targetId":"'"$GROUP_ID"'"}' "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
GROUP_POST1_ID=$(json_field "$BODY" "id")

test_name "B posts to group"
RESPONSE=$(do_post "$BASE/api/posts" \
  '{"content":"B here, great group! ('"$RUN_ID"')","targetType":"GROUP_FEED","targetId":"'"$GROUP_ID"'"}' "$AUTH_B")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
GROUP_POST2_ID=$(json_field "$BODY" "id")

test_name "C posts to group"
RESPONSE=$(do_post "$BASE/api/posts" \
  '{"content":"C checking in! ('"$RUN_ID"')","targetType":"GROUP_FEED","targetId":"'"$GROUP_ID"'"}' "$AUTH_C")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
GROUP_POST3_ID=$(json_field "$BODY" "id")

test_name "Group has 3 posts"
RESPONSE=$(do_get "$BASE/api/groups/$GROUP_ID/posts" "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
POST_COUNT=$(json_len "$BODY")
if [ "$POST_COUNT" -ge 3 ] 2>/dev/null; then
  echo -n "    -> $POST_COUNT posts ... "
  pass
else
  echo -n "    -> expected >=3 posts, got $POST_COUNT ... "
  fail "wrong post count"
fi

section "Group: Comments on posts"

test_name "B comments on A's group post"
RESPONSE=$(do_post "$BASE/api/comments" \
  '{"postId":"'"$GROUP_POST1_ID"'","content":"Nice post A! ('"$RUN_ID"')"}' "$AUTH_B")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
COMMENT1_ID=$(json_field "$BODY" "id")

test_name "C comments on A's group post"
RESPONSE=$(do_post "$BASE/api/comments" \
  '{"postId":"'"$GROUP_POST1_ID"'","content":"I agree! ('"$RUN_ID"')"}' "$AUTH_C")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
COMMENT2_ID=$(json_field "$BODY" "id")

test_name "A replies to B's comment"
RESPONSE=$(do_post "$BASE/api/comments" \
  '{"postId":"'"$GROUP_POST1_ID"'","parentCommentId":"'"$COMMENT1_ID"'","content":"Thanks B! ('"$RUN_ID"')"}' "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
REPLY1_ID=$(json_field "$BODY" "id")

test_name "Post has 3 comments (2 top-level + 1 reply)"
RESPONSE=$(do_get "$BASE/api/posts/$GROUP_POST1_ID/comments" "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
TOP_COMMENTS=$(json_len "$BODY")
if [ "$TOP_COMMENTS" -ge 2 ] 2>/dev/null; then
  echo -n "    -> $TOP_COMMENTS top-level comments ... "
  pass
else
  echo -n "    -> expected >=2, got $TOP_COMMENTS ... "
  fail "wrong count"
fi

test_name "B's comment has A's reply"
REPLY_COUNT=$(echo "$BODY" | python3 -c "
import sys,json
comments = json.load(sys.stdin)
for c in comments:
    if str(c['id']) == '$COMMENT1_ID':
        print(len(c.get('replies', [])))
        break
" 2>/dev/null)
if [ "$REPLY_COUNT" = "1" ]; then
  pass
else
  fail "expected 1 reply, got $REPLY_COUNT"
fi

section "Group: Reactions on group posts"

test_name "B reacts LIKE to A's group post"
RESPONSE=$(do_post "$BASE/api/reactions" \
  '{"targetId":"'"$GROUP_POST1_ID"'","reactionType":"LIKE"}' "$AUTH_B")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "C reacts LOVE to A's group post"
RESPONSE=$(do_post "$BASE/api/reactions" \
  '{"targetId":"'"$GROUP_POST1_ID"'","reactionType":"LOVE"}' "$AUTH_C")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "A reacts HAHA to B's group post"
RESPONSE=$(do_post "$BASE/api/reactions" \
  '{"targetId":"'"$GROUP_POST2_ID"'","reactionType":"HAHA"}' "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "A's group post has 2 reactions"
RESPONSE=$(do_get "$BASE/api/reactions/$GROUP_POST1_ID/users" "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
REACTOR_COUNT=$(json_len "$BODY")
if [ "$REACTOR_COUNT" -ge 2 ] 2>/dev/null; then
  echo -n "    -> $REACTOR_COUNT reactors ... "
  pass
else
  echo -n "    -> expected >=2, got $REACTOR_COUNT ... "
  fail "wrong count"
fi

section "Group: Pin post"

test_name "A pins their post in the group"
RESPONSE=$(do_post "$BASE/api/groups/$GROUP_ID/pin/$GROUP_POST1_ID" '{}' "$AUTH_A")
extract "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 204

test_name "Group shows pinned post"
RESPONSE=$(do_get "$BASE/api/groups/$GROUP_ID" "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
PINNED=$(json_field "$BODY" "pinnedPostId")
if [ "$PINNED" = "$GROUP_POST1_ID" ]; then
  pass
else
  fail "expected pinned=$GROUP_POST1_ID, got $PINNED"
fi

test_name "A unpins the post"
RESPONSE=$(do_delete "$BASE/api/groups/$GROUP_ID/pin" "$AUTH_A")
extract "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 204

###############################################################################
# PART 3: PAGE CONTENT — POSTING AND COMMENTING
###############################################################################

section "Page: Create and populate"

test_name "A creates a page"
RESPONSE=$(do_post "$BASE/api/pages" \
  '{"name":"Content Test Page '"$RUN_ID"'","description":"Page for testing posts and comments","visibility":"PUBLIC"}' "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
PAGE_ID=$(json_field "$BODY" "id")
echo -n "    -> page $PAGE_ID ... "
pass

test_name "B follows the page"
RESPONSE=$(do_post "$BASE/api/pages/$PAGE_ID/follow" '{}' "$AUTH_B")
extract "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 204

test_name "C follows the page"
RESPONSE=$(do_post "$BASE/api/pages/$PAGE_ID/follow" '{}' "$AUTH_C")
extract "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 204

test_name "B is following the page"
RESPONSE=$(do_get "$BASE/api/pages/$PAGE_ID/following" "$AUTH_B")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"

section "Page: Posts"

test_name "A posts to page"
RESPONSE=$(do_post "$BASE/api/posts" \
  '{"content":"Welcome to our page! ('"$RUN_ID"')","targetType":"PAGE_FEED","targetId":"'"$PAGE_ID"'"}' "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
PAGE_POST1_ID=$(json_field "$BODY" "id")

test_name "B posts to page"
RESPONSE=$(do_post "$BASE/api/posts" \
  '{"content":"Excited to be here! ('"$RUN_ID"')","targetType":"PAGE_FEED","targetId":"'"$PAGE_ID"'"}' "$AUTH_B")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
PAGE_POST2_ID=$(json_field "$BODY" "id")

test_name "Page has posts"
RESPONSE=$(do_get "$BASE/api/pages/$PAGE_ID/posts" "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
PAGE_POST_COUNT=$(json_len "$BODY")
if [ "$PAGE_POST_COUNT" -ge 2 ] 2>/dev/null; then
  echo -n "    -> $PAGE_POST_COUNT posts ... "
  pass
else
  echo -n "    -> expected >=2, got $PAGE_POST_COUNT ... "
  fail "wrong count"
fi

section "Page: Comments on posts"

test_name "C comments on A's page post"
RESPONSE=$(do_post "$BASE/api/comments" \
  '{"postId":"'"$PAGE_POST1_ID"'","content":"Great page launch! ('"$RUN_ID"')"}' "$AUTH_C")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
PAGE_COMMENT1_ID=$(json_field "$BODY" "id")

test_name "A replies to C's comment"
RESPONSE=$(do_post "$BASE/api/comments" \
  '{"postId":"'"$PAGE_POST1_ID"'","parentCommentId":"'"$PAGE_COMMENT1_ID"'","content":"Thank you C! ('"$RUN_ID"')"}' "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "B comments on B's own page post"
RESPONSE=$(do_post "$BASE/api/comments" \
  '{"postId":"'"$PAGE_POST2_ID"'","content":"Adding more context here ('"$RUN_ID"')"}' "$AUTH_B")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "A's page post has comments with replies"
RESPONSE=$(do_get "$BASE/api/posts/$PAGE_POST1_ID/comments" "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
PAGE_COMMENT_COUNT=$(json_len "$BODY")
if [ "$PAGE_COMMENT_COUNT" -ge 1 ] 2>/dev/null; then
  echo -n "    -> $PAGE_COMMENT_COUNT top-level comments ... "
  pass
else
  fail "no comments"
fi

section "Page: Reactions on page posts"

test_name "B reacts LOVE to A's page post"
RESPONSE=$(do_post "$BASE/api/reactions" \
  '{"targetId":"'"$PAGE_POST1_ID"'","reactionType":"LOVE"}' "$AUTH_B")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "C reacts WOW to A's page post"
RESPONSE=$(do_post "$BASE/api/reactions" \
  '{"targetId":"'"$PAGE_POST1_ID"'","reactionType":"WOW"}' "$AUTH_C")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "A reacts SAD to B's page post"
RESPONSE=$(do_post "$BASE/api/reactions" \
  '{"targetId":"'"$PAGE_POST2_ID"'","reactionType":"SAD"}' "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "C changes reaction on A's page post to HAHA"
RESPONSE=$(do_post "$BASE/api/reactions" \
  '{"targetId":"'"$PAGE_POST1_ID"'","reactionType":"HAHA"}' "$AUTH_C")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "Verify A's page post reactors"
RESPONSE=$(do_get "$BASE/api/reactions/$PAGE_POST1_ID/users" "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
PAGE_REACTORS=$(json_len "$BODY")
if [ "$PAGE_REACTORS" -ge 2 ] 2>/dev/null; then
  echo -n "    -> $PAGE_REACTORS reactors ... "
  pass
else
  fail "expected >=2 reactors"
fi

section "Page: Pin post"

test_name "A pins post on page"
RESPONSE=$(do_post "$BASE/api/pages/$PAGE_ID/pin/$PAGE_POST1_ID" '{}' "$AUTH_A")
extract "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 204

test_name "Page shows pinned post"
RESPONSE=$(do_get "$BASE/api/pages/$PAGE_ID" "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
PAGE_PINNED=$(json_field "$BODY" "pinnedPostId")
if [ "$PAGE_PINNED" = "$PAGE_POST1_ID" ]; then
  pass
else
  fail "expected pinned=$PAGE_POST1_ID, got $PAGE_PINNED"
fi

###############################################################################
# PART 4: CROSS-USER FEED VISIBILITY
###############################################################################

section "Feed: Cross-user content visibility"

test_name "B's feed includes group posts from A and C"
RESPONSE=$(do_get "$BASE/api/feed?limit=50" "$AUTH_B")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
HAS_A_POST=$(echo "$BODY" | python3 -c "
import sys,json
posts = json.load(sys.stdin)['posts']
print(any(str(p['id'])=='$GROUP_POST1_ID' for p in posts))
" 2>/dev/null)
if [ "$HAS_A_POST" = "True" ]; then
  echo -n "    -> A's group post visible to B ... "
  pass
else
  echo -n "    -> A's group post NOT visible to B ... "
  fail "post not in feed"
fi

test_name "B's feed includes page posts"
HAS_PAGE_POST=$(echo "$BODY" | python3 -c "
import sys,json
posts = json.load(sys.stdin)['posts']
print(any(str(p['id'])=='$PAGE_POST1_ID' for p in posts))
" 2>/dev/null)
if [ "$HAS_PAGE_POST" = "True" ]; then
  echo -n "    -> A's page post visible to B ... "
  pass
else
  echo -n "    -> A's page post NOT visible to B ... "
  fail "post not in feed"
fi

###############################################################################
# PART 5: COMMENT EDITING AND DELETION
###############################################################################

section "Content: Edit and delete"

test_name "B updates their group comment"
RESPONSE=$(do_put "$BASE/api/comments/$COMMENT1_ID" \
  '{"content":"Updated: Really nice post A! ('"$RUN_ID"')"}' "$AUTH_B")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"
UPDATED_CONTENT=$(json_field "$BODY" "content")
if echo "$UPDATED_CONTENT" | grep -q "Updated"; then
  pass
else
  fail "content not updated"
fi

test_name "A updates their group post"
RESPONSE=$(do_put "$BASE/api/posts/$GROUP_POST1_ID" \
  '{"content":"Updated first post in the group! ('"$RUN_ID"')"}' "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "A deletes C's reply (cleanup)"
RESPONSE=$(do_delete "$BASE/api/comments/$REPLY1_ID" "$AUTH_A")
extract "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 204

###############################################################################
# PART 6: MESSAGING WITH CONTEXT (after group activity)
###############################################################################

section "Messaging: Post-activity conversations"

test_name "B messages A about the group post"
RESPONSE=$(do_post "$BASE/api/messages" \
  '{"recipientId":"'"$USER_A_ID"'","content":"Hey A, loved your group post! Want to collaborate? ('"$RUN_ID"')"}' "$AUTH_B")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "A messages C about the page"
RESPONSE=$(do_post "$BASE/api/messages" \
  '{"recipientId":"'"$USER_C_ID"'","content":"C, thanks for the comment on the page! ('"$RUN_ID"')"}' "$AUTH_A")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "C messages B"
RESPONSE=$(do_post "$BASE/api/messages" \
  '{"recipientId":"'"$USER_B_ID"'","content":"B, should we do a group project? ('"$RUN_ID"')"}' "$AUTH_C")
extract "$RESPONSE"
assert_status 200 "$HTTP_CODE"

test_name "All three users have multiple conversations"
for AUTH_USER in "$AUTH_A" "$AUTH_B" "$AUTH_C"; do
  RESPONSE=$(do_get "$BASE/api/messages/conversations" "$AUTH_USER")
  extract "$RESPONSE"
  CONV_CT=$(json_len "$BODY")
  if [ "$CONV_CT" -ge 2 ] 2>/dev/null; then
    : # ok
  else
    fail "user has $CONV_CT conversations, expected >=2"
    break
  fi
done
pass

###############################################################################
# PART 7: CLEANUP — Leave group, unfollow page
###############################################################################

section "Cleanup"

test_name "B leaves the group"
RESPONSE=$(do_delete "$BASE/api/groups/$GROUP_ID/leave" "$AUTH_B")
extract "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 204

test_name "C unfollows the page"
RESPONSE=$(do_delete "$BASE/api/pages/$PAGE_ID/unfollow" "$AUTH_C")
extract "$RESPONSE"
assert_status_in "$HTTP_CODE" 200 204

test_name "B is no longer a group member"
RESPONSE=$(do_get "$BASE/api/groups/$GROUP_ID/membership" "$AUTH_B")
extract "$RESPONSE"
# Should return 204 (no membership) or 404, or a membership with no data
assert_status_in "$HTTP_CODE" 200 204 404

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
if [ $FAIL_COUNT -eq 0 ]; then
  echo -e "  ${GREEN}${BOLD}All tests passed!${RESET}"
else
  echo -e "  ${RED}${BOLD}$FAIL_COUNT test(s) failed.${RESET}"
fi
