# Tenant Cheatsheet

Quick reference for all tenants, users, and access patterns in the WorkSphere multi-tenant platform.

## Tenants Overview

| ID | Name | Slug | Plan | Max Users | Primary Color |
|----|------|------|------|-----------|---------------|
| `1` | WorkSphere (Default) | `default` | free | 1000 | `#3B82F6` (blue) |
| `1774990020324`* | Nexus Technologies | `nexus` | enterprise | 500 | `#8B5CF6` (purple) |
| `1774990021731`* | Meridian Health | `meridian` | pro | 200 | `#10B981` (green) |

> *Tenant IDs are generated at creation time via `System.currentTimeMillis()`. The IDs above are from the initial seed run. If you re-seed on a fresh database, IDs will differ. Check `scripts/seed-tenants-output.json` after running `seed-tenants.sh` for the actual values.

## Login Credentials

All passwords follow the pattern `{tenant}123`.

### Tenant 1: WorkSphere (Default)

Uses `X-Tenant-Id: 1`.

| Username | Password | Display Name | Role | Notes |
|----------|----------|--------------|------|-------|
| `lamar.lehner` | `password` | Lamar Lehner | **Admin** | Super-admin (can manage tenants) |
| `joshua.padberg` | `password` | Joshua Padberg | **Admin** | |
| `cecilia.watsica` | `password` | Cecilia Watsica | **Admin** | |
| `marcellus.beatty` | `password` | Marcellus Beatty | User | Most active user |
| `steve.kovacek` | `password` | Steve Kovacek | User | |
| `lila.parisian` | `password` | Lila Parisian | User | |

The default tenant's primary admin debug user ID is `72057594037927937`.

### Tenant 2: Nexus Technologies

Uses `X-Tenant-Id: <NEXUS_ID>`.

| Username | Password | Display Name | Email | Role |
|----------|----------|--------------|-------|------|
| `sarah.chen` | `nexus123` | Sarah Chen | sarah.chen@nexus.tech | **Admin** |
| `marcus.rivera` | `nexus123` | Marcus Rivera | marcus@nexus.tech | User |
| `priya.sharma` | `nexus123` | Priya Sharma | priya@nexus.tech | User |
| `james.wilson` | `nexus123` | James Wilson | james@nexus.tech | User |
| `aisha.johnson` | `nexus123` | Aisha Johnson | aisha@nexus.tech | User |
| `kai.tanaka` | `nexus123` | Kai Tanaka | kai@nexus.tech | User |

**Groups:**
- **Engineering Hub** -- Members: sarah.chen (creator), marcus.rivera, priya.sharma, james.wilson
- **Product Design** -- Members: sarah.chen (creator), aisha.johnson, kai.tanaka, priya.sharma

**Seeded Content:**
- Engineering Hub: architecture updates, API migration results, meeting reminders
- Product Design: design system components, user research findings
- Conversations: Marcus/Priya (code review), Sarah/Aisha (design walkthrough), Kai/James (A/B testing)

### Tenant 3: Meridian Health

Uses `X-Tenant-Id: <MERIDIAN_ID>`.

| Username | Password | Display Name | Email | Role |
|----------|----------|--------------|-------|------|
| `dr.emily.ross` | `meridian123` | Dr. Emily Ross | emily.ross@meridian.health | **Admin** |
| `nurse.alex` | `meridian123` | Alex Thompson | alex@meridian.health | User |
| `dr.patel` | `meridian123` | Dr. Raj Patel | raj@meridian.health | User |
| `admin.torres` | `meridian123` | Maria Torres | maria@meridian.health | User |
| `tech.kim` | `meridian123` | David Kim | david@meridian.health | User |

**Groups:**
- **Clinical Updates** -- All users are members
- **Staff Lounge** -- All users are members

**Seeded Content:**
- Clinical Updates: COVID guidelines, cardiac case discussion, nursing checklists
- Staff Lounge: team lunch announcement, IT maintenance notice
- Conversations: Dr. Ross/Dr. Patel (case discussion), Alex/Maria (supply order), David/Dr. Ross (telehealth pilot)

