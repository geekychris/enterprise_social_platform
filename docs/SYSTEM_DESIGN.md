# WorkSphere System Design

## Overview

WorkSphere is an enterprise social platform comprising a Spring Boot backend, React web frontend, native iOS (SwiftUI) and Android (Jetpack Compose) apps, and an AI-powered bot assistant. The system supports real-time messaging, feed assembly with recommendations, organizational hierarchy, polls, and agent-based AI interactions.

## Architecture Diagram

```mermaid
graph TB
    LB["Load Balancer /<br/>Ingress Controller"]

    LB --> React["React SPA<br/>(Nginx)<br/>Port 80"]
    LB --> API["Social App API<br/>(Spring Boot)<br/>Port 8080<br/>REST API, GraphQL<br/>AI SSE streaming<br/>Rate limiting"]
    LB --> WS["WebSocket Gateway<br/>/ws (STOMP)<br/>(same process)"]

    iOS["iOS App<br/>(SwiftUI)"] --> API
    Android["Android App<br/>(Compose)"] --> API

    API --> PG["PostgreSQL<br/>Primary + Read Replica"]
    API --> Redis["Redis<br/>Cache + Pub/Sub + Feeds"]
    API --> Kafka["Kafka<br/>Event Bus"]
    API --> Ollama["Ollama<br/>LLM"]
    API --> OS["OpenSearch<br/>Full-text Search"]

    PG --> AOEE["AOEE<br/>Graph Engine"]
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

```mermaid
graph TB
    A["HTTP Request"] --> B["RateLimitFilter<br/>(Caffeine-based, per-user/IP, 60-300 req/min)"]
    B --> C["DebugAuthFilter<br/>(X-Debug-User-Id bypass)"]
    C --> D["JwtAuthFilter<br/>(Bearer token validation)"]
    D --> E["Spring Security<br/>(endpoint authorization)"]
    E --> F["Controller<br/>(REST/GraphQL/WebSocket)"]
    F --> G["Service Layer<br/>(business logic)"]
    G --> H["Repository Layer<br/>(JPA/JDBC)"]
    H --> I["PostgreSQL"]
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

```mermaid
graph LR
    subgraph Publishers
        MS["MessageService.send()"]
        PC["PostController.createPost()"]
        RC["ReactionController.addReaction()"]
    end

    EP["EventPublisher"]

    MS --> EP
    PC --> EP
    RC --> EP

    EP --> T1["messages.sent<br/>(4 partitions)"]
    EP --> T2["posts.created<br/>(4 partitions)"]
    EP --> T3["reactions.added<br/>(4 partitions)"]

    T1 --> C1["notification service (future)"]
    T2 --> C2["FeedFanoutConsumer<br/>(fan-out to followers)"]
    T3 --> C3["analytics (future)"]
```

#### WebSocket (Real-Time Messaging)

```mermaid
graph TB
    subgraph Destinations["STOMP over SockJS at /ws"]
        D1["/user/{userId}/queue/messages<br/>New message delivery"]
        D2["/topic/conversation.{id}.typing<br/>Typing indicators"]
    end

    C["Client"] -->|"1. Connect /ws with JWT"| WSA["WebSocketAuthInterceptor<br/>2. Validate token, set userId"]
    WSA -->|"3. Subscribe"| D1
    Send["On message send"] --> MBS["MessageBroadcastService<br/>4. Push to all participants"]
    MBS --> Redis["Redis Pub/Sub<br/>5. Delivery across instances"]
    Redis --> D1
```

#### Rate Limiting

```mermaid
graph LR
    RL["RateLimitFilter<br/>(Caffeine-backed, per-minute windows)"]

    RL --> A["/api/conversations, /api/messages<br/>300 req/min"]
    RL --> B["/api/feed<br/>120 req/min"]
    RL --> C["/api/search<br/>60 req/min"]
    RL --> D["/api/ai/*<br/>60 req/min"]
    RL --> E["Default<br/>200 req/min"]

    RL -.->|"Response headers"| H["X-RateLimit-Limit: 200<br/>X-RateLimit-Remaining: 198<br/>HTTP 429 when exceeded"]
```

