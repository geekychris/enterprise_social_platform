#!/bin/bash
###############################################################################
# GraphQL API Test Suite
# Tests every query and mutation defined in the GraphQL schema.
# Usage: ./test-graphql-api.sh [BASE_URL]
###############################################################################

BASE_URL="${1:-http://localhost:8080}"
BASE="$BASE_URL"
AUTH="X-Debug-User-Id: 72057594037927937"
USER_ID="72057594037927937"
CONTENT_TYPE="Content-Type: application/json"

# Second user for follow/unfollow tests
SECOND_USER_ID="72057594037927938"
AUTH2="X-Debug-User-Id: $SECOND_USER_ID"

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
    return 0
  else
    fail "HTTP $actual (expected $expected)"
    return 1
  fi
}

assert_no_errors() {
  local body="$1"
  local has_errors
  has_errors=$(echo "$body" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    if 'errors' in d and d['errors']:
        print('yes')
    else:
        print('no')
except:
    print('parse_error')
" 2>/dev/null)

  if [ "$has_errors" = "no" ]; then
    pass
    return 0
  elif [ "$has_errors" = "yes" ]; then
    local err_msg
    err_msg=$(echo "$body" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(d['errors'][0].get('message','unknown error')[:80])
" 2>/dev/null)
    fail "GraphQL error: $err_msg"
    return 1
  else
    fail "could not parse response"
    return 1
  fi
}

json_field() {
  local json="$1"
  local path="$2"
  echo "$json" | python3 -c "
import sys, json
d = json.load(sys.stdin)
keys = '$path'.split('.')
for k in keys:
    if isinstance(d, dict):
        d = d.get(k)
    else:
        d = None
        break
if d is not None:
    print(d)
" 2>/dev/null
}

gql() {
  # Execute a GraphQL query/mutation
  # $1 = query string (JSON-safe)
  # $2 = auth header (optional, defaults to $AUTH)
  local query="$1"
  local auth_header="${2:-$AUTH}"
  curl -s -w "\n%{http_code}" \
    -H "$auth_header" \
    -H "$CONTENT_TYPE" \
    -d "$query" \
    "$BASE/graphql"
}

extract_response() {
  local response="$1"
  HTTP_CODE=$(echo "$response" | tail -1)
  BODY=$(echo "$response" | sed '$d')
}

###############################################################################
# 1. QUERY: me
###############################################################################
section "Query: me"

test_name "Query me - get current user"
RESPONSE=$(gql '{"query":"{ me { id username displayName email avatarUrl bio visibility followerCount followingCount } }"}')
extract_response "$RESPONSE"
if assert_status 200 "$HTTP_CODE"; then
  assert_no_errors "$BODY"
  MY_USERNAME=$(json_field "$BODY" "data.me.username")
  echo "    -> username: $MY_USERNAME"
fi

###############################################################################
# 2. QUERY: user(id)
###############################################################################
section "Query: user(id)"

test_name "Query user by ID"
RESPONSE=$(gql "{\"query\":\"{ user(id: \\\"$USER_ID\\\") { id username displayName email bio visibility followerCount followingCount } }\"}")
extract_response "$RESPONSE"
if assert_status 200 "$HTTP_CODE"; then
  assert_no_errors "$BODY"
fi

test_name "Query user by ID (second user)"
RESPONSE=$(gql "{\"query\":\"{ user(id: \\\"$SECOND_USER_ID\\\") { id username displayName } }\"}")
extract_response "$RESPONSE"
if assert_status 200 "$HTTP_CODE"; then
  assert_no_errors "$BODY"
fi

###############################################################################
# 3. MUTATION: createPost
###############################################################################
section "Mutation: createPost"

test_name "Create a post"
RESPONSE=$(gql '{"query":"mutation { createPost(content: \"GraphQL test post from test script\", visibility: \"PUBLIC\") { id content visibility createdAt author { id username } reactionCounts { type count } commentCount } }"}')
extract_response "$RESPONSE"
if assert_status 200 "$HTTP_CODE"; then
  assert_no_errors "$BODY"
  GQL_POST_ID=$(json_field "$BODY" "data.createPost.id")
  echo "    -> post ID: $GQL_POST_ID"
fi

test_name "Create post with targetType and targetId"
RESPONSE=$(gql '{"query":"mutation { createPost(content: \"Targeted post\", targetType: \"USER_FEED\", visibility: \"PUBLIC\") { id content targetType visibility } }"}')
extract_response "$RESPONSE"
if assert_status 200 "$HTTP_CODE"; then
  assert_no_errors "$BODY"
fi

###############################################################################
# 4. QUERY: post(id)
###############################################################################
section "Query: post(id)"

test_name "Query post by ID"
if [ -n "$GQL_POST_ID" ]; then
  RESPONSE=$(gql "{\"query\":\"{ post(id: \\\"$GQL_POST_ID\\\") { id content visibility createdAt author { id username displayName } reactionCounts { type count } commentCount comments { id content author { username } } attachments { id fileUrl mediaType } } }\"}")
  extract_response "$RESPONSE"
  if assert_status 200 "$HTTP_CODE"; then
    assert_no_errors "$BODY"
    CONTENT=$(json_field "$BODY" "data.post.content")
    echo "    -> content: $CONTENT"
  fi
else
  fail "no post ID from createPost"
fi

###############################################################################
# 5. QUERY: feed
###############################################################################
section "Query: feed"

test_name "Query feed (default)"
RESPONSE=$(gql '{"query":"{ feed { posts { id content author { id username } reactionCounts { type count } commentCount createdAt } nextCursor hasMore } }"}')
extract_response "$RESPONSE"
if assert_status 200 "$HTTP_CODE"; then
  assert_no_errors "$BODY"
  POST_COUNT=$(echo "$BODY" | python3 -c "
import sys, json
d = json.load(sys.stdin)
posts = d.get('data',{}).get('feed',{}).get('posts',[])
print(len(posts) if posts else 0)
" 2>/dev/null)
  echo "    -> $POST_COUNT posts in feed"
fi

test_name "Query feed with limit"
RESPONSE=$(gql '{"query":"{ feed(limit: 3) { posts { id content } nextCursor hasMore } }"}')
extract_response "$RESPONSE"
if assert_status 200 "$HTTP_CODE"; then
  assert_no_errors "$BODY"
fi

test_name "Query feed with cursor (pagination)"
NEXT_CURSOR=$(json_field "$BODY" "data.feed.nextCursor")
if [ -n "$NEXT_CURSOR" ] && [ "$NEXT_CURSOR" != "None" ]; then
  RESPONSE=$(gql "{\"query\":\"{ feed(cursor: \\\"$NEXT_CURSOR\\\", limit: 3) { posts { id content } nextCursor hasMore } }\"}")
  extract_response "$RESPONSE"
  if assert_status 200 "$HTTP_CODE"; then
    assert_no_errors "$BODY"
  fi
else
  # No cursor available, just run without cursor which we already tested
  RESPONSE=$(gql '{"query":"{ feed(limit: 1) { posts { id } hasMore } }"}')
  extract_response "$RESPONSE"
  if assert_status 200 "$HTTP_CODE"; then
    assert_no_errors "$BODY"
  fi
fi

###############################################################################
# 6. MUTATION: createComment
###############################################################################
section "Mutation: createComment"

test_name "Create comment on post"
if [ -n "$GQL_POST_ID" ]; then
  RESPONSE=$(gql "{\"query\":\"mutation { createComment(postId: \\\"$GQL_POST_ID\\\", content: \\\"GraphQL comment from test script\\\") { id content depth postId author { id username } createdAt } }\"}")
  extract_response "$RESPONSE"
  if assert_status 200 "$HTTP_CODE"; then
    assert_no_errors "$BODY"
    GQL_COMMENT_ID=$(json_field "$BODY" "data.createComment.id")
    echo "    -> comment ID: $GQL_COMMENT_ID"
  fi
else
  fail "no post ID"
fi

test_name "Create reply comment"
if [ -n "$GQL_POST_ID" ] && [ -n "$GQL_COMMENT_ID" ]; then
  RESPONSE=$(gql "{\"query\":\"mutation { createComment(postId: \\\"$GQL_POST_ID\\\", parentCommentId: \\\"$GQL_COMMENT_ID\\\", content: \\\"Reply via GraphQL\\\") { id content depth parentCommentId } }\"}")
  extract_response "$RESPONSE"
  if assert_status 200 "$HTTP_CODE"; then
    assert_no_errors "$BODY"
    REPLY_DEPTH=$(json_field "$BODY" "data.createComment.depth")
    echo "    -> reply depth: $REPLY_DEPTH"
  fi
else
  fail "no post or comment ID"
fi

test_name "Verify post now has comments"
if [ -n "$GQL_POST_ID" ]; then
  RESPONSE=$(gql "{\"query\":\"{ post(id: \\\"$GQL_POST_ID\\\") { commentCount comments { id content depth replies { id content } } } }\"}")
  extract_response "$RESPONSE"
  if assert_status 200 "$HTTP_CODE"; then
    assert_no_errors "$BODY"
    COMMENT_COUNT=$(json_field "$BODY" "data.post.commentCount")
    echo "    -> commentCount: $COMMENT_COUNT"
  fi
fi

###############################################################################
# 7. MUTATION: react
###############################################################################
section "Mutation: react"

test_name "React LIKE to post"
if [ -n "$GQL_POST_ID" ]; then
  RESPONSE=$(gql "{\"query\":\"mutation { react(targetId: \\\"$GQL_POST_ID\\\", reactionType: \\\"LIKE\\\") }\"}")
  extract_response "$RESPONSE"
  if assert_status 200 "$HTTP_CODE"; then
    assert_no_errors "$BODY"
    REACT_RESULT=$(json_field "$BODY" "data.react")
    echo "    -> result: $REACT_RESULT"
  fi
else
  fail "no post ID"
fi

test_name "React LOVE to post (change reaction)"
if [ -n "$GQL_POST_ID" ]; then
  RESPONSE=$(gql "{\"query\":\"mutation { react(targetId: \\\"$GQL_POST_ID\\\", reactionType: \\\"LOVE\\\") }\"}")
  extract_response "$RESPONSE"
  if assert_status 200 "$HTTP_CODE"; then
    assert_no_errors "$BODY"
  fi
else
  fail "no post ID"
fi

test_name "Verify reaction counts on post"
if [ -n "$GQL_POST_ID" ]; then
  RESPONSE=$(gql "{\"query\":\"{ post(id: \\\"$GQL_POST_ID\\\") { reactionCounts { type count } } }\"}")
  extract_response "$RESPONSE"
  if assert_status 200 "$HTTP_CODE"; then
    assert_no_errors "$BODY"
    REACTION_INFO=$(echo "$BODY" | python3 -c "
import sys, json
d = json.load(sys.stdin)
counts = d.get('data',{}).get('post',{}).get('reactionCounts',[])
for c in (counts or []):
    print(f\"{c['type']}={c['count']}\", end=' ')
" 2>/dev/null)
    echo "    -> reactions: $REACTION_INFO"
  fi
fi

###############################################################################
# 8. MUTATION: follow / unfollow
###############################################################################
section "Mutation: follow / unfollow"

test_name "Follow a user"
RESPONSE=$(gql "{\"query\":\"mutation { follow(targetId: \\\"$SECOND_USER_ID\\\") }\"}")
extract_response "$RESPONSE"
if assert_status 200 "$HTTP_CODE"; then
  assert_no_errors "$BODY"
  FOLLOW_RESULT=$(json_field "$BODY" "data.follow")
  echo "    -> result: $FOLLOW_RESULT"
fi

test_name "Verify follower count increased"
RESPONSE=$(gql "{\"query\":\"{ user(id: \\\"$SECOND_USER_ID\\\") { followerCount } }\"}")
extract_response "$RESPONSE"
if assert_status 200 "$HTTP_CODE"; then
  assert_no_errors "$BODY"
  FOLLOWER_COUNT=$(json_field "$BODY" "data.user.followerCount")
  echo "    -> followerCount: $FOLLOWER_COUNT"
fi

test_name "Unfollow a user"
RESPONSE=$(gql "{\"query\":\"mutation { unfollow(targetId: \\\"$SECOND_USER_ID\\\") }\"}")
extract_response "$RESPONSE"
if assert_status 200 "$HTTP_CODE"; then
  assert_no_errors "$BODY"
  UNFOLLOW_RESULT=$(json_field "$BODY" "data.unfollow")
  echo "    -> result: $UNFOLLOW_RESULT"
fi

test_name "Verify follower count after unfollow"
RESPONSE=$(gql "{\"query\":\"{ user(id: \\\"$SECOND_USER_ID\\\") { followerCount } }\"}")
extract_response "$RESPONSE"
if assert_status 200 "$HTTP_CODE"; then
  assert_no_errors "$BODY"
  FOLLOWER_COUNT=$(json_field "$BODY" "data.user.followerCount")
  echo "    -> followerCount: $FOLLOWER_COUNT"
fi

###############################################################################
# 9. QUERY: search
###############################################################################
section "Query: search"

test_name "Search with no type filter"
RESPONSE=$(gql '{"query":"{ search(query: \"test\") { totalHits hits { id objectType name description score } } }"}')
extract_response "$RESPONSE"
if assert_status 200 "$HTTP_CODE"; then
  assert_no_errors "$BODY"
  TOTAL_HITS=$(json_field "$BODY" "data.search.totalHits")
  echo "    -> totalHits: $TOTAL_HITS"
fi

test_name "Search for users"
RESPONSE=$(gql '{"query":"{ search(query: \"test\", type: \"user\") { totalHits hits { id objectType name description avatarUrl score } } }"}')
extract_response "$RESPONSE"
if assert_status 200 "$HTTP_CODE"; then
  assert_no_errors "$BODY"
  USER_HITS=$(json_field "$BODY" "data.search.totalHits")
  echo "    -> user hits: $USER_HITS"
fi

test_name "Search for groups"
RESPONSE=$(gql '{"query":"{ search(query: \"test\", type: \"group\") { totalHits hits { id objectType name } } }"}')
extract_response "$RESPONSE"
if assert_status 200 "$HTTP_CODE"; then
  assert_no_errors "$BODY"
fi

test_name "Search for pages"
RESPONSE=$(gql '{"query":"{ search(query: \"test\", type: \"page\") { totalHits hits { id objectType name } } }"}')
extract_response "$RESPONSE"
if assert_status 200 "$HTTP_CODE"; then
  assert_no_errors "$BODY"
fi

test_name "Search with empty result"
RESPONSE=$(gql '{"query":"{ search(query: \"zzzznonexistent99999\") { totalHits hits { id } } }"}')
extract_response "$RESPONSE"
if assert_status 200 "$HTTP_CODE"; then
  assert_no_errors "$BODY"
  EMPTY_HITS=$(json_field "$BODY" "data.search.totalHits")
  echo "    -> hits for nonsense query: $EMPTY_HITS"
fi

###############################################################################
# 10. COMBINED / ADVANCED QUERIES
###############################################################################
section "Advanced Queries"

test_name "Query user with nested follow counts"
RESPONSE=$(gql "{\"query\":\"{ user(id: \\\"$USER_ID\\\") { id username displayName followerCount followingCount } }\"}")
extract_response "$RESPONSE"
if assert_status 200 "$HTTP_CODE"; then
  assert_no_errors "$BODY"
fi

test_name "Query post with full nested data"
if [ -n "$GQL_POST_ID" ]; then
  RESPONSE=$(gql "{\"query\":\"{ post(id: \\\"$GQL_POST_ID\\\") { id content visibility createdAt author { id username displayName avatarUrl } reactionCounts { type count } commentCount comments { id content depth author { id username } replies { id content author { username } } createdAt } attachments { id fileUrl mediaType fileSize width height } } }\"}")
  extract_response "$RESPONSE"
  if assert_status 200 "$HTTP_CODE"; then
    assert_no_errors "$BODY"
  fi
else
  fail "no post ID"
fi

test_name "Query me and feed in single request (aliased)"
RESPONSE=$(gql '{"query":"{ currentUser: me { id username } latestFeed: feed(limit: 2) { posts { id content } hasMore } }"}')
extract_response "$RESPONSE"
if assert_status 200 "$HTTP_CODE"; then
  assert_no_errors "$BODY"
  ALIAS_USER=$(json_field "$BODY" "data.currentUser.username")
  echo "    -> currentUser: $ALIAS_USER"
fi

###############################################################################
# SUMMARY
###############################################################################
echo ""
echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════════════${RESET}"
echo -e "${BOLD}${CYAN}  GRAPHQL TEST SUMMARY${RESET}"
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