## How to Switch Tenants

### In the Browser (React UI)

The frontend reads the tenant from the `X-Tenant-Id` header. For local development:

1. Open browser DevTools > Console
2. Set localStorage to trigger the tenant switch:
```javascript
// Switch to Nexus Technologies
localStorage.setItem('tenantId', '1774990020324');
location.reload();

// Switch to Meridian Health
localStorage.setItem('tenantId', '1774990021731');
location.reload();

// Switch back to Default
localStorage.removeItem('tenantId');
location.reload();
```

The `TenantFilter` on the backend reads `X-Tenant-Id` from the request header. The frontend Axios interceptor attaches this automatically from localStorage.

Alternatively, toggle **Debug Mode** on the login page and enter a user ID directly.

### Via API (curl)

Add the `X-Tenant-Id` header to every request:

```bash
# Default tenant
curl -H "X-Debug-User-Id: 72057594037927937" \
     -H "X-Tenant-Id: 1" \
     http://localhost:8080/api/feed

# Nexus Technologies
curl -H "X-Debug-User-Id: <SARAH_USER_ID>" \
     -H "X-Tenant-Id: <NEXUS_TENANT_ID>" \
     http://localhost:8080/api/feed

# Meridian Health
curl -H "X-Debug-User-Id: <EMILY_USER_ID>" \
     -H "X-Tenant-Id: <MERIDIAN_TENANT_ID>" \
     http://localhost:8080/api/feed
```

### Via Login Flow

```bash
# Login returns a session scoped to the tenant
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: <TENANT_ID>" \
  -d '{"username":"sarah.chen","password":"nexus123"}'
```

## Debug Mode Instructions

Debug mode bypasses authentication by setting the `X-Debug-User-Id` header to a user's numeric ID.

### Per-Tenant Debug Access

| Tenant | Debug User ID | Who | Headers Needed |
|--------|--------------|-----|----------------|
| Default | `72057594037927937` | Lamar Lehner (admin) | `X-Debug-User-Id: 72057594037927937` + `X-Tenant-Id: 1` |
| Nexus | *(from seed output)* | Sarah Chen (admin) | `X-Debug-User-Id: <ID>` + `X-Tenant-Id: <NEXUS_ID>` |
| Meridian | *(from seed output)* | Dr. Emily Ross (admin) | `X-Debug-User-Id: <ID>` + `X-Tenant-Id: <MERIDIAN_ID>` |

Both headers are required. `X-Tenant-Id` sets the tenant context and `X-Debug-User-Id` authenticates as a specific user within that tenant.

### In the UI

1. Go to the login page
2. Toggle the **Debug Mode** switch
3. Enter a user ID (numeric)
4. Make sure the correct tenant ID is set (via localStorage or URL)

### Super-Admin Access

Super-admin endpoints (`/api/super-admin/tenants/*`) require admin credentials on tenant 1:

```bash
# List all tenants
curl -H "X-Debug-User-Id: 72057594037927937" \
     -H "X-Tenant-Id: 1" \
     http://localhost:8080/api/super-admin/tenants

# Get specific tenant details
curl -H "X-Debug-User-Id: 72057594037927937" \
     -H "X-Tenant-Id: 1" \
     http://localhost:8080/api/super-admin/tenants/<TENANT_ID>

# Update tenant branding
curl -X PUT "http://localhost:8080/api/super-admin/tenants/<TENANT_ID>" \
     -H "X-Debug-User-Id: 72057594037927937" \
     -H "X-Tenant-Id: 1" \
     -H "Content-Type: application/json" \
     -d '{"settings":"{\"branding\":{\"companyName\":\"New Name\",\"primaryColor\":\"#FF6600\",\"logoUrl\":null}}"}'
```

## Branding

Each tenant has custom branding stored in the `settings` JSONB column:

