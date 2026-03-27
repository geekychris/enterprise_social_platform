# Roid - AI Assistant Bot

Roid is an AI-powered assistant built into WorkSphere that you can chat with like any other user. It can answer questions about your groups, pages, feed, profiles, and conversations.

## Getting Started

### Direct Chat
Click **"Chat with Roid"** in the left sidebar (purple sparkle icon). This opens a 1:1 conversation where you can ask anything.

### Mentioning in Group Conversations
In any conversation with other people, type `@roid` to bring the bot into the thread. Roid will join the conversation as a participant and respond to your message. It stays in the conversation for future mentions.

**Mention format:** Just type `@roid` naturally in your message — no special syntax needed.

---

## What Roid Can Do

### 1. Group Activity
Ask about any group you're a member of. Roid fetches the actual posts and provides a thematic summary.

| Example | What Happens |
|---------|-------------|
| "What's going on in the agnus group?" | Fetches posts from the agnus group, summarizes themes |
| "Summarize the remote workers group" | Provides high-level summary of recent activity |
| "Any important announcements in the engineering team?" | Looks for announcements/decisions in the group |
| "What has been discussed in the design group this week?" | Summarizes recent discussions |

### 2. Page Content
Ask about pages on the platform.

| Example | What Happens |
|---------|-------------|
| "What's new on the HR page?" | Fetches recent posts from the HR page |
| "Summarize the engineering blog page" | Summarizes recent page content |

### 3. Your Feed
Get caught up on what you've missed across all your followed content.

| Example | What Happens |
|---------|-------------|
| "What's new?" | Summarizes your recent feed |
| "Catch me up" | High-level overview of recent activity |
| "What's happening today?" | Highlights recent/important posts |
| "What did I miss?" | Summary of recent feed content |

### 4. User Profiles
Ask about people in the organization.

| Example | What Happens |
|---------|-------------|
| "Who is @[Marcellus Beatty](72057594037927940)?" | Shows their profile, role, skills, bio |
| "Tell me about Pedro" | Looks up Pedro's profile by name |
| "Who is the lead on the design team?" | Searches for the person by context |

### 5. Search
Find content across the platform.

| Example | What Happens |
|---------|-------------|
| "Search for cloud migration" | Finds posts mentioning cloud migration |
| "Find posts about performance" | Searches for performance-related content |
| "Look for announcements about the office" | Searches for matching posts |

### 6. Conversation Summaries
When Roid is in a conversation (either DM or group chat), it can analyze the thread.

| Example | What Happens |
|---------|-------------|
| "Summarize this conversation" | Summarizes recent messages in the current thread |
| "What were the key decisions?" | Extracts decisions from the conversation |
| "What action items came up?" | Identifies action items from the discussion |

---

## Using Roid in Group Conversations

This is where Roid becomes especially powerful — you can bring it into any conversation with colleagues.

### Bringing Roid In
Just mention `@roid` in any message. The bot automatically joins the conversation and responds.

```
You: Hey team, @roid can you summarize what's been going on in the remote workers group?

Roid: The remote workers group has been focused on 3 main themes...
```

### Asking About Other Participants
Roid can see the conversation history and answer questions about what people said.

```
Alice: I think we should migrate to Kubernetes
Bob: No, I prefer staying on bare metal for now
Carol: What about a hybrid approach?

You: @roid summarize what everyone's position is

Roid: There are three positions in the discussion:
- **Alice** advocates for full Kubernetes migration
- **Bob** prefers staying on bare metal
- **Carol** suggests a hybrid approach...
```

### Using Conversation Context
Roid reads recent messages in the thread, so you can reference the discussion naturally.

```
You: @roid based on this discussion, what are the main risks?
You: @roid can you draft a summary of this meeting for the team?
You: @roid what questions haven't been addressed yet?
```

### Combining Tools
Roid can pull in external data while in a conversation.

```
You: @roid what did the engineering group post about this topic?
You: @roid can you look up Maria's profile? I want to loop her in
You: @roid search for any prior discussions about database migration
```

---

## Actions — Roid Does Things For You

Roid can take actions on your behalf, not just answer questions.

### Creating Posts
```
You: Post in the engineering group: Reminder — standup moves to 10am next week
Roid: Done! I've posted your reminder to the Engineering group.
```

### Creating Polls
```
You: Create a poll in remote workers: What day works for team lunch? Options: Monday, Wednesday, Friday
Roid: Poll created! "What day works for team lunch?" with 3 options has been posted to Remote Workers.
```
Polls show up as interactive cards in the group feed. Members can vote directly on the post.

### Sending Messages
```
You: Send a message to Alice: I'll be 10 minutes late to the meeting
Roid: Message sent to Alice.
```

### Joining Groups
```
You: Join the design group
Roid: You've been added to the Design group.
```

---

## Memory — Roid Remembers

Roid has cross-conversation memory. Things you tell it to remember persist and are available in future conversations.

### Saving Memories
```
You: Remember that my favorite programming language is Rust
Roid: Got it! I'll remember that your favorite language is Rust.

// ... later, in a different conversation ...
You: What's my favorite language?
Roid: Your favorite programming language is Rust.
```

