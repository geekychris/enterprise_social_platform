package com.social.app.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests that exercise the full API stack against a real database.
 * Requires PostgreSQL, Redis, and Kafka running.
 * Uses X-Debug-User-Id header for authentication.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper om = new ObjectMapper();

    // Test state shared across ordered tests
    private long testUserId;
    private long testPostId;
    private long testConversationId;
    private long testGroupId;
    private long botUserId;

    // ── Setup: find a valid user ──

    @BeforeAll
    void setUp() throws Exception {
        // Find a user to test with
        MvcResult result = mvc.perform(get("/api/users/search?q=a")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", "72057594037928142"))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> users = om.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {});

        assertFalse(users.isEmpty(), "Need at least one user in the database");
        testUserId = toLong(users.get(0).get("id"));

        // Get bot info
        MvcResult botResult = mvc.perform(get("/api/ai/bot/info")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId)))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> botInfo = om.readValue(
                botResult.getResponse().getContentAsString(),
                new TypeReference<>() {});
        botUserId = toLong(botInfo.get("id"));
    }

    // ══════════════════════════════════════════════════════════════════
    // Feed Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    void feedReturnsPostsWithPagination() throws Exception {
        MvcResult result = mvc.perform(get("/api/feed?limit=5")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId)))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> feed = om.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {});

        assertNotNull(feed.get("posts"));
        List<?> posts = (List<?>) feed.get("posts");
        assertTrue(posts.size() <= 5, "Should respect limit");
        assertNotNull(feed.get("hasMore"));
    }

    @Test
    @Order(2)
    void feedHasRateLimitHeaders() throws Exception {
        mvc.perform(get("/api/feed?limit=1")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId)))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-RateLimit-Limit"))
                .andExpect(header().exists("X-RateLimit-Remaining"));
    }

    // ══════════════════════════════════════════════════════════════════
    // Post Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(10)
    void createPost() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "content", "Integration test post " + System.currentTimeMillis(),
                "visibility", "PUBLIC"
        ));

        MvcResult result = mvc.perform(post("/api/posts")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> post = om.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {});

        testPostId = toLong(post.get("id"));
        assertTrue(testPostId > 0);
        assertNotNull(post.get("content"));
        assertNotNull(post.get("author"));
    }

    @Test
    @Order(11)
    void getPostById() throws Exception {
        mvc.perform(get("/api/posts/" + testPostId)
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.content").exists());
    }

    @Test
    @Order(12)
    void addReactionToPost() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "targetId", testPostId,
                "reactionType", "LIKE"
        ));

        mvc.perform(post("/api/reactions")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @Order(13)
    void addCommentToPost() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "postId", testPostId,
                "content", "Test comment " + System.currentTimeMillis()
        ));

        mvc.perform(post("/api/comments")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    @Order(14)
    void getPostComments() throws Exception {
        mvc.perform(get("/api/posts/" + testPostId + "/comments")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId)))
                .andExpect(status().isOk());
    }

    // ══════════════════════════════════════════════════════════════════
    // Messaging Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(20)
    void createDirectConversation() throws Exception {
        MvcResult result = mvc.perform(post("/api/conversations/direct/" + botUserId)
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId)))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> conv = om.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {});

        testConversationId = toLong(conv.get("id"));
        assertTrue(testConversationId > 0);
        assertEquals("DIRECT", conv.get("type"));
    }

    @Test
    @Order(21)
    void sendMessage() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "content", "Hello from integration test!"
        ));

        mvc.perform(post("/api/conversations/" + testConversationId + "/messages")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.content").value("Hello from integration test!"));
    }

    @Test
    @Order(22)
    void getConversationMessages() throws Exception {
        mvc.perform(get("/api/conversations/" + testConversationId + "/messages")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(23)
    void listConversations() throws Exception {
        mvc.perform(get("/api/conversations")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(24)
    void markConversationRead() throws Exception {
        mvc.perform(post("/api/conversations/" + testConversationId + "/read")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(25)
    void getUnreadCount() throws Exception {
        MvcResult result = mvc.perform(get("/api/messages/unread-count")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId)))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> body = om.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {});
        assertNotNull(body.get("unreadCount"));
    }

    @Test
    @Order(26)
    void catchUpEndpoint() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "conversations", Map.of(String.valueOf(testConversationId), 0)
        ));

        mvc.perform(post("/api/catchup")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updates").exists());
    }

    // ══════════════════════════════════════════════════════════════════
    // Search Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(30)
    void searchAll() throws Exception {
        mvc.perform(get("/api/search?q=test")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits").exists());
    }

    @Test
    @Order(31)
    void searchByType() throws Exception {
        mvc.perform(get("/api/search?q=a&type=GROUP")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits").exists());
    }

    // ══════════════════════════════════════════════════════════════════
    // Group Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(40)
    void getUserGroups() throws Exception {
        mvc.perform(get("/api/groups/mine")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId)))
                .andExpect(status().isOk());
    }

    // ══════════════════════════════════════════════════════════════════
    // Org Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(50)
    void getOrgRoots() throws Exception {
        mvc.perform(get("/api/org/units")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(51)
    void getUserOrgAssignments() throws Exception {
        mvc.perform(get("/api/org/assignments/user/" + testUserId)
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId)))
                .andExpect(status().isOk());
    }

    // ══════════════════════════════════════════════════════════════════
    // Notification Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(60)
    void getNotifications() throws Exception {
        mvc.perform(get("/api/notifications?limit=10")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(61)
    void getNotificationUnreadCount() throws Exception {
        mvc.perform(get("/api/notifications/unread-count")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId)))
                .andExpect(status().isOk());
    }

    // ══════════════════════════════════════════════════════════════════
    // User / Profile Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(70)
    void getUserProfile() throws Exception {
        mvc.perform(get("/api/users/" + testUserId)
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.username").exists());
    }

    @Test
    @Order(71)
    void searchUsers() throws Exception {
        mvc.perform(get("/api/users/search?q=a")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId)))
                .andExpect(status().isOk());
    }

    // ══════════════════════════════════════════════════════════════════
    // AI / Bot Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(80)
    void getBotInfo() throws Exception {
        mvc.perform(get("/api/ai/bot/info")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.username").value("roid"));
    }

    // ══════════════════════════════════════════════════════════════════
    // Poll Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(90)
    void createAndVoteOnPoll() throws Exception {
        // Create a post first
        String postBody = om.writeValueAsString(Map.of(
                "content", "Poll test post",
                "visibility", "PUBLIC"
        ));

        MvcResult postResult = mvc.perform(post("/api/posts")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> postData = om.readValue(
                postResult.getResponse().getContentAsString(),
                new TypeReference<>() {});
        long pollPostId = toLong(postData.get("id"));

        // Create poll
        String pollBody = om.writeValueAsString(Map.of(
                "postId", pollPostId,
                "question", "Test poll?",
                "options", List.of("Yes", "No"),
                "allowMultiple", false
        ));

        MvcResult pollResult = mvc.perform(post("/api/polls")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pollBody))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> poll = om.readValue(
                pollResult.getResponse().getContentAsString(),
                new TypeReference<>() {});
        long pollId = toLong(poll.get("id"));
        List<Map<String, Object>> options = (List<Map<String, Object>>) poll.get("options");
        long optionId = toLong(options.get(0).get("id"));

        // Vote
        String voteBody = om.writeValueAsString(Map.of(
                "optionIds", List.of(optionId)
        ));

        mvc.perform(post("/api/polls/" + pollId + "/vote")
                        .header("X-Tenant-Id", "1")
                        .header("X-Debug-User-Id", String.valueOf(testUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voteBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVotes").exists());
    }

    // ══════════════════════════════════════════════════════════════════
    // WebSocket Endpoint Test
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(100)
    void webSocketEndpointAvailable() throws Exception {
        mvc.perform(get("/ws/info"))
                .andExpect(status().isOk());
    }

    // Helper to handle IDs that may be String or Number
    private static long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("Cannot convert to long: " + val);
    }
}