| Tenant | Company Name | Primary Color | Hex |
|--------|-------------|---------------|-----|
| Default | WorkSphere | Blue | `#3B82F6` |
| Nexus | Nexus Technologies | Purple | `#8B5CF6` |
| Meridian | Meridian Health | Green | `#10B981` |

Branding is fetched from `GET /api/branding` (public, no auth) with the `X-Tenant-Id` header. The React frontend applies it on load, changing the page title, header, and generating a color palette from the primary color.

## Quick Test Scenarios

### 1. Verify Tenant Isolation

Confirm users in one tenant cannot see data from another:

```bash
# Get Nexus user's feed - should show only Nexus content
curl -H "X-Debug-User-Id: <MARCUS_ID>" \
     -H "X-Tenant-Id: <NEXUS_ID>" \
     http://localhost:8080/api/feed

# Search for "emily" in Nexus - should find 0 results (Emily is in Meridian)
curl -H "X-Debug-User-Id: <SARAH_ID>" \
     -H "X-Tenant-Id: <NEXUS_ID>" \
     "http://localhost:8080/api/users/search?q=emily"

# Get Meridian user's feed - should show only Meridian content
curl -H "X-Debug-User-Id: <ALEX_ID>" \
     -H "X-Tenant-Id: <MERIDIAN_ID>" \
     http://localhost:8080/api/feed
```

### 2. Verify Tenant-Scoped Groups

```bash
# Search for groups in Nexus - should find "Engineering Hub"
curl -H "X-Debug-User-Id: <SARAH_ID>" \
     -H "X-Tenant-Id: <NEXUS_ID>" \
     "http://localhost:8080/api/groups/search?q=Engineering"

# Same search in Meridian - should NOT find "Engineering Hub"
curl -H "X-Debug-User-Id: <EMILY_ID>" \
     -H "X-Tenant-Id: <MERIDIAN_ID>" \
     "http://localhost:8080/api/groups/search?q=Engineering"
```

### 3. Verify Tenant-Scoped Messaging

```bash
# Marcus's conversations in Nexus
curl -H "X-Debug-User-Id: <MARCUS_ID>" \
     -H "X-Tenant-Id: <NEXUS_ID>" \
     http://localhost:8080/api/messages/conversations

# Alex's conversations in Meridian
curl -H "X-Debug-User-Id: <ALEX_ID>" \
     -H "X-Tenant-Id: <MERIDIAN_ID>" \
     http://localhost:8080/api/messages/conversations
```

### 4. Verify Branding

```bash
# Check each tenant's branding settings
curl -H "X-Debug-User-Id: 72057594037927937" \
     -H "X-Tenant-Id: 1" \
     http://localhost:8080/api/super-admin/tenants/<NEXUS_ID>
# -> settings should contain primaryColor: #8B5CF6
```

### 5. Cross-Tenant Admin View

```bash
# As super-admin, list all tenants
curl -H "X-Debug-User-Id: 72057594037927937" \
     -H "X-Tenant-Id: 1" \
     http://localhost:8080/api/super-admin/tenants
```

### 6. Register New User in a Tenant

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: <NEXUS_ID>" \
  -d '{
    "username": "new.hire",
    "password": "nexus123",
    "displayName": "New Hire",
    "email": "new.hire@nexus.tech",
    "bio": "Just joined Nexus"
  }'
```

## Data Isolation Summary

Each tenant's data is completely isolated:
- Users in one tenant cannot see other tenants' posts, groups, or pages
- Search results are scoped to the current tenant
- Conversations cannot cross tenant boundaries
- Group membership and content are per-tenant
- Admin dashboards show only current-tenant metrics

## Regenerating Test Data

```bash
./scripts/seed-tenants.sh [BASE_URL]
# Defaults to http://localhost:8080
```

The script is idempotent for tenant creation (checks slug uniqueness) but will create additional users/posts if run multiple times. The JSON summary is saved to `scripts/seed-tenants-output.json`.
