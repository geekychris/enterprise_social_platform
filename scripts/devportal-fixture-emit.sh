#!/usr/bin/env bash
# devportal-fixture-emit.sh — translates seed-tenants-output.json into the
# DEVPORTAL_FIXTURE line that the dev_portal Fixtures tab parses, AND verifies
# the credentials actually work against a live social-app (so users don't get
# stale credentials when the DB hasn't been populated by the datagen Job).
#
# Usage: ./scripts/devportal-fixture-emit.sh [UI_BASE_URL] [API_BASE_URL]
# UI_BASE_URL  — for landing links (default http://localhost:30080)
# API_BASE_URL — for the live login probe (default http://localhost:30002)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTFILE="${ROOT}/scripts/seed-tenants-output.json"
UI_BASE="${1:-${WORKSPHERE_UI_URL:-http://localhost:30080}}"
API_BASE="${2:-${WORKSPHERE_API_URL:-http://localhost:30002}}"

if [ ! -f "$OUTFILE" ]; then
  echo "ERROR: seed-tenants-output.json not found at $OUTFILE" >&2
  echo "Run scripts/seed-tenants.sh against a running social-app first." >&2
  exit 1
fi

# Probe the live API: try logging in as the documented first admin. Determines whether the
# datagen Job has actually populated the DB. The fixture surfaces this as a status field so
# the UI can warn the user if accounts don't exist yet.
LOGIN_PROBE_USER="lamar.lehner"
LOGIN_PROBE_STATUS="not-checked"
if curl -s --max-time 3 -o /dev/null -w "%{http_code}" "$API_BASE/actuator/health" 2>/dev/null \
  | grep -q "^200$"; then
  PROBE_HTTP=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -X POST "$API_BASE/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$LOGIN_PROBE_USER\",\"password\":\"password\"}" 2>/dev/null || echo "000")
  if [ "$PROBE_HTTP" = "200" ]; then
    LOGIN_PROBE_STATUS="ok"
  elif [ "$PROBE_HTTP" = "401" ] || [ "$PROBE_HTTP" = "404" ]; then
    LOGIN_PROBE_STATUS="db-empty"
  else
    LOGIN_PROBE_STATUS="api-error-http-$PROBE_HTTP"
  fi
else
  LOGIN_PROBE_STATUS="api-unreachable"
fi
export LOGIN_PROBE_STATUS LOGIN_PROBE_USER

# Build the structured credentials payload. Admin password is "password" (per TEST_ACCOUNTS.md);
# tenant 2/3 admins have their own creds known to seed-tenants.sh — surface both.
python3 - "$OUTFILE" "$UI_BASE" <<'PY'
import json, os, sys
out_file, base = sys.argv[1], sys.argv[2]
probe_status = os.environ.get('LOGIN_PROBE_STATUS', 'not-checked')
probe_user = os.environ.get('LOGIN_PROBE_USER', '?')
data = json.load(open(out_file))
creds = []
# Tenant 1 (default) — first 3 users are admins, all share password "password".
admins = [
    ("Lamar Lehner",   "lamar.lehner",   "password", "Tenant 1 (default) Admin"),
    ("Joshua Padberg", "joshua.padberg", "password", "Tenant 1 (default) Admin"),
    ("Cecilia Watsica","cecilia.watsica","password", "Tenant 1 (default) Admin"),
]
for label, user, pw, role in admins:
    creds.append({
        "label": label, "username": user, "password": pw, "role": role,
        "url": base + "/login"
    })
# Tenant 2/3 admins — sarah.chen / nexus123 etc. (from seed-tenants.sh source).
tenant2_admins = [
    ("sarah.chen",   "nexus123",      "Nexus Technologies (Tenant 2) Admin"),
]
tenant3_admins = [
    ("dr.emily.ross","meridian123",   "Meridian Health (Tenant 3) Admin"),
]
for user, pw, role in tenant2_admins + tenant3_admins:
    creds.append({
        "label": role, "username": user, "password": pw, "role": role,
        "url": base + "/login"
    })

links = [
    {"label": "WorkSphere UI",            "url": base + "/"},
    {"label": "Login page",               "url": base + "/login"},
    {"label": "Admin dashboard (admins)", "url": base + "/admin"},
]
tenants = data.get("tenants", {})
total_users = len(data.get("users", {}))
total_groups = len(data.get("groups", {}))

# Live-probe annotation: if the social-app login failed, prepend a clear warning so users
# don't waste time trying credentials that don't exist in the running DB.
if probe_status == "ok":
    warning = "Verified: lamar.lehner login works against the live API."
elif probe_status == "db-empty":
    warning = (
        "WARNING: live login probe FAILED — the social-app DB has no users yet. "
        "Run the datagen Job: kubectl create job --from=cronjob/datagen datagen-rerun -n worksphere "
        "or `kubectl apply -f k8s/datagen-job.yaml` after deleting the old Job. "
        "The credentials below ARE the documented test accounts but they need to be created first."
    )
elif probe_status == "api-unreachable":
    warning = (
        "WARNING: social-app API unreachable (no /actuator/health). Bring it up first; the "
        "credentials below are still the canonical test accounts."
    )
else:
    warning = f"WARNING: login probe inconclusive ({probe_status}). Credentials may not be loaded."

summary = (
    f"{warning}\n"
    f"Seed dataset has {len(tenants)} tenants ({', '.join(t.get('name','?') for t in tenants.values())}), "
    f"{total_users} users, {total_groups} groups. All tenant-1 users share password 'password'."
)
print("DEVPORTAL_FIXTURE: " + json.dumps({
    "summary": summary,
    "credentials": creds,
    "links": links,
}))
PY
