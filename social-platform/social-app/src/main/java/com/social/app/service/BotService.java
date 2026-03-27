package com.social.app.service;

import com.social.app.persistence.entity.ConversationParticipantEntity;
import com.social.app.persistence.entity.MessageEntity;
import com.social.app.persistence.repository.ConversationParticipantRepository;
import com.social.app.persistence.repository.ConversationRepository;
import com.social.app.persistence.repository.UserRepository;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BotService {

    private static final Logger log = LoggerFactory.getLogger(BotService.class);
    private static final Pattern MENTION_PATTERN = Pattern.compile("@\\[([^\\]]+)]\\((\\d+)\\)");
    private static final Pattern GROUP_ID_PATTERN = Pattern.compile("(?:group|Group)\\s+(?:id:?\\s*)?(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAGE_ID_PATTERN = Pattern.compile("(?:page|Page)\\s+(?:id:?\\s*)?(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern USER_MENTION_PATTERN = Pattern.compile("(?:what|about|said|from|by)\\s+@\\[([^\\]]+)]\\((\\d+)\\)");

    @Value("${social.bot.user-id}")
    private long botUserId;

    @Value("${social.bot.name}")
    private String botName;

    @Value("${social.bot.max-history-messages}")
    private int maxHistoryMessages;

    private final OllamaService ollamaService;
    private final BotToolService toolService;
    private final BotActionService actionService;
    private final BotMemoryService memoryService;
    private final MessageService messageService;
    private final ConversationService conversationService;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final GlobalIdGenerator idGenerator;

    public BotService(OllamaService ollamaService, BotToolService toolService,
                      BotActionService actionService, BotMemoryService memoryService,
                      MessageService messageService, ConversationService conversationService,
                      ConversationRepository conversationRepository,
                      ConversationParticipantRepository participantRepository,
                      UserRepository userRepository, GlobalIdGenerator idGenerator) {
        this.ollamaService = ollamaService;
        this.toolService = toolService;
        this.actionService = actionService;
        this.memoryService = memoryService;
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.idGenerator = idGenerator;
    }

    public long getBotUserId() {
        return botUserId;
    }

    /**
     * Check if a message should trigger the bot and respond if so.
     * Called after a message is saved.
     */
    public void handleMessage(long conversationId, long senderId, String content) {
        if (senderId == botUserId) return; // Don't respond to self

        boolean shouldRespond = false;

        // Check 1: Is this a DM conversation with the bot?
        var participants = participantRepository.findByConversationId(conversationId);
        boolean isBotConversation = participants.stream().anyMatch(p -> p.getUserId() == botUserId);

        if (isBotConversation) {
            var conv = conversationRepository.findById(conversationId).orElse(null);
            // Respond in DM with bot, or if mentioned in group
            if (conv != null && "DIRECT".equals(conv.getType())) {
                shouldRespond = true;
            } else if (content != null && isBotMentioned(content)) {
                shouldRespond = true;
            }
        }

        // Check 2: Bot is mentioned but not in the conversation yet — add it
        if (!isBotConversation && content != null && isBotMentioned(content)) {
            // Add bot to the conversation
            ensureBotInConversation(conversationId);
            shouldRespond = true;
        }

        if (shouldRespond) {
            // Run async to not block the message send
            Thread.ofVirtual().start(() -> {
                try {
                    generateAndSendResponse(conversationId, senderId, content);
                } catch (Exception e) {
                    log.error("Bot failed to respond in conversation {}", conversationId, e);
                }
            });
        }
    }

    private boolean isBotMentioned(String content) {
        // Check for @[roid](botId) or plain @roid
        if (content.contains("@[" + botName + "]") || content.contains("@" + botName)) {
            return true;
        }
        // Also check mention pattern for bot user ID
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            try {
                if (Long.parseLong(matcher.group(2)) == botUserId) return true;
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }

    @Transactional
    protected void ensureBotInConversation(long conversationId) {
        var existing = participantRepository.findByConversationIdAndUserId(conversationId, botUserId);
        if (existing.isEmpty()) {
            var participant = new ConversationParticipantEntity();
            participant.setConversationId(conversationId);
            participant.setUserId(botUserId);
            participantRepository.save(participant);
        }
    }

    private void generateAndSendResponse(long conversationId, long senderId, String userMessage) {
        // Gather context
        String systemPrompt = buildSystemPrompt();
        String context = gatherContext(conversationId, senderId, userMessage);
        String fullUserMessage = context + "\n\nUser's message: " + userMessage;

        log.info("Bot context gathered ({} chars) for conversation {}", context.length(), conversationId);

        // Call Ollama
        StringBuilder responseBuilder = new StringBuilder();
        try {
            ollamaService.chatBlocking(systemPrompt, fullUserMessage, responseBuilder);
        } catch (Exception e) {
            log.error("Ollama call failed", e);
            responseBuilder.append("Sorry, I'm having trouble thinking right now. Please try again.");
        }

        String response = responseBuilder.toString().trim();
        if (response.isEmpty()) return;

        // Parse and execute actions
        response = parseAndExecuteActions(response, senderId, conversationId);

        // Parse and save memories
        response = parseAndSaveMemories(response, senderId);

        // Save bot's response as a message
        response = response.trim();
        if (!response.isEmpty()) {
            messageService.send(botUserId, conversationId, response, null);
        }
    }

    private String parseAndExecuteActions(String response, long senderId, long conversationId) {
        // Match action tags — use a more forgiving pattern that handles content with special chars
        Pattern actionPattern = Pattern.compile("\\[ACTION:(\\w+)\\|([^\\]]*)]");
        Matcher m = actionPattern.matcher(response);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (m.find()) {
            String textBefore = response.substring(lastEnd, m.start()).trim();
            result.append(response, lastEnd, m.start());
            String action = m.group(1);
            String params = m.group(2);
            var paramMap = parseParams(params);

            try {
                switch (action) {
                    case "CREATE_POST" -> {
                        String content = paramMap.getOrDefault("content", "");
                        // If content is empty/short/placeholder, use the text before the action tag
                        if (content.length() < 10 || content.equals("...") || content.startsWith("...")) {
                            content = extractPostContent(textBefore);
                        }
                        String targetType = paramMap.get("targetType");
                        if (targetType == null) targetType = "GROUP_FEED";
                        Long targetId = resolveTargetId(paramMap.get("targetId"), targetType, senderId);
                        if (targetId != null && !content.isBlank()) {
                            actionService.createPost(senderId, content, targetType, targetId);
                            log.info("Bot executed CREATE_POST ({} chars) for user {} in {}", content.length(), senderId, targetId);
                        } else {
                            log.warn("CREATE_POST skipped: targetId={}, content length={}", targetId, content.length());
                        }
                    }
                    case "CREATE_POLL" -> {
                        String question = paramMap.getOrDefault("question", "");
                        List<String> options = List.of(paramMap.getOrDefault("options", "").split(","));
                        String targetType = paramMap.get("targetType");
                        if (targetType == null) targetType = "GROUP_FEED";
                        Long targetId = resolveTargetId(paramMap.get("targetId"), targetType, senderId);
                        if (targetId != null) {
                            actionService.createPollPost(senderId, question, options, targetType, targetId);
                            log.info("Bot executed CREATE_POLL for user {} in {}", senderId, targetId);
                        }
                    }
                    case "SEND_MESSAGE" -> {
                        Long recipientId = resolveUserId(paramMap.getOrDefault("recipientId", "0"));
                        String content = paramMap.getOrDefault("content", "");
                        if (content.length() < 5) content = extractPostContent(textBefore);
                        if (recipientId != null) {
                            actionService.sendMessage(senderId, recipientId, content);
                            log.info("Bot executed SEND_MESSAGE to {} for user {}", recipientId, senderId);
                        } else {
                            log.warn("SEND_MESSAGE: could not resolve recipient '{}'", paramMap.get("recipientId"));
                            result.append("\n(Could not find that user to message)\n");
                        }
                    }
                    case "JOIN_GROUP" -> {
                        String groupRef = paramMap.getOrDefault("groupId", "0");
                        Long groupId = resolveTargetId(groupRef, "GROUP_FEED", senderId);
                        if (groupId != null) {
                            actionService.joinGroup(senderId, groupId);
                            log.info("Bot executed JOIN_GROUP for user {}", senderId);
                        }
                    }
                    default -> log.warn("Unknown bot action: {}", action);
                }
            } catch (Exception e) {
                log.error("Bot action {} failed: {}", action, e.getMessage());
                result.append("\n(Action failed: ").append(e.getMessage()).append(")\n");
            }
            lastEnd = m.end();
        }
        result.append(response.substring(lastEnd));
        return result.toString();
    }

    /**
     * Extract the main content from the bot's response text to use as a post body.
     * Strips leading/trailing boilerplate like "Here's the summary:" or "I'll post this for you".
     */
    private String extractPostContent(String text) {
        if (text == null || text.isBlank()) return "";
        // Remove common bot preamble/postamble
        String cleaned = text
                .replaceAll("(?i)^(here'?s?|i'?ll post|let me post|posting|sure|okay)[^\\n]*\\n?", "")
                .replaceAll("(?i)\\n?(i'?ll post|let me|posted|done)[^\\n]*$", "")
                .trim();
        return cleaned.isEmpty() ? text.trim() : cleaned;
    }

    private String parseAndSaveMemories(String response, long userId) {
        Pattern memPattern = Pattern.compile("\\[REMEMBER\\|([^\\]]+)]");
        Matcher m = memPattern.matcher(response);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (m.find()) {
            result.append(response, lastEnd, m.start());
            var params = parseParams(m.group(1));
            String key = params.get("key");
            String value = params.get("value");
            if (key != null && value != null) {
                memoryService.remember(userId, key, value);
                log.info("Bot remembered '{}' for user {}", key, userId);
            }
            lastEnd = m.end();
        }
        result.append(response.substring(lastEnd));
        return result.toString();
    }

    /**
     * Resolve a user reference that may be a numeric ID, a username, or a display name.
     */
    private Long resolveUserId(String raw) {
        if (raw == null || raw.isBlank()) return null;

        // Try numeric
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {}

        // Strip common LLM noise like (@username), URL encoding, extra text
        String cleaned = raw.trim();
        try { cleaned = java.net.URLDecoder.decode(cleaned, "UTF-8"); } catch (Exception ignored) {}
        cleaned = cleaned
                .replaceAll("\\(.*\\)", "")  // remove (parenthetical)
                .replaceAll("@", "")
                .replaceAll("%20", " ")
                .trim();

        // Try by username first
        var byUsername = userRepository.findByUsername(cleaned);
        if (byUsername.isPresent()) return byUsername.get().getId();

        // Try name search
        Long id = toolService.findUserByName(cleaned);
        if (id != null) return id;

        // Try individual words for multi-word names
        if (cleaned.contains(" ")) {
            id = toolService.findUserByName(cleaned.split("\\s+")[0]);
            if (id != null) return id;
        }

        return null;
    }

    /**
     * Resolve a targetId that may be a numeric ID or a group/page name.
     */
    private Long resolveTargetId(String raw, String targetType, long userId) {
        if (raw == null || raw.isBlank()) return null;

        // Try parsing as number first
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {}

        // It's a name — resolve it
        String name = raw.trim();
        if ("GROUP_FEED".equals(targetType)) {
            Long id = toolService.findGroupByName(name, userId);
            if (id == null) id = toolService.findGroupByNameInMessage(name.toLowerCase(), userId);
            if (id != null) return id;
        }
        if ("PAGE_FEED".equals(targetType)) {
            Long id = toolService.findPageByName(name);
            if (id != null) return id;
        }

        // Last resort: try as group name regardless of targetType
        Long id = toolService.findGroupByName(name, userId);
        if (id == null) id = toolService.findGroupByNameInMessage(name.toLowerCase(), userId);
        return id;
    }

    private java.util.Map<String, String> parseParams(String params) {
        var map = new java.util.LinkedHashMap<String, String>();
        for (String pair : params.split("\\|")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = pair.substring(0, eq).trim();
                String value = pair.substring(eq + 1).trim();
                // Decode URL-encoded values the LLM sometimes produces
                try { value = java.net.URLDecoder.decode(value, "UTF-8"); } catch (Exception ignored) {}
                map.put(key, value);
            }
        }
        return map;
    }

    private String buildSystemPrompt() {
        return "You are " + botName + ", a helpful AI assistant integrated into the WorkSphere enterprise social platform. " +
                "You have REAL access to the user's groups, pages, conversations, feed, profiles, and search. " +
                "The context provided below contains ACTUAL data from the platform — use it to answer questions directly. " +
                "Do NOT say you don't have access or suggest the user check elsewhere. The data in the context IS the real data. " +
                "IMPORTANT: When asked to summarize, provide a thoughtful HIGH-LEVEL summary — identify themes, " +
                "key decisions, important announcements, and notable activity. Do NOT just list every post verbatim. " +
                "Group related items together, highlight what matters most, and provide insight. " +
                "Be concise, friendly, and helpful. Cite authors when relevant. " +
                "Format your responses with markdown when appropriate.\n\n" +
                "ACTION CAPABILITIES: You can perform actions for the user. Include these EXACT tags in your response:\n" +
                "  [ACTION:CREATE_POST|content=...|targetType=GROUP_FEED|targetId=<group name or ID>] - Post to a group\n" +
                "  [ACTION:CREATE_POLL|question=...|options=Option A,Option B,Option C|targetType=GROUP_FEED|targetId=<group name or ID>] - Create a poll\n" +
                "  [ACTION:SEND_MESSAGE|recipientId=...|content=...] - Send a DM to someone\n" +
                "  [ACTION:JOIN_GROUP|groupId=...] - Join a group\n" +
                "  [REMEMBER|key=...|value=...] - Remember something for the user\n" +
                "For targetId you can use the group/page name (e.g. targetId=remote workers) and it will be resolved.\n" +
                "For recipientId you can use the person's name (e.g. recipientId=Jeramy Osinski) and it will be resolved.\n" +
                "IMPORTANT: When the user asks you to send a message, create a post, or take any action, DO IT IMMEDIATELY. " +
                "Include the action tag in your response. Do NOT ask for confirmation — just execute and confirm what you did.\n\n" +
                "MEMORY: If the user asks you to remember something, use [REMEMBER|key=...|value=...]. " +
                "Their memories are provided in the context below.";
    }

    private String gatherContext(long conversationId, long senderId, String userMessage) {
        List<String> contextParts = new ArrayList<>();
        String msg = userMessage != null ? userMessage.toLowerCase() : "";
        String original = userMessage != null ? userMessage : "";

        // Include user memories
        var memories = memoryService.recallAll(senderId);
        if (!memories.isEmpty()) {
            StringBuilder memSb = new StringBuilder("=== User's saved memories ===\n");
            memories.forEach((k, v) -> memSb.append(k).append(": ").append(v).append("\n"));
            contextParts.add(memSb.toString());
        }

        // Include recent conversation (both user and bot) for continuity
        // so the bot knows who "them" or "that" refers to from its last answer
        String history = toolService.getConversationHistory(conversationId, 6);
        if (!history.isBlank()) {
            contextParts.add("=== Recent conversation ===\n" + history);
        }

        // --- GROUP detection ---
        if (msg.contains("group") || msg.contains("team")) {
            String groupName = extractEntityName(msg, "group");
            if (groupName == null) groupName = extractEntityName(msg, "team");

            Long groupId = null;

            // Try explicit ID first
            Matcher gm = GROUP_ID_PATTERN.matcher(original);
            if (gm.find()) {
                try { groupId = Long.parseLong(gm.group(1)); } catch (NumberFormatException ignored) {}
            }

            // Try name lookup
            if (groupId == null && groupName != null) {
                try {
                    groupId = toolService.findGroupByName(groupName, senderId);
                    log.info("Group name '{}' resolved to ID: {}", groupName, groupId);
                } catch (Exception e) {
                    log.warn("Group name lookup failed for '{}': {}", groupName, e.getMessage());
                }
            }

            // If still not found, scan all user's groups for a fuzzy match on the full message
            if (groupId == null) {
                groupId = toolService.findGroupByNameInMessage(msg, senderId);
                if (groupId != null) log.info("Fuzzy group match found ID: {}", groupId);
            }

            if (groupId != null) {
                contextParts.add("=== Group posts ===\n" + toolService.getGroupPosts(groupId, senderId));
            } else {
                contextParts.add("=== User's groups ===\n" + toolService.getUserGroups(senderId));
            }
        }

        // --- PAGE detection ---
        if (msg.contains("page")) {
            String pageName = extractEntityName(msg, "page");
            Long pageId = null;
            if (pageName != null) {
                pageId = toolService.findPageByName(pageName);
            }
            if (pageId == null) {
                Matcher pm = PAGE_ID_PATTERN.matcher(original);
                if (pm.find()) pageId = Long.parseLong(pm.group(1));
            }
            if (pageId != null) {
                contextParts.add("=== Page posts ===\n" + toolService.getPagePosts(pageId, senderId));
            }
        }

        // --- FEED ---
        if (msg.contains("feed") || msg.contains("what's new") || msg.contains("what's happening")
                || msg.contains("catch me up") || msg.contains("what did i miss")) {
            contextParts.add("=== User's feed ===\n" + toolService.getUserFeed(senderId));
        }

        // --- SEARCH ---
        if (msg.contains("search") || msg.contains("find") || msg.contains("look for") || msg.contains("look up")) {
            String query = extractSearchQuery(msg);
            if (query != null) {
                contextParts.add("=== Search results ===\n" + toolService.search(query, senderId));
            }
        }

        // --- USER / PROFILE detection ---
        boolean foundProfile = false;

        // From @[Name](id) mentions
        Matcher um = MENTION_PATTERN.matcher(original);
        while (um.find()) {
            try {
                long targetId = Long.parseLong(um.group(2));
                if (targetId != botUserId) {
                    contextParts.add("=== Profile: " + um.group(1) + " ===\n" + toolService.getUserProfile(targetId));
                    foundProfile = true;
                }
            } catch (NumberFormatException ignored) {}
        }

        // From natural language name detection
        boolean profileNeeded = msg.contains("who is") || msg.contains("about") || msg.contains("tell me")
                || msg.contains("profile") || msg.contains("summarize");
        if (profileNeeded) {
            String personName = extractPersonName(original);
            if (personName != null) {
                Long userId = toolService.findUserByName(personName);
                if (userId != null) {
                    contextParts.add("=== Profile ===\n" + toolService.getUserProfile(userId));
                    foundProfile = true;
                    log.info("Profile loaded for '{}' -> user {}", personName, userId);
                }
            }
        }

        // --- WHO / WHAT questions: if we couldn't find a specific person or group,
        // do a broad search to give the LLM something to work with ---
        if (!foundProfile && (msg.contains("who") || msg.contains("which") || msg.contains("where"))) {
            // Extract the key topic from the question for search
            String topic = msg.replaceAll("(?i)\\b(who|which|what|where|is|are|the|on|in|of|a|an|can|you|tell|me|do|does|please|roid|@roid)\\b", " ")
                    .replaceAll("\\s+", " ").replaceAll("[?!.]", "").trim();
            if (topic.length() > 3) {
                String searchResults = toolService.search(topic, senderId);
                String userResults = toolService.searchUsers(topic);
                contextParts.add("=== Search results ===\n" + searchResults);
                contextParts.add("=== User search ===\n" + userResults);
                log.info("Broad search for '{}': posts={} chars, users={} chars", topic, searchResults.length(), userResults.length());
            }
        }

        // --- Summarize conversation ---
        // Already handled by always including history above

        // Truncate total context to fit in model's window
        StringBuilder combined = new StringBuilder();
        for (String part : contextParts) {
            if (combined.length() + part.length() > 8000) {
                combined.append("\n(Additional context truncated for length)\n");
                break;
            }
            combined.append(part).append("\n\n");
        }

        return combined.toString();
    }

    /**
     * Extract an entity name near a keyword. E.g., from "what's going on in the agnus group"
     * extracts "agnus". Handles patterns like "X group", "group X", "the X group", etc.
     */
    private String extractEntityName(String msg, String keyword) {
        // Pattern: grab 1-3 words before "group/page/team"
        Pattern before = Pattern.compile("(\\w+(?:\\s+\\w+){0,2})\\s+" + keyword, Pattern.CASE_INSENSITIVE);
        Matcher m = before.matcher(msg);
        if (m.find()) {
            String raw = m.group(1).trim();
            // Strip leading articles/prepositions
            raw = raw.replaceAll("(?i)^(the|a|an|in|from|about|on|our|my)\\s+", "").trim();
            if (!raw.isEmpty() && !raw.matches("(?i)(this|that|what|which|every|any)")) {
                return raw;
            }
        }
        // Pattern: "<keyword> <name>" or "<keyword> called <name>"
        Pattern after = Pattern.compile(keyword + "\\s+(?:called |named )?([a-zA-Z][a-zA-Z0-9_ -]{1,30})", Pattern.CASE_INSENSITIVE);
        m = after.matcher(msg);
        if (m.find()) {
            String name = m.group(1).trim();
            if (!name.matches("(?i)(this|that|my|our|the|a|an|posts?|members?|activity)")) {
                return name;
            }
        }
        return null;
    }

    /**
     * Extract a person's name from messages like "who is Bob", "tell me about Alice".
     */
    private String extractPersonName(String msg) {
        // Try multiple patterns
        String[] patterns = {
            "(?:who is|about|tell me about|summarize|describe)\\s+([A-Z][a-zA-Z]+(?:\\s+[A-Z][a-zA-Z]+){0,2})",
            "([A-Z][a-zA-Z]+(?:\\s+[A-Z][a-zA-Z]+){1,2})(?:'s)?\\s+profile",
            "profile\\s+(?:of|for)\\s+([A-Z][a-zA-Z]+(?:\\s+[A-Z][a-zA-Z]+){0,2})",
        };
        for (String pat : patterns) {
            Matcher m = Pattern.compile(pat).matcher(msg);
            if (m.find()) {
                String name = m.group(1).trim();
                // Filter out generic/bot words
                if (!name.matches("(?i)(this|that|the|group|page|team|roid|Roid|my|our|your|here|what|can|you|it|write)")) {
                    return name;
                }
            }
        }
        // Last resort: look for any "First Last" capitalized pair not at start of sentence
        Matcher nameMatcher = Pattern.compile("(?<=\\s)([A-Z][a-z]{2,}\\s+[A-Z][a-z]{2,})").matcher(msg);
        if (nameMatcher.find()) {
            String name = nameMatcher.group(1).trim();
            if (!name.matches("(?i)(Can You|Write It|Post This)")) {
                return name;
            }
        }
        return null;
    }

    private String extractSearchQuery(String msg) {
        String[] patterns = {"search for ", "search ", "find ", "look for ", "look up "};
        for (String p : patterns) {
            int idx = msg.indexOf(p);
            if (idx >= 0) {
                String rest = msg.substring(idx + p.length()).trim();
                // Take up to end of sentence
                int end = rest.indexOf('.');
                if (end < 0) end = rest.indexOf('?');
                if (end < 0) end = Math.min(rest.length(), 100);
                return rest.substring(0, end).trim();
            }
        }
        return null;
    }
}
