# Support Bot — WorkSphere App Example

A webhook-based support bot that demonstrates the WorkSphere App Platform.

## What It Does

1. Listens for new posts on installed support pages
2. Searches a FAQ knowledge base for matching answers
3. If found: replies with a rich card containing the answer
4. If not found: creates a support case with a tracking number
5. Support agents can view and resolve cases in the Case Management UI

## Quick Start

### 1. Register the app in WorkSphere

```bash
curl -X POST http://localhost:8080/api/app-registry/apps \
  -H "X-Debug-User-Id: 72057594037927937" \
  -H "X-Tenant-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Support Bot",
    "slug": "support-bot",
    "description": "Automated FAQ answers and case management",
    "webhookUrl": "http://localhost:5050/webhook",
    "appType": "PAGE",
    "permissions": ["READ_POSTS", "WRITE_COMMENTS", "WRITE_POSTS"]
  }'
# Save the appId and apiKey from the response
```

### 2. Install on a support page

```bash
curl -X POST http://localhost:8080/api/app-registry/install \
  -H "X-Debug-User-Id: 72057594037927937" \
  -H "X-Tenant-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{"appId": APP_ID, "installType": "PAGE", "targetId": PAGE_ID}'
```

### 3. Start the bot

```bash
cd apps/support-bot
pip install -r requirements.txt
APP_ID=xxx API_KEY=app_xxx python app.py
```

### 4. Test

Post a question on the support page:
- "How do I reset my password?" → Bot replies with FAQ answer
- "My screen is flickering when I open reports" → Bot creates case #1001

## Configuration

| Env Var | Default | Purpose |
|---|---|---|
| `WORKSPHERE_URL` | `http://localhost:8080` | WorkSphere API URL |
| `WEBHOOK_PORT` | `5050` | Port for webhook receiver |
| `APP_ID` | — | App ID from registration |
| `API_KEY` | — | API key from registration |
| `OLLAMA_URL` | `http://localhost:11434` | Ollama for semantic search (future) |

## FAQ Data

Edit `faq_data.json` to customize the knowledge base. Each entry has:
- `question`: The question text (used for matching)
- `answer`: The answer to display
- `tags`: Keywords for improved matching

The bot also learns from resolved support cases, adding their Q&A pairs to the knowledge base at runtime.
