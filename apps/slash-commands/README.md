# Slash Commands App

A WorkSphere app that provides two slash commands:

- **/joke <topic>** -- Generates a knock-knock joke about the given topic. Recognizes special topics like `coffee`, `code`, `work`, and `meeting` for themed punchlines.
- **/stock <TICKER>** -- Looks up a stock ticker and responds with a formatted card showing price, change, volume, P/E ratio, and market cap.

The app listens for `POST_CREATED` webhook events and checks whether the post content starts with `/joke` or `/stock`. When it matches, it posts a comment on the original post with the result.

## Available Stock Tickers

AAPL, AMZN, GOOGL, JPM, META, MSFT, NFLX, NVDA, TSLA, V

Stock data is simulated for demo purposes.

## Setup

### 1. Register the app

```bash
curl -s -X POST http://localhost:8080/api/app-registry/apps \
  -H "X-Debug-User-Id: 72057594037927937" \
  -H "X-Tenant-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Slash Commands",
    "slug": "slash-commands",
    "description": "/joke and /stock commands for all users",
    "webhookUrl": "http://localhost:5051/webhook",
    "appType": "USER",
    "permissions": ["READ_POSTS", "WRITE_COMMENTS", "SLASH_COMMANDS"]
  }'
```

Save the `id` and `apiKey` from the response.

### 2. Install the app

**For a single user:**

```bash
curl -s -X POST http://localhost:8080/api/app-registry/install \
  -H "X-Debug-User-Id: 72057594037927937" \
  -H "X-Tenant-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{"appId": <APP_ID>, "installType": "USER"}'
```

**Org-wide (admin):**

```bash
curl -s -X POST http://localhost:8080/api/app-registry/install \
  -H "X-Debug-User-Id: 72057594037927937" \
  -H "X-Tenant-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{"appId": <APP_ID>, "installType": "ORG", "targetId": 1}'
```

### 3. Run the automated setup

Alternatively, use the setup script which registers and installs in one step:

```bash
./setup.sh http://localhost:8080
```

## Running

### Python (local)

```bash
APP_ID=<id> API_KEY=<key> python3 app.py
```

### Docker

```bash
docker build -t worksphere/slash-commands .
docker run -e APP_ID=<id> -e API_KEY=<key> -p 5051:5051 worksphere/slash-commands
```

### Docker Compose

```bash
SLASH_CMD_APP_ID=<id> SLASH_CMD_API_KEY=<key> \
  docker compose -f docker-compose.apps.yml up -d slash-commands
```

### Kubernetes

```bash
# Create the secret first
kubectl create secret generic slash-commands-secrets \
  --from-literal=app-id=YOUR_APP_ID \
  --from-literal=api-key=YOUR_API_KEY \
  -n worksphere

kubectl apply -k k8s/
```

## Example Interactions

**User posts:** `/joke coffee`

**App comments:**
> **Knock knock!**
>
> *Who's there?*
>
> **coffee.**
>
> *coffee who?*
>
> **coffee** -- better latte than never!

---

**User posts:** `/stock NVDA`

**App comments:**
> **NVIDIA Corp.** (NVDA)
>
> ### $875.30
> +12.40 (+1.44%)
>
> | Metric | Value |
> |---|---|
> | Day High | $880.00 |
> | Day Low | $860.00 |
> | Volume | 42.8M |
> | P/E Ratio | 65.4 |
> | Market Cap | $2.16T |

---

**User posts:** `/stock FAKE`

**App comments:**
> **Stock not found: FAKE**
>
> Available tickers: AAPL, AMZN, GOOGL, JPM, META, MSFT, NFLX, NVDA, TSLA, V
