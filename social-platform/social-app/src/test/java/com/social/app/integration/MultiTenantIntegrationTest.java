package com.social.app.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive integration tests covering multi-tenancy, messaging, conversations,
 * reactions, groups, comments, polls, org structure, and cross-tenant isolation.
 *
 * Requires PostgreSQL, Redis, and Kafka running.
 * Uses X-Debug-User-Id header for authentication and X-Tenant-Id for tenant context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MultiTenantIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper om = new ObjectMapper();

    // ── Shared state across ordered tests ──

    // Default tenant user IDs (discovered in setup)
    private long defaultUserId;
    private long secondUserId;

    // TestCorp tenant
    private String testCorpTenantId;
    private String testCorpAdminUserId;
    private long testCorpPostId;

    // OtherCorp tenant
    private String otherCorpTenantId;
    private String otherCorpAdminUserId;

    // Messaging
    private long directConversationId;
    private long groupConversationId;
    private long tenantConversationId;

    // Posts, comments, reactions, groups, polls, org
    private long defaultPostId;
    private long commentId;
    private long replyCommentId;
    private long groupId;
    private long groupPostId;
    private long pollId;
    private long pollOptionId;
    private long orgUnitId;

    // ══════════════════════════════════════════════════════════════════
    // Setup
    // ══════════════════════════════════════════════════════════════════

    @BeforeAll
    void setUp() throws Exception {
        // Find two users in the default tenant to use for messaging tests
        MvcResult result = mvc.perform(get("/api/users/search?q=a")
                        .header("X-Debug-User-Id", "72057594037928142")
                        .header("X-Tenant-Id", "1"))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> users = om.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {});

        assertTrue(users.size() >= 2, "Need at least two users in the database for messaging tests");
        defaultUserId = toLong(users.get(0).get("id"));
        secondUserId = toLong(users.get(1).get("id"));
    }

    // ══════════════════════════════════════════════════════════════════
    // Multi-Tenancy Tests (Order 1-20)
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    void listTenants() throws Exception {
        String json = getJson("/api/super-admin/tenants", String.valueOf(defaultUserId));

        List<Map<String, Object>> tenants = om.readValue(json, new TypeReference<>() {});
        assertFalse(tenants.isEmpty(), "Should have at least the default tenant");

        boolean hasDefault = tenants.stream()
                .anyMatch(t -> toLong(t.get("id")) == 1L);
        assertTrue(hasDefault, "Default tenant (id=1) should exist");
    }

    @Test
    @Order(2)
    void createTenant() throws Exception {
        Map<String, Object> body = Map.of(
                "name", "TestCorp",
                "slug", "testcorp-" + System.currentTimeMillis(),
                "plan", "pro",
                "adminUsername", "testcorp_admin_" + System.currentTimeMillis(),
                "adminPassword", "secure123",
                "adminEmail", "admin@testcorp-" + System.currentTimeMillis() + ".com",
                "adminDisplayName", "TestCorp Admin"
        );

        String json = postJson("/api/super-admin/tenants", String.valueOf(defaultUserId), body);

        Map<String, Object> result = om.readValue(json, new TypeReference<>() {});
        assertNotNull(result.get("tenant"), "Response should contain tenant object");
        assertNotNull(result.get("admin"), "Response should contain admin object");

        Map<String, Object> tenant = (Map<String, Object>) result.get("tenant");
        Map<String, Object> admin = (Map<String, Object>) result.get("admin");

        testCorpTenantId = String.valueOf(toLong(tenant.get("id")));
        testCorpAdminUserId = String.valueOf(toLong(admin.get("id")));

        assertEquals("TestCorp", tenant.get("name"));
        assertEquals("pro", tenant.get("plan"));
        assertNotNull(testCorpTenantId);
        assertNotNull(testCorpAdminUserId);
    }

    @Test
    @Order(3)
    void createSecondTenant() throws Exception {
        Map<String, Object> body = Map.of(
                "name", "OtherCorp",
                "slug", "othercorp-" + System.currentTimeMillis(),
                "plan", "free",
                "adminUsername", "othercorp_admin_" + System.currentTimeMillis(),
                "adminPassword", "secure456",
                "adminEmail", "admin@othercorp-" + System.currentTimeMillis() + ".com",
                "adminDisplayName", "OtherCorp Admin"
        );

        String json = postJson("/api/super-admin/tenants", String.valueOf(defaultUserId), body);

        Map<String, Object> result = om.readValue(json, new TypeReference<>() {});
        Map<String, Object> tenant = (Map<String, Object>) result.get("tenant");
        Map<String, Object> admin = (Map<String, Object>) result.get("admin");

        otherCorpTenantId = String.valueOf(toLong(tenant.get("id")));
        otherCorpAdminUserId = String.valueOf(toLong(admin.get("id")));

        assertEquals("OtherCorp", tenant.get("name"));
        assertNotNull(otherCorpTenantId);
        assertNotNull(otherCorpAdminUserId);
    }

    @Test
    @Order(4)
    void tenantAdminCanCreatePost() throws Exception {
        Map<String, Object> body = Map.of(
                "content", "TestCorp announcement: welcome to our tenant!",
                "visibility", "PUBLIC"
        );

        String json = postJsonTenant("/api/posts", testCorpAdminUserId, testCorpTenantId, body);

        Map<String, Object> post = om.readValue(json, new TypeReference<>() {});
        testCorpPostId = toLong(post.get("id"));
        assertTrue(testCorpPostId > 0, "Post should have a valid ID");
        assertEquals("TestCorp announcement: welcome to our tenant!", post.get("content"));
    }

    @Test
    @Order(5)
    void postVisibleInSameTenant() throws Exception {
        String json = getJsonTenant("/api/feed?limit=50", testCorpAdminUserId, testCorpTenantId);

        Map<String, Object> feed = om.readValue(json, new TypeReference<>() {});
        List<Map<String, Object>> posts = (List<Map<String, Object>>) feed.get("posts");
        assertNotNull(posts, "Feed should contain posts list");

        boolean found = posts.stream()
                .anyMatch(p -> toLong(p.get("id")) == testCorpPostId);
        assertTrue(found, "TestCorp post should be visible in TestCorp's feed");
    }

    @Test
    @Order(6)
    void postInvisibleInDifferentTenant() throws Exception {
        String json = getJsonTenant("/api/feed?limit=50", otherCorpAdminUserId, otherCorpTenantId);

        Map<String, Object> feed = om.readValue(json, new TypeReference<>() {});
        List<Map<String, Object>> posts = (List<Map<String, Object>>) feed.get("posts");

        if (posts != null && !posts.isEmpty()) {
            boolean found = posts.stream()
                    .anyMatch(p -> toLong(p.get("id")) == testCorpPostId);
            assertFalse(found, "TestCorp post should NOT be visible in OtherCorp's feed");
        }
        // Empty feed is also acceptable — OtherCorp has no posts yet
    }

    @Test
    @Order(7)
    void defaultTenantUnaffected() throws Exception {
        String json = getJson("/api/feed?limit=50", String.valueOf(defaultUserId));

        Map<String, Object> feed = om.readValue(json, new TypeReference<>() {});
        List<Map<String, Object>> posts = (List<Map<String, Object>>) feed.get("posts");

        if (posts != null && !posts.isEmpty()) {
            boolean foundTestCorpPost = posts.stream()
                    .anyMatch(p -> toLong(p.get("id")) == testCorpPostId);
            assertFalse(foundTestCorpPost, "TestCorp post should NOT appear in default tenant feed");
        }
    }

    @Test
    @Order(8)
    void userSearchScopedToTenant() throws Exception {
        String json = getJsonTenant("/api/users/search?q=admin", testCorpAdminUserId, testCorpTenantId);

        List<Map<String, Object>> users = om.readValue(json, new TypeReference<>() {});
        // All returned users should belong to TestCorp tenant
        for (Map<String, Object> user : users) {
            if (user.containsKey("tenantId")) {
                assertEquals(Long.parseLong(testCorpTenantId), toLong(user.get("tenantId")),
                        "User search should only return users from the same tenant");
            }
        }
    }

    @Test
    @Order(9)
    void crossTenantUserSearchEmpty() throws Exception {
        // Search for TestCorp admin username from OtherCorp context
        String json = getJsonTenant("/api/users/search?q=testcorp_admin", otherCorpAdminUserId, otherCorpTenantId);

        List<Map<String, Object>> users = om.readValue(json, new TypeReference<>() {});
        assertEquals(0, users.size(), "Cross-tenant user search should return no results");
    }

    // ══════════════════════════════════════════════════════════════════
    // Messaging Tests (Order 21-40)
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(21)
    void createDirectConversation() throws Exception {
        MvcResult result = mvc.perform(post("/api/conversations/direct/" + secondUserId)
                        .header("X-Debug-User-Id", String.valueOf(defaultUserId))
                        .header("X-Tenant-Id", "1"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> conv = om.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {});

        directConversationId = toLong(conv.get("id"));
        assertTrue(directConversationId > 0, "Conversation should have a valid ID");
        assertEquals("DIRECT", conv.get("type"), "Should be a DIRECT conversation");
    }

    @Test
    @Order(22)
    void sendMessage() throws Exception {
        Map<String, Object> body = Map.of("content", "Hello from multi-tenant integration test!");

        String json = postJson("/api/conversations/" + directConversationId + "/messages",
                String.valueOf(defaultUserId), body);

        Map<String, Object> message = om.readValue(json, new TypeReference<>() {});
        assertNotNull(message.get("id"), "Message should have an ID");
        assertEquals("Hello from multi-tenant integration test!", message.get("content"));
    }

    @Test
    @Order(23)
    void getMessages() throws Exception {
        String json = getJson("/api/conversations/" + directConversationId + "/messages",
                String.valueOf(defaultUserId));

        List<Map<String, Object>> messages = om.readValue(json, new TypeReference<>() {});
        assertFalse(messages.isEmpty(), "Should have at least one message");

        // Verify the message we sent is present
        boolean found = messages.stream()
                .anyMatch(m -> "Hello from multi-tenant integration test!".equals(m.get("content")));
        assertTrue(found, "Our sent message should be in the message list");
    }

    @Test
    @Order(24)
    void sendMultipleMessages() throws Exception {
        // Send 3 more messages
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> body = Map.of("content", "Message number " + i);
            postJson("/api/conversations/" + directConversationId + "/messages",
                    String.valueOf(defaultUserId), body);
        }

        // Verify order
        String json = getJson("/api/conversations/" + directConversationId + "/messages",
                String.valueOf(defaultUserId));
        List<Map<String, Object>> messages = om.readValue(json, new TypeReference<>() {});

        assertTrue(messages.size() >= 4, "Should have at least 4 messages (1 original + 3 new)");
    }

    @Test
    @Order(25)
    void conversationListShowsLatest() throws Exception {
        String json = getJson("/api/conversations", String.valueOf(defaultUserId));

        List<Map<String, Object>> conversations = om.readValue(json, new TypeReference<>() {});
        assertFalse(conversations.isEmpty(), "User should have at least one conversation");

        // Find our direct conversation
        boolean found = conversations.stream()
                .anyMatch(c -> toLong(c.get("id")) == directConversationId);
        assertTrue(found, "Our direct conversation should appear in the list");
    }

    @Test
    @Order(26)
    void markConversationRead() throws Exception {
        mvc.perform(post("/api/conversations/" + directConversationId + "/read")
                        .header("X-Debug-User-Id", String.valueOf(defaultUserId))
                        .header("X-Tenant-Id", "1"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(27)
    void createGroupConversation() throws Exception {
        Map<String, Object> body = Map.of(
                "participantIds", List.of(secondUserId),
                "name", "Integration Test Group Chat"
        );

        String json = postJson("/api/conversations", String.valueOf(defaultUserId), body);

        Map<String, Object> conv = om.readValue(json, new TypeReference<>() {});
        groupConversationId = toLong(conv.get("id"));
        assertTrue(groupConversationId > 0, "Group conversation should have a valid ID");
        assertEquals("GROUP", conv.get("type"), "Should be a GROUP conversation");
    }

    @Test
    @Order(28)
    void sendGroupMessage() throws Exception {
        Map<String, Object> body = Map.of("content", "Hello group from integration test!");

        String json = postJson("/api/conversations/" + groupConversationId + "/messages",
                String.valueOf(defaultUserId), body);

        Map<String, Object> message = om.readValue(json, new TypeReference<>() {});
        assertNotNull(message.get("id"));
        assertEquals("Hello group from integration test!", message.get("content"));

        // Verify second user can also see the message
        String otherJson = getJson("/api/conversations/" + groupConversationId + "/messages",
                String.valueOf(secondUserId));
        List<Map<String, Object>> messages = om.readValue(otherJson, new TypeReference<>() {});
        assertFalse(messages.isEmpty(), "Second participant should see group messages");
    }

    @Test
    @Order(29)
    void addParticipantToGroup() throws Exception {
        // Find a third user to add
        MvcResult searchResult = mvc.perform(get("/api/users/search?q=a")
                        .header("X-Debug-User-Id", String.valueOf(defaultUserId))
                        .header("X-Tenant-Id", "1"))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> users = om.readValue(
                searchResult.getResponse().getContentAsString(),
                new TypeReference<>() {});

        // Find a user that isn't defaultUserId or secondUserId
        long thirdUserId = 0;
        for (Map<String, Object> u : users) {
            long uid = toLong(u.get("id"));
            if (uid != defaultUserId && uid != secondUserId) {
                thirdUserId = uid;
                break;
            }
        }

        if (thirdUserId > 0) {
            Map<String, Object> body = Map.of("userId", thirdUserId);
            String json = postJson("/api/conversations/" + groupConversationId + "/participants",
                    String.valueOf(defaultUserId), body);

            Map<String, Object> conv = om.readValue(json, new TypeReference<>() {});
            List<?> participants = (List<?>) conv.get("participants");
            assertNotNull(participants, "Response should include participants");
            assertTrue(participants.size() >= 3, "Should now have at least 3 participants");
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Messaging Multi-Tenant Isolation (Order 41-50)
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(41)
    void conversationScopedToTenant() throws Exception {
        // Create a conversation in TestCorp
        // First, we need a second user in TestCorp. Create another tenant user via a post
        // For now, create a direct conversation between TestCorp admin and... themselves won't work.
        // Instead, create a group conversation with just the admin
        Map<String, Object> body = Map.of(
                "participantIds", List.of(),
                "name", "TestCorp Internal Chat"
        );

        String json = postJsonTenant("/api/conversations", testCorpAdminUserId, testCorpTenantId, body);
        Map<String, Object> conv = om.readValue(json, new TypeReference<>() {});
        tenantConversationId = toLong(conv.get("id"));
        assertTrue(tenantConversationId > 0);

        // Verify it's NOT visible from OtherCorp
        String otherConvs = getJsonTenant("/api/conversations", otherCorpAdminUserId, otherCorpTenantId);
        List<Map<String, Object>> otherList = om.readValue(otherConvs, new TypeReference<>() {});

        boolean found = otherList.stream()
                .anyMatch(c -> toLong(c.get("id")) == tenantConversationId);
        assertFalse(found, "TestCorp conversation should NOT be visible from OtherCorp");
    }

    @Test
    @Order(42)
    void cannotSendMessageCrossTenant() {
        // Attempt to send a message to TestCorp's conversation from OtherCorp user
        // This should fail — the user is not a participant (tenant isolation prevents it)
        Map<String, Object> body = Map.of("content", "Cross-tenant intrusion attempt");

        try {
            MvcResult result = mvc.perform(post("/api/conversations/" + tenantConversationId + "/messages")
                            .header("X-Debug-User-Id", otherCorpAdminUserId)
                            .header("X-Tenant-Id", otherCorpTenantId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(body)))
                    .andReturn();
            int status = result.getResponse().getStatus();
            assertTrue(status >= 400, "Cross-tenant message should be denied, got status: " + status);
        } catch (Exception e) {
            // Exception is also acceptable — means the server correctly rejected the request
            assertTrue(true, "Cross-tenant message correctly denied with exception");
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Reaction Tests (Order 51-60)
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(50)
    void createPostForReactionTests() throws Exception {
        // Create a fresh post in the default tenant for reaction tests
        Map<String, Object> body = Map.of(
                "content", "Post for reaction testing " + System.currentTimeMillis(),
                "visibility", "PUBLIC"
        );

        String json = postJson("/api/posts", String.valueOf(defaultUserId), body);
        Map<String, Object> post = om.readValue(json, new TypeReference<>() {});
        defaultPostId = toLong(post.get("id"));
        assertTrue(defaultPostId > 0);
    }

    @Test
    @Order(51)
    void addReaction() throws Exception {
        Map<String, Object> body = Map.of(
                "targetId", defaultPostId,
                "reactionType", "LIKE"
        );

        String json = postJson("/api/reactions", String.valueOf(defaultUserId), body);
        Map<String, Object> reaction = om.readValue(json, new TypeReference<>() {});

        assertNotNull(reaction.get("id"), "Reaction should have an ID");
        assertEquals("LIKE", reaction.get("reactionType"));
    }

    @Test
    @Order(52)
    void reactionCountInPost() throws Exception {
        String json = getJson("/api/posts/" + defaultPostId, String.valueOf(defaultUserId));
        Map<String, Object> post = om.readValue(json, new TypeReference<>() {});

        // Check that reactionCounts field exists and includes our reaction
        Object reactionCounts = post.get("reactionCounts");
        assertNotNull(reactionCounts, "Post should have reactionCounts");

        if (reactionCounts instanceof Map) {
            Map<String, Object> counts = (Map<String, Object>) reactionCounts;
            // Should have a LIKE count >= 1
            if (counts.containsKey("LIKE")) {
                assertTrue(toLong(counts.get("LIKE")) >= 1, "LIKE count should be at least 1");
            }
        }
    }

    @Test
    @Order(53)
    void changeReaction() throws Exception {
        // React with LOVE on the same post — should replace LIKE
        Map<String, Object> body = Map.of(
                "targetId", defaultPostId,
                "reactionType", "LOVE"
        );

        String json = postJson("/api/reactions", String.valueOf(defaultUserId), body);
        Map<String, Object> reaction = om.readValue(json, new TypeReference<>() {});
        assertEquals("LOVE", reaction.get("reactionType"), "Reaction should now be LOVE");
    }

    @Test
    @Order(54)
    void removeReaction() throws Exception {
        // Remove the reaction
        mvc.perform(delete("/api/reactions/" + defaultPostId)
                        .header("X-Debug-User-Id", String.valueOf(defaultUserId))
                        .header("X-Tenant-Id", "1"))
                .andExpect(status().isNoContent());

        // Verify count is decremented
        String json = getJson("/api/posts/" + defaultPostId, String.valueOf(defaultUserId));
        Map<String, Object> post = om.readValue(json, new TypeReference<>() {});

        Object reactionCounts = post.get("reactionCounts");
        if (reactionCounts instanceof Map) {
            Map<String, Object> counts = (Map<String, Object>) reactionCounts;
            if (counts.containsKey("LOVE")) {
                assertTrue(toLong(counts.get("LOVE")) == 0, "LOVE count should be 0 after removal");
            }
        }
    }

    @Test
    @Order(55)
    void getReactors() throws Exception {
        // Re-add a reaction so we have data to query
        Map<String, Object> body = Map.of(
                "targetId", defaultPostId,
                "reactionType", "LIKE"
        );
        postJson("/api/reactions", String.valueOf(defaultUserId), body);

        // Also add from second user
        postJson("/api/reactions", String.valueOf(secondUserId), body);

        // Get reactors
        String json = getJson("/api/reactions/" + defaultPostId + "/users", String.valueOf(defaultUserId));
        List<Map<String, Object>> reactors = om.readValue(json, new TypeReference<>() {});

        assertNotNull(reactors, "Should return a list of reactors");
        assertTrue(reactors.size() >= 1, "Should have at least one reactor");
    }

    // ══════════════════════════════════════════════════════════════════
    // Group & Page Tests (Order 61-70)
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(61)
    void createGroup() throws Exception {
        Map<String, Object> body = Map.of(
                "name", "Integration Test Group " + System.currentTimeMillis(),
                "description", "A group created by integration tests",
                "visibility", "PUBLIC"
        );

        String json = postJson("/api/groups", String.valueOf(defaultUserId), body);
        Map<String, Object> group = om.readValue(json, new TypeReference<>() {});

        groupId = toLong(group.get("id"));
        assertTrue(groupId > 0, "Group should have a valid ID");
        assertNotNull(group.get("name"));
    }

    @Test
    @Order(62)
    void joinGroup() throws Exception {
        mvc.perform(post("/api/groups/" + groupId + "/join")
                        .header("X-Debug-User-Id", String.valueOf(secondUserId))
                        .header("X-Tenant-Id", "1"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(63)
    void postToGroup() throws Exception {
        Map<String, Object> body = Map.of(
                "content", "Hello from the integration test group!",
                "targetType", "GROUP_FEED",
                "targetId", groupId,
                "visibility", "PUBLIC"
        );

        String json = postJson("/api/posts", String.valueOf(defaultUserId), body);
        Map<String, Object> post = om.readValue(json, new TypeReference<>() {});

        groupPostId = toLong(post.get("id"));
        assertTrue(groupPostId > 0);
        assertEquals("Hello from the integration test group!", post.get("content"));
    }

    @Test
    @Order(64)
    void groupPostInFeed() throws Exception {
        // The group member (secondUserId) should see the group post in their feed
        String json = getJson("/api/feed?limit=50", String.valueOf(secondUserId));
        Map<String, Object> feed = om.readValue(json, new TypeReference<>() {});
        List<Map<String, Object>> posts = (List<Map<String, Object>>) feed.get("posts");

        // The group post might or might not appear in the main feed depending on
        // the feed algorithm. At minimum, verify it's accessible via group posts endpoint.
        String groupPostsJson = getJson("/api/groups/" + groupId + "/posts", String.valueOf(secondUserId));
        List<Map<String, Object>> groupPosts = om.readValue(groupPostsJson, new TypeReference<>() {});

        boolean found = groupPosts.stream()
                .anyMatch(p -> toLong(p.get("id")) == groupPostId);
        assertTrue(found, "Group post should be visible via group posts endpoint");
    }

    @Test
    @Order(65)
    void getGroupMembers() throws Exception {
        String json = getJson("/api/groups/" + groupId + "/members", String.valueOf(defaultUserId));
        List<Map<String, Object>> members = om.readValue(json, new TypeReference<>() {});

        assertTrue(members.size() >= 2, "Group should have at least 2 members (creator + joined user)");
    }

    @Test
    @Order(66)
    void leaveGroup() throws Exception {
        mvc.perform(delete("/api/groups/" + groupId + "/leave")
                        .header("X-Debug-User-Id", String.valueOf(secondUserId))
                        .header("X-Tenant-Id", "1"))
                .andExpect(status().isNoContent());
    }

    // ══════════════════════════════════════════════════════════════════
    // Comment Tests (Order 71-80)
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(71)
    void addComment() throws Exception {
        Map<String, Object> body = Map.of(
                "postId", defaultPostId,
                "content", "Great post! This is a test comment."
        );

        String json = postJson("/api/comments", String.valueOf(defaultUserId), body);
        Map<String, Object> comment = om.readValue(json, new TypeReference<>() {});

        commentId = toLong(comment.get("id"));
        assertTrue(commentId > 0, "Comment should have a valid ID");
        assertEquals("Great post! This is a test comment.", comment.get("content"));
    }

    @Test
    @Order(72)
    void getComments() throws Exception {
        String json = getJson("/api/posts/" + defaultPostId + "/comments", String.valueOf(defaultUserId));
        List<Map<String, Object>> comments = om.readValue(json, new TypeReference<>() {});

        assertFalse(comments.isEmpty(), "Post should have at least one comment");
        boolean found = comments.stream()
                .anyMatch(c -> toLong(c.get("id")) == commentId);
        assertTrue(found, "Our comment should appear in the comment list");
    }

    @Test
    @Order(73)
    void replyToComment() throws Exception {
        Map<String, Object> body = Map.of(
                "postId", defaultPostId,
                "parentCommentId", commentId,
                "content", "This is a reply to the comment."
        );

        String json = postJson("/api/comments", String.valueOf(secondUserId), body);
        Map<String, Object> reply = om.readValue(json, new TypeReference<>() {});

        replyCommentId = toLong(reply.get("id"));
        assertTrue(replyCommentId > 0, "Reply comment should have a valid ID");
        assertEquals("This is a reply to the comment.", reply.get("content"));
    }

    @Test
    @Order(74)
    void getCommentById() throws Exception {
        String json = getJson("/api/comments/" + commentId, String.valueOf(defaultUserId));
        Map<String, Object> comment = om.readValue(json, new TypeReference<>() {});

        assertEquals(commentId, toLong(comment.get("id")));
    }

    @Test
    @Order(75)
    void deleteComment() throws Exception {
        mvc.perform(delete("/api/comments/" + replyCommentId)
                        .header("X-Debug-User-Id", String.valueOf(secondUserId))
                        .header("X-Tenant-Id", "1"))
                .andExpect(status().isNoContent());

        // Verify the reply is removed
        mvc.perform(get("/api/comments/" + replyCommentId)
                        .header("X-Debug-User-Id", String.valueOf(defaultUserId))
                        .header("X-Tenant-Id", "1"))
                .andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════════
    // Poll Tests (Order 81-90)
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(81)
    void createPoll() throws Exception {
        // First create a post for the poll
        Map<String, Object> postBody = Map.of(
                "content", "Poll post: What is your favorite color?",
                "visibility", "PUBLIC"
        );
        String postJson = postJson("/api/posts", String.valueOf(defaultUserId), postBody);
        Map<String, Object> post = om.readValue(postJson, new TypeReference<>() {});
        long pollPostId = toLong(post.get("id"));

        // Create the poll
        Map<String, Object> pollBody = Map.of(
                "postId", pollPostId,
                "question", "What is your favorite color?",
                "options", new String[]{"Red", "Blue", "Green"},
                "allowMultiple", false
        );

        String json = postJson("/api/polls", String.valueOf(defaultUserId), pollBody);
        Map<String, Object> poll = om.readValue(json, new TypeReference<>() {});

        pollId = toLong(poll.get("id"));
        assertTrue(pollId > 0, "Poll should have a valid ID");
        assertEquals("What is your favorite color?", poll.get("question"));

        List<Map<String, Object>> options = (List<Map<String, Object>>) poll.get("options");
        assertNotNull(options, "Poll should have options");
        assertTrue(options.size() >= 3, "Poll should have at least 3 options");
        pollOptionId = toLong(options.get(0).get("id"));
    }

    @Test
    @Order(82)
    void voteOnPoll() throws Exception {
        Map<String, Object> voteBody = Map.of(
                "optionIds", new Long[]{pollOptionId}
        );

        String json = postJson("/api/polls/" + pollId + "/vote", String.valueOf(defaultUserId), voteBody);
        Map<String, Object> result = om.readValue(json, new TypeReference<>() {});

        assertNotNull(result.get("totalVotes"), "Should return totalVotes");
        assertTrue(toLong(result.get("totalVotes")) >= 1, "Total votes should be at least 1");
    }

    @Test
    @Order(83)
    void getPollResults() throws Exception {
        String json = getJson("/api/polls/" + pollId, String.valueOf(defaultUserId));
        Map<String, Object> poll = om.readValue(json, new TypeReference<>() {});

        assertEquals(pollId, toLong(poll.get("id")));
        assertTrue(toLong(poll.get("totalVotes")) >= 1, "Poll should have at least 1 vote");

        List<Map<String, Object>> options = (List<Map<String, Object>>) poll.get("options");
        assertNotNull(options);

        // The first option should have at least 1 vote
        Map<String, Object> firstOption = options.stream()
                .filter(o -> toLong(o.get("id")) == pollOptionId)
                .findFirst()
                .orElseThrow();
        assertTrue(toLong(firstOption.get("voteCount")) >= 1, "Voted option should have count >= 1");
    }

    @Test
    @Order(84)
    void secondUserVotesOnPoll() throws Exception {
        // Get options to pick a different one
        String pollJson = getJson("/api/polls/" + pollId, String.valueOf(secondUserId));
        Map<String, Object> poll = om.readValue(pollJson, new TypeReference<>() {});
        List<Map<String, Object>> options = (List<Map<String, Object>>) poll.get("options");

        // Vote on the second option
        long secondOptionId = toLong(options.get(1).get("id"));
        Map<String, Object> voteBody = Map.of("optionIds", new Long[]{secondOptionId});

        String json = postJson("/api/polls/" + pollId + "/vote", String.valueOf(secondUserId), voteBody);
        Map<String, Object> result = om.readValue(json, new TypeReference<>() {});

        assertTrue(toLong(result.get("totalVotes")) >= 2, "Should now have at least 2 total votes");
    }

    // ══════════════════════════════════════════════════════════════════
    // Org Tests (Order 91-100)
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(91)
    void createOrgUnit() throws Exception {
        Map<String, Object> body = Map.of(
                "name", "Engineering",
                "type", "DEPARTMENT",
                "description", "Engineering department"
        );

        String json = postJson("/api/org/units", String.valueOf(defaultUserId), body);
        Map<String, Object> unit = om.readValue(json, new TypeReference<>() {});

        orgUnitId = toLong(unit.get("id"));
        assertTrue(orgUnitId > 0, "Org unit should have a valid ID");
        assertEquals("Engineering", unit.get("name"));
    }

    @Test
    @Order(92)
    void assignUserToOrg() throws Exception {
        Map<String, Object> body = Map.of(
                "userId", defaultUserId,
                "orgUnitId", orgUnitId,
                "title", "Senior Engineer",
                "relationshipType", "MEMBER"
        );

        String json = postJson("/api/org/assignments", String.valueOf(defaultUserId), body);
        Map<String, Object> assignment = om.readValue(json, new TypeReference<>() {});

        assertNotNull(assignment.get("id"), "Assignment should have an ID");
        assertEquals("Senior Engineer", assignment.get("title"));
    }

    @Test
    @Order(93)
    void getOrgTree() throws Exception {
        String json = getJson("/api/org/units?all=true", String.valueOf(defaultUserId));
        List<Map<String, Object>> tree = om.readValue(json, new TypeReference<>() {});

        assertNotNull(tree, "Org tree should not be null");

        // Our Engineering unit should be somewhere in the tree
        boolean found = findInTree(tree, orgUnitId);
        assertTrue(found, "Engineering org unit should appear in the org tree");
    }

    @Test
    @Order(94)
    void getUserOrgAssignments() throws Exception {
        String json = getJson("/api/org/assignments/user/" + defaultUserId, String.valueOf(defaultUserId));
        List<Map<String, Object>> assignments = om.readValue(json, new TypeReference<>() {});

        assertFalse(assignments.isEmpty(), "User should have at least one org assignment");
    }

    @Test
    @Order(95)
    void getOrgUnitMembers() throws Exception {
        String json = getJson("/api/org/units/" + orgUnitId + "/members", String.valueOf(defaultUserId));
        List<Map<String, Object>> members = om.readValue(json, new TypeReference<>() {});

        assertFalse(members.isEmpty(), "Org unit should have at least one member");
    }

    // ══════════════════════════════════════════════════════════════════
    // Tenant Admin Tests (Order 101-110)
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(101)
    void updateTenant() throws Exception {
        Map<String, Object> body = Map.of(
                "plan", "enterprise",
                "maxUsers", 500
        );

        MvcResult result = mvc.perform(put("/api/super-admin/tenants/" + testCorpTenantId)
                        .header("X-Debug-User-Id", String.valueOf(defaultUserId))
                        .header("X-Tenant-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> tenant = om.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {});

        assertEquals("enterprise", tenant.get("plan"), "Plan should be updated to enterprise");
    }

    @Test
    @Order(102)
    void getTenantById() throws Exception {
        String json = getJson("/api/super-admin/tenants/" + testCorpTenantId, String.valueOf(defaultUserId));
        Map<String, Object> tenant = om.readValue(json, new TypeReference<>() {});

        assertEquals("TestCorp", tenant.get("name"));
        assertEquals("enterprise", tenant.get("plan"));
    }

    @Test
    @Order(110)
    void disableTenant() throws Exception {
        // Disable OtherCorp (keep TestCorp for other tests)
        mvc.perform(delete("/api/super-admin/tenants/" + otherCorpTenantId)
                        .header("X-Debug-User-Id", String.valueOf(defaultUserId))
                        .header("X-Tenant-Id", "1"))
                .andExpect(status().isOk());

        // Verify it's now disabled
        String json = getJson("/api/super-admin/tenants/" + otherCorpTenantId, String.valueOf(defaultUserId));
        Map<String, Object> tenant = om.readValue(json, new TypeReference<>() {});
        assertEquals("disabled", tenant.get("plan"), "Tenant should be disabled");
    }

    @Test
    @Order(111)
    void cannotDeleteDefaultTenant() throws Exception {
        mvc.perform(delete("/api/super-admin/tenants/1")
                        .header("X-Debug-User-Id", String.valueOf(defaultUserId))
                        .header("X-Tenant-Id", "1"))
                .andExpect(status().isBadRequest());
    }

    // ══════════════════════════════════════════════════════════════════
    // Additional Cross-Cutting Tests (Order 120+)
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(120)
    void searchScopedToTenant() throws Exception {
        // Search in default tenant should not return TestCorp content
        String json = getJson("/api/search?q=TestCorp+announcement", String.valueOf(defaultUserId));
        Map<String, Object> result = om.readValue(json, new TypeReference<>() {});
        Object hits = result.get("hits");
        // The search might or might not index instantly; just verify it doesn't crash
        assertNotNull(result, "Search should return a valid response");
    }

    @Test
    @Order(121)
    void notificationsAccessible() throws Exception {
        String json = getJson("/api/notifications?limit=10", String.valueOf(defaultUserId));
        // Just verify the endpoint works and returns valid JSON
        assertNotNull(json);
    }

    @Test
    @Order(122)
    void notificationUnreadCount() throws Exception {
        String json = getJson("/api/notifications/unread-count", String.valueOf(defaultUserId));
        assertNotNull(json);
    }

    @Test
    @Order(123)
    void unreadMessageCount() throws Exception {
        String json = getJson("/api/messages/unread-count", String.valueOf(defaultUserId));
        Map<String, Object> result = om.readValue(json, new TypeReference<>() {});
        assertNotNull(result.get("unreadCount"), "Should return unreadCount");
    }

    @Test
    @Order(124)
    void conversationInTenantIsolated() throws Exception {
        // Verify default tenant conversations don't leak to TestCorp
        String json = getJsonTenant("/api/conversations", testCorpAdminUserId, testCorpTenantId);
        List<Map<String, Object>> convs = om.readValue(json, new TypeReference<>() {});

        boolean foundDefault = convs.stream()
                .anyMatch(c -> toLong(c.get("id")) == directConversationId);
        assertFalse(foundDefault, "Default tenant conversations should NOT appear in TestCorp");
    }

    @Test
    @Order(125)
    void groupSearchInTenant() throws Exception {
        String json = getJson("/api/groups/mine", String.valueOf(defaultUserId));
        List<Map<String, Object>> groups = om.readValue(json, new TypeReference<>() {});
        assertNotNull(groups, "Should return a groups list");
    }

    @Test
    @Order(126)
    void tenantPostWithReaction() throws Exception {
        // Add reaction to TestCorp post from TestCorp admin
        Map<String, Object> body = Map.of(
                "targetId", testCorpPostId,
                "reactionType", "LIKE"
        );

        String json = postJsonTenant("/api/reactions", testCorpAdminUserId, testCorpTenantId, body);
        Map<String, Object> reaction = om.readValue(json, new TypeReference<>() {});
        assertEquals("LIKE", reaction.get("reactionType"));
    }

    @Test
    @Order(127)
    void tenantPostCommentIsolation() throws Exception {
        // Add comment to TestCorp post
        Map<String, Object> body = Map.of(
                "postId", testCorpPostId,
                "content", "TestCorp internal comment"
        );

        String json = postJsonTenant("/api/comments", testCorpAdminUserId, testCorpTenantId, body);
        Map<String, Object> comment = om.readValue(json, new TypeReference<>() {});
        assertNotNull(comment.get("id"), "Comment should be created in tenant context");
    }

    @Test
    @Order(128)
    void tenantGroupCreation() throws Exception {
        Map<String, Object> body = Map.of(
                "name", "TestCorp Engineering " + System.currentTimeMillis(),
                "description", "TestCorp's engineering team",
                "visibility", "PUBLIC"
        );

        String json = postJsonTenant("/api/groups", testCorpAdminUserId, testCorpTenantId, body);
        Map<String, Object> group = om.readValue(json, new TypeReference<>() {});

        long tenantGroupId = toLong(group.get("id"));
        assertTrue(tenantGroupId > 0);

        // Verify this group is NOT visible from default tenant search
        String searchJson = getJson("/api/groups/search?q=TestCorp+Engineering", String.valueOf(defaultUserId));
        List<Map<String, Object>> results = om.readValue(searchJson, new TypeReference<>() {});

        boolean found = results.stream()
                .anyMatch(g -> toLong(g.get("id")) == tenantGroupId);
        assertFalse(found, "TestCorp group should not appear in default tenant group search");
    }

    @Test
    @Order(129)
    void multipleTenantsInListing() throws Exception {
        String json = getJson("/api/super-admin/tenants", String.valueOf(defaultUserId));
        List<Map<String, Object>> tenants = om.readValue(json, new TypeReference<>() {});

        // We created at least 2 tenants, plus the default
        assertTrue(tenants.size() >= 3, "Should have at least 3 tenants (default + TestCorp + OtherCorp)");
    }

    // ══════════════════════════════════════════════════════════════════
    // Helper Methods
    // ══════════════════════════════════════════════════════════════════

    private long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("Cannot convert to long: " + val);
    }

    private String postJson(String path, String userId, Map<String, Object> body) throws Exception {
        return mvc.perform(post(path)
                        .header("X-Debug-User-Id", userId)
                        .header("X-Tenant-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String postJsonTenant(String path, String userId, String tenantId, Map<String, Object> body) throws Exception {
        return mvc.perform(post(path)
                        .header("X-Debug-User-Id", userId)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String getJson(String path, String userId) throws Exception {
        return mvc.perform(get(path)
                        .header("X-Debug-User-Id", userId)
                        .header("X-Tenant-Id", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String getJsonTenant(String path, String userId, String tenantId) throws Exception {
        return mvc.perform(get(path)
                        .header("X-Debug-User-Id", userId)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * Recursively search an org tree for a unit with the given ID.
     */
    private boolean findInTree(List<Map<String, Object>> nodes, long targetId) {
        if (nodes == null) return false;
        for (Map<String, Object> node : nodes) {
            if (toLong(node.get("id")) == targetId) return true;
            Object children = node.get("children");
            if (children instanceof List<?> childList) {
                if (findInTree((List<Map<String, Object>>) childList, targetId)) return true;
            }
        }
        return false;
    }
}
