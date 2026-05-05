#!/usr/bin/env bash
# devportal-fixture-emit.sh — translates seed-tenants-output.json into the
# DEVPORTAL_FIXTURE line that the dev_portal Fixtures tab parses.
#
# Usage: ./scripts/devportal-fixture-emit.sh [BASE_URL]
# When BASE_URL is set, this is taken as the URL to use for landing links
# (e.g. http://localhost:30080 for the React UI exposed via NodePort).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTFILE="${ROOT}/scripts/seed-tenants-output.json"
BASE_URL="${1:-${WORKSPHERE_BASE_URL:-http://localhost:30080}}"

if [ ! -f "$OUTFILE" ]; then
  echo "ERROR: seed-tenants-output.json not found at $OUTFILE" >&2
  echo "Run scripts/seed-tenants.sh against a running social-app first." >&2
  exit 1
fi

# Build the structured credentials payload. Admin password is "password" (per TEST_ACCOUNTS.md);
# tenant 2/3 admins have their own creds known to seed-tenants.sh — surface both.
python3 - "$OUTFILE" "$BASE_URL" <<'PY'
import json, sys
out_file, base = sys.argv[1], sys.argv[2]
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
summary = (f"Seed dataset has {len(tenants)} tenants ({', '.join(t.get('name','?') for t in tenants.values())}), "
           f"{total_users} users, {total_groups} groups. All tenant-1 users share password 'password'.")
print("DEVPORTAL_FIXTURE: " + json.dumps({
    "summary": summary,
    "credentials": creds,
    "links": links,
}))
PY