### How It Works
- Memories are stored per-user in a database key-value store
- They persist across all conversations with Roid
- Roid automatically includes your memories in its context
- Say "forget X" to remove a memory

---

## Daily Digest

Roid sends you a morning summary of overnight activity (8:00 AM by default).

**What's included:**
- Unread message count
- New posts in your groups since last digest
- Pending friend requests

The digest arrives as a regular DM from Roid. You can disable it in your profile settings.

---

## One-Click Summarize

Two "Summarize" buttons are available throughout the app:

### Conversation Summary
In any message thread, click the **Summarize** button (sparkle icon) above the messages. Roid streams a structured summary with key topics, decisions, and action items.

### Comment Thread Summary
On any post with comments, click **Summarize** to get an AI-generated overview of the discussion, identifying points of agreement, disagreement, and conclusions.

---

## How It Works (Technical)

### Trigger Conditions
1. **DM with Roid** — Every message you send gets a response
2. **@roid mention** — In any conversation, Roid joins and responds to the specific message

### Context Gathering
When you send a message, Roid automatically:
1. Reads recent messages in the current conversation (user messages only, to avoid echo)
2. Detects keywords to determine which tools to call:
   - Group/team names → fetches posts from that group
   - Page references → fetches page posts
   - Feed keywords → loads your feed
   - Search/find keywords → searches platform content
   - Profile/who-is keywords → looks up user profiles
   - @mentions of users → loads their profiles
3. Assembles all gathered context (truncated to fit the model's context window)
4. Sends everything to the LLM with a system prompt instructing it to answer helpfully

### Visibility & Security
- Roid can only access content **you** have permission to see
- Group posts are only available if you're an **approved member** of that group
- Conversations are only accessible if you're a **participant**
- The bot respects all platform visibility rules (public, restricted, private)

### Configuration
Configurable in `application.yml`:

| Setting | Default | Description |
|---------|---------|-------------|
| `social.bot.name` | `roid` | Bot username and mention trigger |
| `social.bot.user-id` | `72057594037999999` | Bot's user ID in the database |
| `social.bot.max-history-messages` | `30` | Max conversation messages for "summarize" |
| `social.bot.max-context-tokens` | `3000` | Max context size sent to model |
| `social.ai.model` | `llama3.2:latest` | Ollama model to use |
| `social.ai.ollama-url` | `http://localhost:11434` | Ollama server URL |
| `social.bot.digest.enabled` | `true` | Enable/disable daily digest |
| `social.bot.digest.cron` | `0 0 8 * * *` | Cron schedule for digest (default 8 AM) |
| `social.bot.digest.lookback-hours` | `12` | Hours to look back for activity |

### Response Behavior
- Roid responds **after** your message is saved (not while typing)
- Responses typically take 5-15 seconds depending on the model and context size
- Bot responses appear as regular messages from "Roid" — they persist like any other message
- In polling-based clients (web, iOS), the response appears on the next refresh cycle (~3 seconds)

---

## Architecture: Cross-Conversation Memory

### Design
The memory system enables Roid to remember information across separate conversations with the same user.

```
┌──────────────────────────────────────────┐
│              User sends message           │
│  "Remember my favorite language is Rust"  │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│           BotService.gatherContext()      │
│  1. Load existing memories from DB       │
│  2. Include in LLM context               │
│  3. Add conversation history + tools     │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│           Ollama LLM processes           │
│  Sees: memories + context + user message │
│  Outputs: response + [REMEMBER|...] tags │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│     BotService.parseAndSaveMemories()    │
│  1. Extract [REMEMBER|key=...|value=...] │
│  2. Upsert to bot_memory table           │
│  3. Strip tags from response             │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│         Response saved as message         │
│    "Got it! I'll remember that..."        │
└──────────────────────────────────────────┘
```

### Database Schema
```sql
CREATE TABLE bot_memory (
    id           BIGINT PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id),
    memory_key   VARCHAR(128) NOT NULL,    -- e.g., "favorite_language"
    memory_value TEXT NOT NULL,             -- e.g., "Rust"
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, memory_key)           -- one value per key per user
);
```

### Key Properties
- **Per-user isolation** — each user's memories are private and separate
- **Upsert semantics** — saving the same key updates the value rather than duplicating
- **Automatic inclusion** — all memories are loaded into context on every bot interaction
- **Context budget** — memories count against the context window limit; oldest are trimmed if needed
- **No cross-user leakage** — memory queries are always scoped to the authenticated user's ID

### Action Execution Architecture
```
User message → BotService.handleMessage()
                    │
                    ├── gatherContext() — reads memories, conversation, groups, pages, etc.
                    │
                    ├── ollamaService.chatBlocking() — LLM generates response with [ACTION:...] tags
                    │
                    ├── parseAndExecuteActions() — finds tags, calls BotActionService methods:
                    │       ├── createPost()     → PostRepository.save()
                    │       ├── createPollPost() → PostRepository.save() + PollService.createPoll()
                    │       ├── sendMessage()    → ConversationService + MessageService
                    │       └── joinGroup()      → GroupService.join()
                    │
                    ├── parseAndSaveMemories() — finds [REMEMBER|...] tags, calls BotMemoryService
                    │
                    └── messageService.send() — saves cleaned response as bot message
```
