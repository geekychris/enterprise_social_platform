# WorkSphere System Design

## Overview

WorkSphere is an enterprise social platform comprising a Spring Boot backend, React web frontend, native iOS (SwiftUI) and Android (Jetpack Compose) apps, and an AI-powered bot assistant. The system supports real-time messaging, feed assembly with recommendations, organizational hierarchy, polls, and agent-based AI interactions.

## Architecture Diagram

```
                              ┌──────────────────────────────┐
                              │       Load Balancer /         │
                              │       Ingress Controller      │
                              └──────────┬───────────────────┘
                                         │
              ┌──────────────────────────┼──────────────────────────┐
              │                          │                          │
     ┌────────┴────────┐     ┌──────────┴──────────┐    ┌─────────┴─────────┐
     │   React SPA     │     │   Social App API     │    │   WebSocket       │
     │   (Nginx)       │     │   (Spring Boot)      │    │   Gateway         │
     │   Port 80       │     │   Port 8080           │    │   /ws (STOMP)    │
     └─────────────────┘     │                       │    │   (same process) │
                              │   REST API            │    └───────────────────┘
     ┌─────────────────┐     │   GraphQL              │
     │   iOS App        │────│   AI SSE streaming     │
     │   (SwiftUI)      │    │   Rate limiting        │
     └─────────────────┘     │                       │
                              └──────────┬───────────┘
     ┌─────────────────┐                │
     │   Android App    │               │
     │   (Compose)      │    ┌──────────┼──────────────────────────────┐
     └─────────────────┘     │          │          │         │         │
                         ┌───┴───┐  ┌───┴───┐  ┌──┴──┐  ┌──┴──┐  ┌──┴──────┐
                         │Postgre│  │ Redis │  │Kafka│  │Ollama│  │OpenSearch│
                         │  SQL  │  │       │  │     │  │ LLM  │  │         │
                         │Primary│  │Cache +│  │Event│  │      │  │Full-text│
                         │+ Read │  │Pub/Sub│  │Bus  │  │      │  │ Search  │
                         │Replica│  │+Feeds │  │     │  │      │  │         │
                         └───────┘  └───────┘  └─────┘  └──────┘  └─────────┘
                              │
                         ┌────┴────┐
                         │  AOEE   │
                         │  Graph  │
                         │  Engine │
                         └─────────┘
```

## Client Applications

### React Web (social-frontend)

Single-page application built with React 18, TypeScript, TanStack Query, and Tailwind CSS.

- **Feed**: Infinite scroll with cursor-based pagination, AI assistant
- **Messaging**: Conversation list with polling (10s), message thread with polling (3s), markdown rendering, file attachments, @mentions
- **Profiles**: Inline org tree, follow/friend system, edit with image upload
- **Groups/Pages**: Member avatars, polls, AI summarization, posts
- **Org**: Searchable hierarchy viewer, admin editor
- **Bot**: Chat with Roid via sidebar shortcut, @roid mentions in conversations
- **Admin**: Dashboard, user management, org editor, graph explorer

**API Communication**: All API calls go through Axios client with interceptor for JWT/debug auth headers. SSE used for AI streaming. Markdown rendered via `react-markdown` + `remark-gfm` + `@tailwindcss/typography`.

### iOS App (WorkSphere)

SwiftUI app targeting iOS 17+, universal iPhone/iPad.

- **Architecture**: `@Observable` services, `NavigationStack`/`NavigationSplitView`
- **Adaptive Layout**: iPhone uses `TabView` (5 tabs), iPad uses `NavigationSplitView` with sidebar
- **API Client**: Generic async/await methods, `FlexibleDecoder` handles string-encoded Int64 IDs
- **Special Features**: Camera/photo library for profile images, speech-to-text for message composition
- **Auth**: JWT token or X-Debug-User-Id stored in UserDefaults

### Android App (WorkSphere)

Kotlin + Jetpack Compose app targeting API 26+, phone and tablet.

- **Architecture**: Singleton `ApiClient` + `AuthService`, Compose navigation with `NavHost`
- **Adaptive Layout**: Phone uses `NavigationBar` (5 tabs), tablet uses `NavigationRail`
- **API Client**: OkHttp with auth interceptor, custom Gson `SafeLongAdapter` for string-encoded IDs
- **Data Layer**: SharedPreferences for auth persistence, Caffeine-style in-memory caching

### Client-Server Contract

All three clients communicate with the same REST API. No client-specific endpoints exist. The API returns JSON with string-encoded Int64 IDs (to avoid JavaScript precision loss). Each client handles this:

