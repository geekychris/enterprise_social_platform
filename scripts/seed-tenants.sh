#!/usr/bin/env bash
set -euo pipefail

###############################################################################
# Seed Tenants Script
#
# Creates test data for 3 tenants in the WorkSphere platform.
# Tenant 1 (Default) already exists; this script creates Tenants 2 and 3,
# registers users, creates groups, posts, and conversations for each.
#
# Usage: ./seed-tenants.sh [BASE_URL]
#   BASE_URL defaults to http://localhost:8080
###############################################################################

BASE="${1:-http://localhost:8080}"
CT="Content-Type: application/json"

# Super-admin credentials (tenant 1, admin user)
SUPER_ADMIN_HEADER="X-Debug-User-Id: 72057594037927937"
TENANT1_HEADER="X-Tenant-Id: 1"

# Colors
GREEN="\033[0;32m"
CYAN="\033[0;36m"
YELLOW="\033[0;33m"
RED="\033[0;31m"
BOLD="\033[1m"
RESET="\033[0m"

info()    { echo -e "${CYAN}[INFO]${RESET} $1"; }
success() { echo -e "${GREEN}[OK]${RESET}   $1"; }
warn()    { echo -e "${YELLOW}[WARN]${RESET} $1"; }
error()   { echo -e "${RED}[ERR]${RESET}  $1"; }
section() {
  echo ""
  echo -e "${BOLD}${CYAN}================================================================${RESET}"
  echo -e "${BOLD}${CYAN}  $1${RESET}"
  echo -e "${BOLD}${CYAN}================================================================${RESET}"
  echo ""
}

# Extract a field from JSON. On any error (missing python3, malformed JSON, bad path)
# log a clear diagnostic to stderr and return empty on stdout — so callers see WHY
# they got nothing instead of silently falling into the wrong fallback path.
json_field() {
  if ! command -v python3 >/dev/null 2>&1; then
    echo "[json_field ERROR] python3 not on PATH — required for JSON extraction" >&2
    return 0
  fi
  local out
  if out=$(echo "$1" | python3 -c "import sys,json; print(json.load(sys.stdin)$2)" 2>&1); then
    echo "$out"
  else
    echo "[json_field ERROR] extracting $2 — $out" >&2
  fi
}

# Make a POST request, return body
do_post() {
  local url="$1" data="$2"
  shift 2
  curl -s -H "$CT" "$@" -d "$data" "$url"
}

# Make a PUT request, return body
do_put() {
  local url="$1" data="$2"
  shift 2
  curl -s -X PUT -H "$CT" "$@" -d "$data" "$url"
}

# Track all created IDs
declare -A USER_IDS
declare -A TENANT_IDS
declare -A GROUP_IDS

TENANT_IDS[default]=1

###############################################################################
section "Tenant 1: Default (verify existing)"
###############################################################################

info "Checking that default tenant exists..."
EXISTING=$(curl -s -H "$SUPER_ADMIN_HEADER" -H "$TENANT1_HEADER" "$BASE/api/super-admin/tenants/1")
EXISTING_NAME=$(json_field "$EXISTING" "['name']")
if [ -n "$EXISTING_NAME" ] && [ "$EXISTING_NAME" != "" ]; then
  success "Default tenant exists: $EXISTING_NAME"
else
  warn "Could not verify default tenant (may need to start the app first)"
fi

# Set branding for Default tenant
info "Setting branding for Default tenant..."
BRAND_RESULT=$(do_put "$BASE/api/super-admin/tenants/1" \
  '{"settings":"{\"branding\":{\"companyName\":\"WorkSphere\",\"primaryColor\":\"#3B82F6\",\"logoUrl\":null}}"}' \
  -H "$SUPER_ADMIN_HEADER" -H "$TENANT1_HEADER")
success "Default tenant branding set"

###############################################################################
section "Tenant 2: Nexus Technologies"
###############################################################################

info "Creating tenant: Nexus Technologies..."
NEXUS_RESULT=$(do_post "$BASE/api/super-admin/tenants" \
  '{
    "name": "Nexus Technologies",
    "slug": "nexus",
    "plan": "enterprise",
    "maxUsers": 500,
    "adminUsername": "sarah.chen",
    "adminPassword": "nexus123",
    "adminEmail": "sarah.chen@nexus.tech",
    "adminDisplayName": "Sarah Chen"
  }' \
  -H "$SUPER_ADMIN_HEADER" -H "$TENANT1_HEADER")