#### Caching (Two-Layer)

```mermaid
graph LR
    subgraph CacheService
        L1["L1: Caffeine<br/>(in-process, 10K entries, 5min TTL)"]
        L2["L2: Redis<br/>(shared, configurable TTL)"]
    end

    Read["Read"] --> L1 -->|miss| L2 -->|miss| DB["Database"]
    DB -->|write back| L1
    DB -->|write back| L2

    Write["Write"] --> L1
    Write --> L2

    Evict["Evict"] -.->|invalidate| L1
    Evict -.->|invalidate| L2
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

```mermaid
graph TB
    UM["User Message"] --> BH["BotService.handleMessage()"]

    BH --> TD["Trigger Detection"]
    TD --> TD1["DM with bot user: always respond"]
    TD --> TD2["@roid mention: join + respond"]

    TD --> CG["Context Gathering (BotToolService)"]
    CG --> CG1["User's group memberships"]
    CG --> CG2["User's pages"]
    CG --> CG3["Recent conversation history (6 messages)"]
    CG --> CG4["User's saved memories"]
    CG --> CG5["keyword: group/team -> group posts"]
    CG --> CG6["keyword: page -> page posts"]
    CG --> CG7["keyword: feed/new -> user's feed"]
    CG --> CG8["keyword: search/find -> search results"]
    CG --> CG9["keyword: org/report/manage -> org data"]
    CG --> CG10["keyword: who/which/where -> broad + user search"]
    CG --> CG11["keyword: profile/summarize -> user profile"]

    CG --> LLM["LLM Call (OllamaService)"]
    LLM --> L1["System prompt with action tag format + examples"]
    LLM --> L2["Context + user message"]
    LLM --> L3["Response with potential ACTION tags"]

    LLM --> AP["Action Parsing"]
    AP --> A1["ACTION:CREATE_POST -> BotActionService.createPost()"]
    AP --> A2["ACTION:CREATE_POLL -> BotActionService.createPollPost()"]
    AP --> A3["ACTION:SEND_MESSAGE -> BotActionService.sendMessage()"]
    AP --> A4["ACTION:JOIN_GROUP -> BotActionService.joinGroup()"]
    AP --> A5["REMEMBER -> BotMemoryService.remember()"]

    AP --> TR["Target Resolution"]
    TR --> TR1["resolveTargetWithType(): group name, then page name"]
    TR --> TR2["resolveUserId(): numeric, username, display name"]
    TR --> TR3["Membership verification before posting"]

    TR --> Resp["Response saved as message from bot user"]
```

### Database Schema

```mermaid
graph LR
    subgraph Core
        users["users: profiles, auth, org"]
        posts["posts (partitioned): content, quarterly partitions"]
        comments["comments (partitioned): threaded, max depth 1"]
        reactions["reactions: 6 types, unique per user per target"]
        attachments["attachments: files, images"]
    end

    subgraph Messaging
        conversations["conversations: DIRECT or GROUP"]
        conversation_participants["conversation_participants: membership, last_read_at, visible_from"]
        messages["messages: content, conversation_id, sender_id"]
        conversation_summaries["conversation_summaries: denormalized last message (materialized)"]
        unread_counts["unread_counts: per-user per-conversation count (materialized)"]
    end

    subgraph Social
        follows["follows: user-to-user"]
        friend_requests["friend_requests: send/accept/reject"]
        memberships["memberships: group roles (OWNER/ADMIN/MEMBER)"]
        page_memberships["page_memberships: page follows"]
    end

    subgraph Organization
        org_units["org_units: COMPANY > DIVISION > DEPARTMENT > TEAM"]
        org_assignments["org_assignments: user-org unit, SOLID/DOTTED"]
    end

    subgraph Features
        polls["polls / poll_options / poll_votes: embedded in posts"]
        bot_memory["bot_memory: per-user key-value for AI memory"]
        bot_triggers["bot_triggers: workflow automation rules"]
        notifications["notifications: in-app notifications"]
        feed_entries["feed_entries: pre-computed feed cache"]
    end

    subgraph IDGeneration["ID Generation"]
        GIG["GlobalIdGenerator: (ObjectType &lt;&lt; 56) | sequence"]
        SIG["SnowflakeIdGenerator: (timestamp &lt;&lt; 22) | (nodeId &lt;&lt; 12) | sequence"]
        OT["16 object types: USER, POST, COMMENT, TEAM,<br/>GROUP, PAGE, PROJECT, ATTACHMENT, REACTION,<br/>MESSAGE, INVITE_TOKEN, CONVERSATION, POLL,<br/>POLL_OPTION, BOT_MEMORY, ORG_UNIT, ORG_ASSIGNMENT"]
    end