| Client | ID Handling | Auth Header |
|--------|-------------|-------------|
| React | `FlexibleDecoder` in iOS-style pre-processing isn't needed; IDs treated as numbers in TypeScript | `Authorization: Bearer {token}` or `X-Debug-User-Id` |
| iOS | `FlexibleDecoder` converts JSON string numbers to `Int64` before decoding | Same |
| Android | `SafeLongAdapter` Gson TypeAdapter handles both `123` and `"123"` | Same via OkHttp interceptor |

**WebSocket Opportunity**: The `/ws` STOMP endpoint is available for real-time messaging. Currently only the React app could use it (via SockJS). Adding WebSocket support to iOS/Android would replace the 3-second polling with instant delivery but is not required — polling still works.

## Backend Services

### Spring Boot Application (social-app)

The monolithic backend handles all API requests, WebSocket connections, event publishing, and scheduled tasks.

#### Request Flow

```
HTTP Request
    → RateLimitFilter (Caffeine-based, per-user/IP, 60-300 req/min)
    → DebugAuthFilter (X-Debug-User-Id bypass)
    → JwtAuthFilter (Bearer token validation)
    → Spring Security (endpoint authorization)
    → Controller (REST/GraphQL/WebSocket)
    → Service Layer (business logic)
    → Repository Layer (JPA/JDBC)
    → PostgreSQL
```

#### Service Layer

| Service | Purpose | Dependencies |
|---------|---------|-------------|
| `MessageService` | Send messages, convert DTOs | ConversationSummaryService, UnreadCountService, MessageBroadcastService, EventPublisher |
| `ConversationService` | Conversation CRUD, participant management | UnreadCountService, CacheService |
| `FeedService` | Feed assembly with organic + recommended posts | PostRepository, RecommendationService |
| `RecommendationService` | Trending, FOF, cross-team post recommendations | AOEE graph client, ReactionRepository |
| `BotService` | AI bot orchestration, action parsing, memory | OllamaService, BotToolService, BotActionService, BotMemoryService |
| `BotToolService` | Read-only tools (search, group posts, org queries) | PostRepository, UserRepository, OrgService |
| `BotActionService` | Write actions (create post, send message, create poll) | PostRepository, PollService, ConversationService |
| `OrgService` | Organizational hierarchy CRUD | OrgUnitRepository, OrgAssignmentRepository |
| `PostService` | Post CRUD with poll attachment | PostRepository, PollRepository, PollService |
| `PollService` | Poll creation, voting, results | PollRepository, PollOptionRepository, PollVoteRepository |
| `DailyDigestService` | Scheduled morning digest DMs | @Scheduled cron, OllamaService |

### Scalability Infrastructure

#### Redis (Cache + Pub/Sub + Feed Storage)

```
Redis Roles:
  1. L2 Cache (CacheService)
     - User profiles: TTL 5min
     - Conversation lists: TTL 10s
     - Unread counts: TTL varies

  2. Pub/Sub (MessageBroadcastService)
     - Channel: conversation:{id}
     - Publishes message JSON on send
     - All app instances subscribe for WebSocket broadcast

  3. Feed Sorted Sets (FeedFanoutConsumer)
     - Key: feed:{userId}
     - Score: timestamp
     - Members: postId
     - Max 500 entries per user
```

#### Kafka (Event Streaming)

```
Topics:
  messages.sent    (4 partitions) → Consumed by: notification service (future)
  posts.created    (4 partitions) → Consumed by: FeedFanoutConsumer (fan-out to followers)
  reactions.added  (4 partitions) → Consumed by: analytics (future)

Published by EventPublisher after:
  - MessageService.send()
  - PostController.createPost()
  - ReactionController.addReaction()
```

#### WebSocket (Real-Time Messaging)

```
STOMP over SockJS at /ws

Destinations:
  /user/{userId}/queue/messages     → New message delivery
  /topic/conversation.{id}.typing   → Typing indicators

Message Flow:
  1. Client connects to /ws with JWT token
  2. WebSocketAuthInterceptor validates token, sets userId in session
  3. Client subscribes to /user/{self}/queue/messages
  4. On message send: MessageBroadcastService pushes to all participants
  5. Redis Pub/Sub ensures delivery across multiple app instances
```

#### Rate Limiting