NEXUS_TENANT_ID=$(json_field "$NEXUS_RESULT" "['tenant']['id']")
NEXUS_ADMIN_ID=$(json_field "$NEXUS_RESULT" "['admin']['id']")

if [ -z "$NEXUS_TENANT_ID" ] || [ "$NEXUS_TENANT_ID" = "" ]; then
  warn "Tenant may already exist, trying to fetch by listing..."
  ALL_TENANTS=$(curl -s -H "$SUPER_ADMIN_HEADER" -H "$TENANT1_HEADER" "$BASE/api/super-admin/tenants")
  NEXUS_TENANT_ID=$(echo "$ALL_TENANTS" | python3 -c "
import sys,json
tenants = json.load(sys.stdin)
for t in tenants:
    if t.get('slug') == 'nexus':
        print(t['id'])
        break
" 2>/dev/null || echo "")
  if [ -n "$NEXUS_TENANT_ID" ]; then
    success "Found existing Nexus tenant: ID=$NEXUS_TENANT_ID"
  else
    error "Failed to create or find Nexus tenant"
    echo "$NEXUS_RESULT"
    exit 1
  fi
else
  success "Created Nexus Technologies: tenant_id=$NEXUS_TENANT_ID, admin_id=$NEXUS_ADMIN_ID"
fi

TENANT_IDS[nexus]="$NEXUS_TENANT_ID"
USER_IDS[sarah.chen]="${NEXUS_ADMIN_ID:-unknown}"
NEXUS_HEADER="X-Tenant-Id: $NEXUS_TENANT_ID"

# Set branding for Nexus
info "Setting branding for Nexus Technologies..."
do_put "$BASE/api/super-admin/tenants/$NEXUS_TENANT_ID" \
  '{"settings":"{\"branding\":{\"companyName\":\"Nexus Technologies\",\"primaryColor\":\"#8B5CF6\",\"logoUrl\":null}}"}' \
  -H "$SUPER_ADMIN_HEADER" -H "$TENANT1_HEADER" > /dev/null
success "Nexus branding set"

# Register 5 additional users for Nexus
info "Registering Nexus users..."

NEXUS_USERS=(
  'marcus.rivera|nexus123|Marcus Rivera|marcus@nexus.tech'
  'priya.sharma|nexus123|Priya Sharma|priya@nexus.tech'
  'james.wilson|nexus123|James Wilson|james@nexus.tech'
  'aisha.johnson|nexus123|Aisha Johnson|aisha@nexus.tech'
  'kai.tanaka|nexus123|Kai Tanaka|kai@nexus.tech'
)

for entry in "${NEXUS_USERS[@]}"; do
  IFS='|' read -r username password displayName email <<< "$entry"
  RESULT=$(do_post "$BASE/api/auth/register" \
    "{\"username\":\"$username\",\"password\":\"$password\",\"displayName\":\"$displayName\",\"email\":\"$email\",\"bio\":\"$displayName at Nexus Technologies\"}" \
    -H "$NEXUS_HEADER")
  USER_ID=$(json_field "$RESULT" "['id']")
  if [ -n "$USER_ID" ] && [ "$USER_ID" != "" ]; then
    USER_IDS[$username]="$USER_ID"
    success "  Registered $username (id=$USER_ID)"
  else
    warn "  $username may already exist or registration failed"
    # Try to find user by searching
    SEARCH=$(curl -s -H "X-Debug-User-Id: ${NEXUS_ADMIN_ID:-1}" -H "$NEXUS_HEADER" "$BASE/api/users/search?q=$username")
    FOUND_ID=$(echo "$SEARCH" | python3 -c "
import sys,json
users = json.load(sys.stdin)
for u in users:
    if u.get('username') == '$username':
        print(u['id'])
        break
" 2>/dev/null || echo "")
    if [ -n "$FOUND_ID" ]; then
      USER_IDS[$username]="$FOUND_ID"
      success "  Found existing $username (id=$FOUND_ID)"
    fi
  fi
done

# Create groups for Nexus
info "Creating Nexus groups..."

# Use Sarah Chen (admin) as the debug user for group operations
NEXUS_AUTH="X-Debug-User-Id: ${USER_IDS[sarah.chen]:-1}"

NEXUS_GROUP1_RESULT=$(do_post "$BASE/api/groups" \
  '{"name":"Engineering Hub","description":"Technical discussions and code reviews for the Nexus engineering team","visibility":"PUBLIC"}' \
  -H "$NEXUS_AUTH" -H "$NEXUS_HEADER")
NEXUS_GROUP1_ID=$(json_field "$NEXUS_GROUP1_RESULT" "['id']")
if [ -n "$NEXUS_GROUP1_ID" ] && [ "$NEXUS_GROUP1_ID" != "" ]; then
  GROUP_IDS[nexus_engineering]="$NEXUS_GROUP1_ID"
  success "Created group: Engineering Hub (id=$NEXUS_GROUP1_ID)"
else
  warn "Engineering Hub group may already exist"
fi

NEXUS_GROUP2_RESULT=$(do_post "$BASE/api/groups" \
  '{"name":"Product Design","description":"Product design discussions, mockups, and UX research","visibility":"PUBLIC"}' \
  -H "$NEXUS_AUTH" -H "$NEXUS_HEADER")
NEXUS_GROUP2_ID=$(json_field "$NEXUS_GROUP2_RESULT" "['id']")
if [ -n "$NEXUS_GROUP2_ID" ] && [ "$NEXUS_GROUP2_ID" != "" ]; then
  GROUP_IDS[nexus_design]="$NEXUS_GROUP2_ID"
  success "Created group: Product Design (id=$NEXUS_GROUP2_ID)"
else
  warn "Product Design group may already exist"
fi

# Join users to groups
info "Adding users to Nexus groups..."
for username in marcus.rivera priya.sharma james.wilson; do
  uid="${USER_IDS[$username]:-}"
  if [ -n "$uid" ] && [ -n "${NEXUS_GROUP1_ID:-}" ]; then
    do_post "$BASE/api/groups/$NEXUS_GROUP1_ID/join" '{}' \
      -H "X-Debug-User-Id: $uid" -H "$NEXUS_HEADER" > /dev/null 2>&1
    success "  $username joined Engineering Hub"
  fi
done

for username in aisha.johnson kai.tanaka priya.sharma; do
  uid="${USER_IDS[$username]:-}"
  if [ -n "$uid" ] && [ -n "${NEXUS_GROUP2_ID:-}" ]; then
    do_post "$BASE/api/groups/$NEXUS_GROUP2_ID/join" '{}' \
      -H "X-Debug-User-Id: $uid" -H "$NEXUS_HEADER" > /dev/null 2>&1
    success "  $username joined Product Design"
  fi
done

# Create posts in Nexus groups
info "Creating posts in Nexus groups..."

if [ -n "${NEXUS_GROUP1_ID:-}" ]; then
  SARAH_AUTH="X-Debug-User-Id: ${USER_IDS[sarah.chen]:-1}"
  do_post "$BASE/api/posts" \
    "{\"content\":\"Welcome to Engineering Hub! Let's use this space to share technical updates, discuss architecture decisions, and coordinate code reviews. Please keep discussions constructive.\",\"targetType\":\"GROUP_FEED\",\"targetId\":\"$NEXUS_GROUP1_ID\"}" \
    -H "$SARAH_AUTH" -H "$NEXUS_HEADER" > /dev/null 2>&1
  success "  Sarah posted welcome in Engineering Hub"

  MARCUS_AUTH="X-Debug-User-Id: ${USER_IDS[marcus.rivera]:-1}"
  do_post "$BASE/api/posts" \
    "{\"content\":\"Just finished the API gateway migration to gRPC. Performance benchmarks show 3x improvement in p99 latency. Full writeup coming tomorrow.\",\"targetType\":\"GROUP_FEED\",\"targetId\":\"$NEXUS_GROUP1_ID\"}" \
    -H "$MARCUS_AUTH" -H "$NEXUS_HEADER" > /dev/null 2>&1
  success "  Marcus posted in Engineering Hub"

  PRIYA_AUTH="X-Debug-User-Id: ${USER_IDS[priya.sharma]:-1}"
  do_post "$BASE/api/posts" \
    "{\"content\":\"Reminder: Architecture review meeting tomorrow at 2pm. We'll be discussing the new microservices decomposition proposal. Please review the RFC doc beforehand.\",\"targetType\":\"GROUP_FEED\",\"targetId\":\"$NEXUS_GROUP1_ID\"}" \
    -H "$PRIYA_AUTH" -H "$NEXUS_HEADER" > /dev/null 2>&1
  success "  Priya posted in Engineering Hub"
fi

if [ -n "${NEXUS_GROUP2_ID:-}" ]; then
  AISHA_AUTH="X-Debug-User-Id: ${USER_IDS[aisha.johnson]:-1}"
  do_post "$BASE/api/posts" \
    "{\"content\":\"Sharing the updated design system components for Q2. New button styles, form inputs, and the refreshed color palette are all in Figma. Link in thread.\",\"targetType\":\"GROUP_FEED\",\"targetId\":\"$NEXUS_GROUP2_ID\"}" \
    -H "$AISHA_AUTH" -H "$NEXUS_HEADER" > /dev/null 2>&1
  success "  Aisha posted in Product Design"

  KAI_AUTH="X-Debug-User-Id: ${USER_IDS[kai.tanaka]:-1}"
  do_post "$BASE/api/posts" \
    "{\"content\":\"User research findings from last week's sessions: 73% of users prefer the sidebar navigation over top-nav. Detailed report attached.\",\"targetType\":\"GROUP_FEED\",\"targetId\":\"$NEXUS_GROUP2_ID\"}" \
    -H "$KAI_AUTH" -H "$NEXUS_HEADER" > /dev/null 2>&1
  success "  Kai posted in Product Design"
fi

# Create conversations between Nexus users
info "Creating Nexus conversations..."

if [ -n "${USER_IDS[marcus.rivera]:-}" ] && [ -n "${USER_IDS[priya.sharma]:-}" ]; then
  do_post "$BASE/api/messages" \
    "{\"recipientId\":\"${USER_IDS[priya.sharma]}\",\"content\":\"Hey Priya, can you review my PR for the gateway migration? It's ready for review.\"}" \
    -H "X-Debug-User-Id: ${USER_IDS[marcus.rivera]}" -H "$NEXUS_HEADER" > /dev/null 2>&1
  do_post "$BASE/api/messages" \
    "{\"recipientId\":\"${USER_IDS[marcus.rivera]}\",\"content\":\"Sure Marcus, I'll take a look this afternoon. Is it on the feature/grpc-gateway branch?\"}" \
    -H "X-Debug-User-Id: ${USER_IDS[priya.sharma]}" -H "$NEXUS_HEADER" > /dev/null 2>&1
  success "  Marcus <-> Priya conversation created"
fi

if [ -n "${USER_IDS[sarah.chen]:-}" ] && [ -n "${USER_IDS[aisha.johnson]:-}" ]; then
  do_post "$BASE/api/messages" \
    "{\"recipientId\":\"${USER_IDS[aisha.johnson]}\",\"content\":\"Aisha, the new design system looks fantastic. Can we schedule a walkthrough with the engineering team?\"}" \
    -H "X-Debug-User-Id: ${USER_IDS[sarah.chen]}" -H "$NEXUS_HEADER" > /dev/null 2>&1
  do_post "$BASE/api/messages" \
    "{\"recipientId\":\"${USER_IDS[sarah.chen]}\",\"content\":\"Thanks Sarah! How about Thursday at 10am? I can do a live demo in Figma.\"}" \
    -H "X-Debug-User-Id: ${USER_IDS[aisha.johnson]}" -H "$NEXUS_HEADER" > /dev/null 2>&1
  success "  Sarah <-> Aisha conversation created"
fi

if [ -n "${USER_IDS[kai.tanaka]:-}" ] && [ -n "${USER_IDS[james.wilson]:-}" ]; then
  do_post "$BASE/api/messages" \
    "{\"recipientId\":\"${USER_IDS[james.wilson]}\",\"content\":\"James, I need your help setting up the A/B testing framework for the nav redesign.\"}" \
    -H "X-Debug-User-Id: ${USER_IDS[kai.tanaka]}" -H "$NEXUS_HEADER" > /dev/null 2>&1
  do_post "$BASE/api/messages" \
    "{\"recipientId\":\"${USER_IDS[kai.tanaka]}\",\"content\":\"No problem Kai. Let's pair on it tomorrow morning. I've got a good template we can start from.\"}" \
    -H "X-Debug-User-Id: ${USER_IDS[james.wilson]}" -H "$NEXUS_HEADER" > /dev/null 2>&1
  success "  Kai <-> James conversation created"
fi

###############################################################################
section "Tenant 3: Meridian Health"
###############################################################################

info "Creating tenant: Meridian Health..."
MERIDIAN_RESULT=$(do_post "$BASE/api/super-admin/tenants" \
  '{
    "name": "Meridian Health",
    "slug": "meridian",
    "plan": "pro",
    "maxUsers": 200,
    "adminUsername": "dr.emily.ross",
    "adminPassword": "meridian123",
    "adminEmail": "emily.ross@meridian.health",
    "adminDisplayName": "Dr. Emily Ross"
  }' \
  -H "$SUPER_ADMIN_HEADER" -H "$TENANT1_HEADER")

MERIDIAN_TENANT_ID=$(json_field "$MERIDIAN_RESULT" "['tenant']['id']")
MERIDIAN_ADMIN_ID=$(json_field "$MERIDIAN_RESULT" "['admin']['id']")

if [ -z "$MERIDIAN_TENANT_ID" ] || [ "$MERIDIAN_TENANT_ID" = "" ]; then
  warn "Tenant may already exist, trying to fetch by listing..."
  ALL_TENANTS=$(curl -s -H "$SUPER_ADMIN_HEADER" -H "$TENANT1_HEADER" "$BASE/api/super-admin/tenants")
  MERIDIAN_TENANT_ID=$(echo "$ALL_TENANTS" | python3 -c "
import sys,json
tenants = json.load(sys.stdin)
for t in tenants:
    if t.get('slug') == 'meridian':
        print(t['id'])
        break
" 2>/dev/null || echo "")
  if [ -n "$MERIDIAN_TENANT_ID" ]; then
    success "Found existing Meridian tenant: ID=$MERIDIAN_TENANT_ID"
  else
    error "Failed to create or find Meridian tenant"
    echo "$MERIDIAN_RESULT"
    exit 1
  fi
else
  success "Created Meridian Health: tenant_id=$MERIDIAN_TENANT_ID, admin_id=$MERIDIAN_ADMIN_ID"
fi

TENANT_IDS[meridian]="$MERIDIAN_TENANT_ID"
USER_IDS[dr.emily.ross]="${MERIDIAN_ADMIN_ID:-unknown}"
MERIDIAN_HEADER="X-Tenant-Id: $MERIDIAN_TENANT_ID"

# Set branding for Meridian
info "Setting branding for Meridian Health..."
do_put "$BASE/api/super-admin/tenants/$MERIDIAN_TENANT_ID" \
  '{"settings":"{\"branding\":{\"companyName\":\"Meridian Health\",\"primaryColor\":\"#10B981\",\"logoUrl\":null}}"}' \
  -H "$SUPER_ADMIN_HEADER" -H "$TENANT1_HEADER" > /dev/null
success "Meridian branding set"

# Register 4 additional users for Meridian
info "Registering Meridian users..."

MERIDIAN_USERS=(
  'nurse.alex|meridian123|Alex Thompson|alex@meridian.health'
  'dr.patel|meridian123|Dr. Raj Patel|raj@meridian.health'
  'admin.torres|meridian123|Maria Torres|maria@meridian.health'
  'tech.kim|meridian123|David Kim|david@meridian.health'
)

for entry in "${MERIDIAN_USERS[@]}"; do
  IFS='|' read -r username password displayName email <<< "$entry"
  RESULT=$(do_post "$BASE/api/auth/register" \
    "{\"username\":\"$username\",\"password\":\"$password\",\"displayName\":\"$displayName\",\"email\":\"$email\",\"bio\":\"$displayName at Meridian Health\"}" \
    -H "$MERIDIAN_HEADER")
  USER_ID=$(json_field "$RESULT" "['id']")
  if [ -n "$USER_ID" ] && [ "$USER_ID" != "" ]; then
    USER_IDS[$username]="$USER_ID"
    success "  Registered $username (id=$USER_ID)"
  else
    warn "  $username may already exist or registration failed"
    SEARCH=$(curl -s -H "X-Debug-User-Id: ${MERIDIAN_ADMIN_ID:-1}" -H "$MERIDIAN_HEADER" "$BASE/api/users/search?q=$username")
    FOUND_ID=$(echo "$SEARCH" | python3 -c "
import sys,json
users = json.load(sys.stdin)
for u in users:
    if u.get('username') == '$username':
        print(u['id'])
        break
" 2>/dev/null || echo "")
    if [ -n "$FOUND_ID" ]; then
      USER_IDS[$username]="$FOUND_ID"
      success "  Found existing $username (id=$FOUND_ID)"
    fi
  fi
done

# Create groups for Meridian
info "Creating Meridian groups..."

EMILY_AUTH="X-Debug-User-Id: ${USER_IDS[dr.emily.ross]:-1}"

MERIDIAN_GROUP1_RESULT=$(do_post "$BASE/api/groups" \
  '{"name":"Clinical Updates","description":"Share clinical guidelines, protocol updates, and case discussions","visibility":"PUBLIC"}' \
  -H "$EMILY_AUTH" -H "$MERIDIAN_HEADER")
MERIDIAN_GROUP1_ID=$(json_field "$MERIDIAN_GROUP1_RESULT" "['id']")
if [ -n "$MERIDIAN_GROUP1_ID" ] && [ "$MERIDIAN_GROUP1_ID" != "" ]; then
  GROUP_IDS[meridian_clinical]="$MERIDIAN_GROUP1_ID"
  success "Created group: Clinical Updates (id=$MERIDIAN_GROUP1_ID)"
else
  warn "Clinical Updates group may already exist"
fi

MERIDIAN_GROUP2_RESULT=$(do_post "$BASE/api/groups" \
  '{"name":"Staff Lounge","description":"Casual conversations, team building, and social events","visibility":"PUBLIC"}' \
  -H "$EMILY_AUTH" -H "$MERIDIAN_HEADER")
MERIDIAN_GROUP2_ID=$(json_field "$MERIDIAN_GROUP2_RESULT" "['id']")
if [ -n "$MERIDIAN_GROUP2_ID" ] && [ "$MERIDIAN_GROUP2_ID" != "" ]; then
  GROUP_IDS[meridian_lounge]="$MERIDIAN_GROUP2_ID"
  success "Created group: Staff Lounge (id=$MERIDIAN_GROUP2_ID)"
else
  warn "Staff Lounge group may already exist"
fi

# Join users to groups
info "Adding users to Meridian groups..."
for username in nurse.alex dr.patel admin.torres tech.kim; do
  uid="${USER_IDS[$username]:-}"
  if [ -n "$uid" ]; then
    if [ -n "${MERIDIAN_GROUP1_ID:-}" ]; then
      do_post "$BASE/api/groups/$MERIDIAN_GROUP1_ID/join" '{}' \
        -H "X-Debug-User-Id: $uid" -H "$MERIDIAN_HEADER" > /dev/null 2>&1
    fi
    if [ -n "${MERIDIAN_GROUP2_ID:-}" ]; then
      do_post "$BASE/api/groups/$MERIDIAN_GROUP2_ID/join" '{}' \
        -H "X-Debug-User-Id: $uid" -H "$MERIDIAN_HEADER" > /dev/null 2>&1
    fi
    success "  $username joined both groups"
  fi
done

# Create posts in Meridian groups
info "Creating posts in Meridian groups..."

if [ -n "${MERIDIAN_GROUP1_ID:-}" ]; then
  do_post "$BASE/api/posts" \
    "{\"content\":\"Updated COVID-19 booster guidelines released today. All staff should review the new protocol before next Monday. Summary: boosters now recommended for all healthcare workers regardless of prior infection status.\",\"targetType\":\"GROUP_FEED\",\"targetId\":\"$MERIDIAN_GROUP1_ID\"}" \
    -H "$EMILY_AUTH" -H "$MERIDIAN_HEADER" > /dev/null 2>&1
  success "  Dr. Ross posted in Clinical Updates"

  PATEL_AUTH="X-Debug-User-Id: ${USER_IDS[dr.patel]:-1}"
  do_post "$BASE/api/posts" \
    "{\"content\":\"Case discussion: 45yo patient presenting with atypical chest pain and normal troponins. Echo shows mild LV dysfunction. Would appreciate input on workup approach. Details in thread.\",\"targetType\":\"GROUP_FEED\",\"targetId\":\"$MERIDIAN_GROUP1_ID\"}" \
    -H "$PATEL_AUTH" -H "$MERIDIAN_HEADER" > /dev/null 2>&1
  success "  Dr. Patel posted in Clinical Updates"

  ALEX_AUTH="X-Debug-User-Id: ${USER_IDS[nurse.alex]:-1}"
  do_post "$BASE/api/posts" \
    "{\"content\":\"Nursing staff reminder: New medication reconciliation checklist is now mandatory for all admissions. Templates are in the shared drive. Let me know if you have questions.\",\"targetType\":\"GROUP_FEED\",\"targetId\":\"$MERIDIAN_GROUP1_ID\"}" \
    -H "$ALEX_AUTH" -H "$MERIDIAN_HEADER" > /dev/null 2>&1
  success "  Alex posted in Clinical Updates"
fi

if [ -n "${MERIDIAN_GROUP2_ID:-}" ]; then
  TORRES_AUTH="X-Debug-User-Id: ${USER_IDS[admin.torres]:-1}"
  do_post "$BASE/api/posts" \
    "{\"content\":\"Team lunch this Friday at noon in the cafeteria! We're celebrating Dr. Patel's 5-year anniversary with Meridian. Please RSVP so we can order enough food.\",\"targetType\":\"GROUP_FEED\",\"targetId\":\"$MERIDIAN_GROUP2_ID\"}" \
    -H "$TORRES_AUTH" -H "$MERIDIAN_HEADER" > /dev/null 2>&1
  success "  Maria posted in Staff Lounge"

  KIM_AUTH="X-Debug-User-Id: ${USER_IDS[tech.kim]:-1}"
  do_post "$BASE/api/posts" \
    "{\"content\":\"IT notice: The EHR system will have scheduled maintenance this Saturday from 2-4am. Downtime expected to be minimal. Please save any open records before midnight Friday.\",\"targetType\":\"GROUP_FEED\",\"targetId\":\"$MERIDIAN_GROUP2_ID\"}" \
    -H "$KIM_AUTH" -H "$MERIDIAN_HEADER" > /dev/null 2>&1
  success "  David posted in Staff Lounge"
fi

# Create conversations for Meridian
info "Creating Meridian conversations..."

if [ -n "${USER_IDS[dr.emily.ross]:-}" ] && [ -n "${USER_IDS[dr.patel]:-}" ]; then
  do_post "$BASE/api/messages" \
    "{\"recipientId\":\"${USER_IDS[dr.patel]}\",\"content\":\"Raj, can we discuss the cardiac case you posted? I have some thoughts on the workup.\"}" \
    -H "X-Debug-User-Id: ${USER_IDS[dr.emily.ross]}" -H "$MERIDIAN_HEADER" > /dev/null 2>&1
  do_post "$BASE/api/messages" \
    "{\"recipientId\":\"${USER_IDS[dr.emily.ross]}\",\"content\":\"Of course Emily. I'm leaning toward a stress echo but wanted a second opinion first.\"}" \
    -H "X-Debug-User-Id: ${USER_IDS[dr.patel]}" -H "$MERIDIAN_HEADER" > /dev/null 2>&1
  success "  Dr. Ross <-> Dr. Patel conversation created"
fi

if [ -n "${USER_IDS[nurse.alex]:-}" ] && [ -n "${USER_IDS[admin.torres]:-}" ]; then
  do_post "$BASE/api/messages" \
    "{\"recipientId\":\"${USER_IDS[admin.torres]}\",\"content\":\"Maria, I need to order more PPE supplies for the ICU. Can you process a rush order?\"}" \
    -H "X-Debug-User-Id: ${USER_IDS[nurse.alex]}" -H "$MERIDIAN_HEADER" > /dev/null 2>&1
  do_post "$BASE/api/messages" \
    "{\"recipientId\":\"${USER_IDS[nurse.alex]}\",\"content\":\"I'll get that submitted today Alex. What specifically do you need? Gowns, gloves, masks?\"}" \
    -H "X-Debug-User-Id: ${USER_IDS[admin.torres]}" -H "$MERIDIAN_HEADER" > /dev/null 2>&1
  success "  Alex <-> Maria conversation created"
fi

if [ -n "${USER_IDS[tech.kim]:-}" ] && [ -n "${USER_IDS[dr.emily.ross]:-}" ]; then
  do_post "$BASE/api/messages" \
    "{\"recipientId\":\"${USER_IDS[dr.emily.ross]}\",\"content\":\"Dr. Ross, the new telehealth module is ready for testing. Would you like to be one of the pilot users?\"}" \
    -H "X-Debug-User-Id: ${USER_IDS[tech.kim]}" -H "$MERIDIAN_HEADER" > /dev/null 2>&1
  do_post "$BASE/api/messages" \
    "{\"recipientId\":\"${USER_IDS[tech.kim]}\",\"content\":\"Absolutely David, I'd love to try it out. Can you set me up with access?\"}" \
    -H "X-Debug-User-Id: ${USER_IDS[dr.emily.ross]}" -H "$MERIDIAN_HEADER" > /dev/null 2>&1
  success "  David <-> Dr. Ross conversation created"
fi

###############################################################################
section "Summary"
###############################################################################

echo -e "${BOLD}Tenants:${RESET}"
echo "  1. Default (WorkSphere)    - ID: ${TENANT_IDS[default]}"
echo "  2. Nexus Technologies      - ID: ${TENANT_IDS[nexus]}"
echo "  3. Meridian Health          - ID: ${TENANT_IDS[meridian]}"
echo ""

echo -e "${BOLD}User IDs:${RESET}"
for username in "${!USER_IDS[@]}"; do
  echo "  $username = ${USER_IDS[$username]}"
done | sort
echo ""

echo -e "${BOLD}Group IDs:${RESET}"
for group in "${!GROUP_IDS[@]}"; do
  echo "  $group = ${GROUP_IDS[$group]}"
done | sort
echo ""

# Save summary to file
SUMMARY_FILE="/Users/chris/code/claude_world/social_enterprise/scripts/seed-tenants-output.json"
python3 -c "
import json, sys
data = {
    'tenants': {
        'default': {'id': ${TENANT_IDS[default]}, 'name': 'WorkSphere', 'slug': 'default', 'plan': 'free'},
        'nexus': {'id': ${TENANT_IDS[nexus]}, 'name': 'Nexus Technologies', 'slug': 'nexus', 'plan': 'enterprise'},
        'meridian': {'id': ${TENANT_IDS[meridian]}, 'name': 'Meridian Health', 'slug': 'meridian', 'plan': 'pro'}
    },
    'users': $(python3 -c "
import json
ids = {}
$(for u in "${!USER_IDS[@]}"; do echo "ids['$u'] = '${USER_IDS[$u]}'"; done)
print(json.dumps(ids))
"),
    'groups': $(python3 -c "
import json
ids = {}
$(for g in "${!GROUP_IDS[@]}"; do echo "ids['$g'] = '${GROUP_IDS[$g]}'"; done)
print(json.dumps(ids))
")
}
with open('$SUMMARY_FILE', 'w') as f:
    json.dump(data, f, indent=2)
print('Summary saved to: $SUMMARY_FILE')
"

echo ""
success "Seed data creation complete!"
echo ""
echo -e "${YELLOW}Tip: Use these headers to access each tenant:${RESET}"
echo "  Default:  -H 'X-Tenant-Id: ${TENANT_IDS[default]}'"
echo "  Nexus:    -H 'X-Tenant-Id: ${TENANT_IDS[nexus]}'"
echo "  Meridian: -H 'X-Tenant-Id: ${TENANT_IDS[meridian]}'"
