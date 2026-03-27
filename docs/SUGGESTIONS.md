# Feature Suggestions for Roid & WorkSphere

These are planned enhancements to explore in future iterations.

## 5. Semantic Search (Vector Embeddings)

Replace keyword-based search with embedding-powered semantic search.

**Approach:**
- Use `nomic-embed-text` model already available in Ollama for generating embeddings
- Add `pgvector` extension to PostgreSQL for vector storage/similarity search
- Index posts, comments, and messages as vectors
- "Find posts about improving developer productivity" would match even without exact keyword overlap

**Tools needed:** `embeddingService`, pgvector column on posts table, background indexing job

---

## 6. Meeting/Discussion Notes Generator

After a long conversation, generate structured notes.

**Approach:**
- Template prompt: extract attendees, key points, decisions, action items, open questions
- "Generate meeting notes for this conversation"
- Option to auto-post notes as a group post

**Tools needed:** `generateMeetingNotes` template prompt, `createPost` action (already built)

---

## 7. People Finder / Expertise Discovery

Find people by skills, activity, or role.

**Examples:**
- "Who knows about Kubernetes?"
- "Find someone in marketing who can help with copywriting"
- "Who are the most active contributors in the engineering group?"

**Approach:**
- Scan user profiles (skills, interests fields) + post content frequency analysis
- `expertiseSearch` tool that combines profile data with activity ranking

---

## 8. Content Translation

First-class translation support across the platform.

**Approach:**
- Translate button on any post/message
- Auto-detect language preference
- "Translate this conversation to Spanish"
- Use Ollama's multilingual capabilities

**Tools needed:** Language detection, `translateContent` tool, UI translate button per message/post

---

## 9. Reaction Analytics

Engagement intelligence across the platform.

**Examples:**
- "What are the most liked posts in engineering this month?"
- "Which of my posts got the most engagement?"
- "What topics get the most reactions?"

**Tools needed:** `reactionAnalytics` tool with aggregate queries over reactions table

---

## 10. Writing Assistant

Help compose content directly in chat.

**Examples:**
- "Help me write a professional announcement about the new office policy"
- "Rewrite this more concisely: [text]"
- "Draft a welcome message for new team members"

**Tools needed:** No new tools — pure LLM capability via improved prompt detection

---

## 11. Onboarding Buddy

Automated guide for new users.

**Approach:**
- Roid proactively DMs new users with tips
- "What groups should I join?" — recommends based on department/role
- "Who should I connect with?" — suggests colleagues
- New user detection hook in registration flow

**Tools needed:** `recommendGroups`, `recommendPeople` (based on department/role), registration hook

---

## 12. Inline Post Comment Mentions

Allow @roid in post comments (not just messages).

**Approach:**
- Hook into `CommentController.createComment()`
- When @roid is mentioned in a comment, bot replies as a comment on the same post
- "Summarize the reactions to this post" → bot comments with analysis

**Tools needed:** Hook in CommentController, `postReactionSummary` tool

---

## 13. Workflow Automations / Triggers

Event-driven rules engine.

**Examples:**
- "When someone posts in announcements, send me a DM summary"
- "If anyone mentions 'outage', alert me"
- "Every Friday, summarize the week in my top 3 groups"

**Approach:**
- `triggerStore` table: user_id, event_type, condition, action
- Event processor that checks triggers on post/message creation
- Scheduled trigger runner for recurring rules

**Tools needed:** `triggerStore` DB table, `webhookProcessor`, scheduled task runner

---

## Priority Matrix

| Feature | Impact | Effort | Priority |
|---------|--------|--------|----------|
| Semantic Search | High | Medium | P1 |
| Meeting Notes | High | Low | P1 |
| People Finder | Medium | Medium | P2 |
| Translation | Medium | Low | P2 |
| Reaction Analytics | Medium | Low | P2 |
| Writing Assistant | Medium | Low | P2 |
| Onboarding Buddy | Medium | Medium | P3 |
| Post Comment Mentions | Medium | Low | P3 |
| Workflow Automations | High | High | P3 |