```
RateLimitFilter (Caffeine-backed, per-minute windows):
  /api/conversations, /api/messages  → 300 req/min
  /api/feed                          → 120 req/min
  /api/search                        → 60 req/min
  /api/ai/*                          → 60 req/min
  Default                            → 200 req/min

Response headers:
  X-RateLimit-Limit: 200
  X-RateLimit-Remaining: 198

  HTTP 429 when exceeded with retryAfterSeconds
```

#### Caching (Two-Layer)

```
CacheService:
  L1: Caffeine (in-process, 10K entries, 5min TTL)
  L2: Redis (shared, configurable TTL)

  Read: L1 → L2 → Database → Write back to L1+L2
  Write: Update L1 + L2
  Evict: Invalidate L1 + L2
```

#### Materialized Views

```
conversation_summaries:
  - Last message preview per conversation
  - Updated by ConversationSummaryService on every message send
  - Eliminates N+1 query for conversation list

unread_counts:
  - Per-user per-conversation unread count
  - Incremented on message send (for all participants except sender)
  - Reset to 0 on mark-read
  - Total = SUM(unread_count) WHERE user_id = ?
  - O(conversations) not O(messages)
```

### AI Integration (Roid Bot)

```
User Message → BotService.handleMessage()
                    │
    Trigger Detection:
    ├── DM with bot user → always respond
    └── @roid mention in any conversation → join + respond
                    │
    Context Gathering (BotToolService):
    ├── User's group memberships
    ├── User's pages
    ├── Recent conversation history (6 messages)
    ├── User's saved memories
    ├── [keyword: group/team] → group posts
    ├── [keyword: page] → page posts
    ├── [keyword: feed/new] → user's feed
    ├── [keyword: search/find] → search results
    ├── [keyword: org/report/manage] → org data
    ├── [keyword: who/which/where] → broad search + user search
    └── [keyword: profile/summarize] → user profile
                    │
    LLM Call (OllamaService):
    ├── System prompt with action tag format + examples
    ├── Context + user message
    └── Response with potential [ACTION:...] tags
                    │
    Action Parsing:
    ├── [ACTION:CREATE_POST|...] → BotActionService.createPost()
    ├── [ACTION:CREATE_POLL|...] → BotActionService.createPollPost()
    ├── [ACTION:SEND_MESSAGE|...] → BotActionService.sendMessage()
    ├── [ACTION:JOIN_GROUP|...] → BotActionService.joinGroup()
    └── [REMEMBER|key=...|value=...] → BotMemoryService.remember()
                    │
    Target Resolution:
    ├── resolveTargetWithType() → tries group name, then page name
    ├── resolveUserId() → tries numeric, username, display name
    └── Membership verification before posting to groups
                    │
    Response saved as message from bot user
```

### Database Schema

```
Core:
  users                  → profiles, auth, org
  posts (partitioned)    → content, quarterly partitions
  comments (partitioned) → threaded, max depth 1
  reactions              → 6 types, unique per user per target
  attachments            → files, images

Messaging:
  conversations                → DIRECT or GROUP
  conversation_participants    → membership, last_read_at, visible_from
  messages                     → content, conversation_id, sender_id
  conversation_summaries       → denormalized last message (materialized)
  unread_counts                → per-user per-conversation count (materialized)

Social:
  follows                → user-to-user
  friend_requests        → send/accept/reject
  memberships            → group membership with roles (OWNER/ADMIN/MEMBER)
  page_memberships       → page follows

Organization:
  org_units              → hierarchical: COMPANY → DIVISION → DEPARTMENT → TEAM
  org_assignments        → user ↔ org unit with SOLID/DOTTED relationship

Features:
  polls / poll_options / poll_votes → embedded in posts
  bot_memory             → per-user key-value for AI memory
  bot_triggers           → workflow automation rules
  notifications          → in-app notifications
  feed_entries           → pre-computed feed cache

ID Generation:
  GlobalIdGenerator      → (ObjectType << 56) | sequence
  SnowflakeIdGenerator   → (timestamp << 22) | (nodeId << 12) | sequence
  16 object types: USER, POST, COMMENT, TEAM, GROUP, PAGE, PROJECT,
                   ATTACHMENT, REACTION, MESSAGE, INVITE_TOKEN, CONVERSATION,
                   POLL, POLL_OPTION, BOT_MEMORY, ORG_UNIT, ORG_ASSIGNMENT
```

### Kubernetes Deployment