```

### Kubernetes Deployment

```mermaid
graph TB
    subgraph worksphere["Namespace: worksphere"]
        subgraph Stateful
            postgres["postgres (1 replica, PV)<br/>Primary database"]
            redis["redis (1 replica)<br/>Cache + Pub/Sub"]
            kafka["kafka (1 replica, KRaft)<br/>Event streaming"]
            opensearch["opensearch (1 replica)<br/>Full-text search"]
            ollama["ollama (1 replica)<br/>AI/LLM"]
        end

        subgraph Stateless
            socialapp["social-app (2 replicas)<br/>API + WebSocket"]
            frontend["frontend (2 replicas)<br/>Nginx serving React SPA"]
            aoeeserver["aoee-server (1 replica)<br/>Graph engine"]
            aoeeproxy["aoee-proxy (1 replica)<br/>gRPC-to-HTTP proxy"]
        end

        subgraph Ingress
            R1["/api, /ws, /actuator,<br/>/graphql, /uploads"] --> socialapp
            R2["/"] --> frontend
            WS["WebSocket: enabled<br/>with long timeouts"]
        end
    end
```

### Data Flow Examples

#### Sending a Message

```mermaid
graph TB
    S1["1. Client POST /api/conversations/{id}/messages"]
    S1 --> S2["2. RateLimitFilter checks rate (300/min)"]
    S2 --> S3["3. ConversationController.sendMessage()<br/>validates participant"]
    S3 --> S4["4. MessageService.send()"]

    S4 --> S4a["a. Generate ID (GlobalIdGenerator)"]
    S4a --> S4b["b. Save to PostgreSQL (messages table)"]
    S4b --> S4c["c. Link attachments (if any)"]
    S4c --> S4d["d. ConversationSummaryService.updateSummary()<br/>upsert conversation_summaries"]
    S4d --> S4e["e. UnreadCountService.incrementUnread()<br/>UPDATE unread_counts +1 for all participants"]
    S4e --> S4f["f. MessageBroadcastService.broadcastMessage()"]
    S4f --> WS["SimpMessagingTemplate: push to WebSocket"]
    S4f --> RP["Redis PUBLISH conversation:{id}: other instances"]
    S4f --> S4g["g. EventPublisher.publishMessageSent()<br/>Kafka topic messages.sent"]

    S4g --> S5["5. BotService.handleMessage()<br/>checks if bot should respond (async)"]
    S5 --> S6["6. Response: MessageDto JSON"]
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

```mermaid
graph TB
    P1["1. User sends: create a poll in remote workers:<br/>lunch day? options: Mon, Wed, Fri"]
    P1 --> P2["2. BotService detects DM with bot<br/>triggers response"]
    P2 --> P3["3. Context gathered: user's groups<br/>(includes remote workers), conversation history"]
    P3 --> P4["4. Ollama generates response with<br/>ACTION:CREATE_POLL tag"]
    P4 --> P5["5. parseAndExecuteActions()"]
    P5 --> P5a["a. Extracts CREATE_POLL action"]
    P5a --> P5b["b. resolveTargetWithType: finds group ID"]
    P5b --> P5c["c. BotActionService.createPollPost()"]
    P5c --> V["Verifies user is group member"]
    P5c --> CP["Creates PostEntity with question"]
    P5c --> PO["Creates PollEntity with 3 PollOptionEntities"]
    P5c --> P6["6. Bot response saved as message<br/>(action tag stripped)"]
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