```
Namespace: worksphere

Stateful:
  postgres (1 replica, PV)     → Primary database
  redis (1 replica)            → Cache + Pub/Sub
  kafka (1 replica, KRaft)     → Event streaming
  opensearch (1 replica)       → Full-text search
  ollama (1 replica)           → AI/LLM

Stateless:
  social-app (2 replicas)      → API + WebSocket
  frontend (2 replicas)        → Nginx serving React SPA
  aoee-server (1 replica)      → Graph engine
  aoee-proxy (1 replica)       → gRPC-to-HTTP proxy

Ingress:
  /api, /ws, /actuator, /graphql, /uploads → social-app:8080
  /                                         → frontend:80
  WebSocket: enabled with long timeouts
```

### Data Flow Examples

#### Sending a Message

```
1. Client POST /api/conversations/{id}/messages {content}
2. RateLimitFilter checks rate (300/min for messaging)
3. ConversationController.sendMessage() validates participant
4. MessageService.send():
   a. Generate ID (GlobalIdGenerator)
   b. Save to PostgreSQL (messages table)
   c. Link attachments (if any)
   d. ConversationSummaryService.updateSummary() → upsert conversation_summaries
   e. UnreadCountService.incrementUnread() → UPDATE unread_counts +1 for all participants
   f. MessageBroadcastService.broadcastMessage():
      - SimpMessagingTemplate → push to WebSocket subscribers
      - Redis PUBLISH conversation:{id} → other server instances
   g. EventPublisher.publishMessageSent() → Kafka topic messages.sent
5. BotService.handleMessage() → checks if bot should respond (async, virtual thread)
6. Response: MessageDto JSON
```

#### Loading the Feed

```
1. Client GET /api/feed?limit=20&cursor={lastPostId}
2. FeedService.assembleFeed():
   a. Get followed user IDs + own ID
   b. Get group/page membership IDs
   c. Query organic posts (author IN followers OR target IN groups)
   d. Apply visibility filter (PUBLIC, TEAM_VISIBLE, RESTRICTED)
   e. Apply cursor pagination
   f. Get recommendations (trending + FOF + cross-team)
   g. Score: engagement × recency_decay × affinity_boost
   h. Interleave: 1 recommended per 5 organic
3. Return FeedResponse {posts, nextCursor, hasMore}
```

#### Bot Creating a Poll

```
1. User sends: "create a poll in remote workers: lunch day? options: Mon, Wed, Fri"
2. BotService detects DM with bot → triggers response
3. Context gathered: user's groups (includes "remote workers"), conversation history
4. Ollama generates: "Done! [ACTION:CREATE_POLL|question=lunch day?|options=Mon,Wed,Fri|targetType=GROUP_FEED|targetId=remote workers]"
5. parseAndExecuteActions():
   a. Extracts CREATE_POLL action
   b. resolveTargetWithType("remote workers") → finds group ID
   c. BotActionService.createPollPost():
      - Verifies user is group member
      - Creates PostEntity with question as content
      - Creates PollEntity with 3 PollOptionEntities
6. Bot response saved as message (with action tag stripped)
```

### Mobile App Integration

Both iOS and Android apps use the **same REST API** as the React web app. No client-specific endpoints exist.

| Feature | API Used | Mobile Notes |
|---------|----------|-------------|
| Auth | POST /api/auth/login, /register | Token stored locally |
| Feed | GET /api/feed?limit=20&cursor= | Infinite scroll via pagination |
| Messages | GET/POST /api/conversations/* | Polling (3s thread, 10s list) |
| Bot chat | POST /api/conversations/direct/{botId}, POST .../messages | Same as human chat |
| Polls | POST /api/polls/{id}/vote | Vote returns updated PollDto |
| Org | GET /api/org/units, /assignments/* | Tree + chain + reports |
| Search | GET /api/search?q=&type= | Type filters |
| AI | POST /api/ai/ask (SSE) | Token-based streaming |
| Catch-up | POST /api/catchup | Batch sync for offline devices |

**Future mobile optimization**: Replace polling with WebSocket connection to `/ws` using STOMP. The backend already supports this — clients just need to add SockJS/STOMP client libraries and subscribe to `/user/{self}/queue/messages`.

### Testing

```
Test Suites:
  SnowflakeIdGeneratorTest (5 tests)
    - Uniqueness, ordering, multi-node, validation, throughput

  ApiIntegrationTest (26 tests)
    - Full API integration against real PostgreSQL + Redis + Kafka
    - Feed, Posts, Reactions, Comments, Messaging, Search,
      Groups, Org, Notifications, User Profiles, AI/Bot, Polls, WebSocket

Run: mvn test -pl social-app
```
