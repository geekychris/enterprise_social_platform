package com.aisupport.qa;

import com.aisupport.config.OllamaConfig;
import com.aisupport.persistence.repository.DocumentChunkRepository;
import com.aisupport.persistence.repository.DocumentRepository;
import com.aisupport.persistence.repository.InteractionRepository;
import com.aisupport.search.UnifiedSearchService;
import com.aisupport.service.*;
import com.aisupport.persistence.entity.InteractionEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agentic QA service with a ReAct-style tool-calling loop.
 * The LLM can iteratively search, retrieve documents, and refine its answer.
 */
@Service
public class AgenticQAService {
    private static final Logger log = LoggerFactory.getLogger(AgenticQAService.class);
    private static final int MAX_ITERATIONS = 5;
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "\\[TOOL:(\\w+)\\(([^)]*)\\)\\]", Pattern.DOTALL);

    private final KnowledgeService knowledgeService;
    private final UnifiedSearchService unifiedSearch;
    private final OllamaService ollamaService;
    private final OllamaConfig ollamaConfig;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;
    private final InteractionRepository interactionRepository;
    private final QATraceService traceService;
    private final AnswerCacheService answerCacheService;
    private final QueryExpansionService queryExpansionService;
    private final GroundingService groundingService;
    private final KnowledgeGapService knowledgeGapService;
    private final ConversationMemoryService conversationMemoryService;

    public AgenticQAService(KnowledgeService knowledgeService, UnifiedSearchService unifiedSearch,
                            OllamaService ollamaService, OllamaConfig ollamaConfig,
                            DocumentChunkRepository chunkRepository,
                            DocumentRepository documentRepository,
                            InteractionRepository interactionRepository,
                            QATraceService traceService,
                            AnswerCacheService answerCacheService,
                            QueryExpansionService queryExpansionService,
                            GroundingService groundingService,
                            KnowledgeGapService knowledgeGapService,
                            ConversationMemoryService conversationMemoryService) {
        this.knowledgeService = knowledgeService;
        this.unifiedSearch = unifiedSearch;
        this.ollamaService = ollamaService;
        this.ollamaConfig = ollamaConfig;
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.interactionRepository = interactionRepository;
        this.traceService = traceService;
        this.answerCacheService = answerCacheService;
        this.queryExpansionService = queryExpansionService;
        this.groundingService = groundingService;
        this.knowledgeGapService = knowledgeGapService;
        this.conversationMemoryService = conversationMemoryService;
    }

    public record AgenticResult(
            String answer, double confidence, List<ToolStep> steps,
            List<Map<String, Object>> citations, boolean suggestHuman,
            Long interactionId, Long traceId
    ) {}

    public record ToolStep(
            int iteration, String thought, String toolName, String toolArgs,
            String toolResult, long durationMs
    ) {}

    @Transactional
    public AgenticResult answer(long knowledgeSetId, String question, Long socialPostId, Long socialUserId) {
        // Check answer cache first
        log.info("AGENTIC: checking cache for ks-{} question='{}'", knowledgeSetId, question.substring(0, Math.min(50, question.length())));
        var cached = answerCacheService.lookup(knowledgeSetId, question);
        if (cached != null) {
            log.info("AGENTIC CACHE HIT for ks-{}, returning cached answer (conf={})", knowledgeSetId, cached.confidence());
            return new AgenticResult(cached.answer(), cached.confidence(), List.of(), List.of(),
                    cached.confidence() < 0.6, null, null);
        }
        log.info("AGENTIC: no cache hit, proceeding with full pipeline");

        long totalTokens = knowledgeService.getTotalTokenCount(knowledgeSetId);
        var ks = knowledgeService.getKnowledgeSet(knowledgeSetId).orElse(null);
        String ksName = ks != null ? ks.getName() : "this topic";

        List<ToolStep> steps = new ArrayList<>();
        List<Map<String, Object>> citations = new ArrayList<>();
        List<Map<String, String>> messages = new ArrayList<>();

        // Build conversation context if this is part of a thread
        String conversationContext = "";
        if (socialPostId != null) {
            conversationContext = conversationMemoryService.buildConversationContext(socialPostId);
        }

        // Build system prompt with tool definitions
        String systemPrompt = buildAgenticSystemPrompt(ksName, knowledgeSetId, totalTokens);
        messages.add(Map.of("role", "system", "content", systemPrompt));
        String userMessage = conversationContext.isEmpty() ? question :
                conversationContext + "\nCurrent question: " + question;
        messages.add(Map.of("role", "user", "content", userMessage));

        String finalAnswer = null;
        long totalLlmMs = 0;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            long start = System.currentTimeMillis();
            String llmResponse;
            try {
                llmResponse = ollamaService.chatWithMessages(messages);
            } catch (Exception e) {
                log.error("LLM call failed at iteration {}: {}", i, e.getMessage());
                finalAnswer = "I'm having trouble processing your question. Let me connect you with a human.";
                break;
            }
            long duration = System.currentTimeMillis() - start;
            totalLlmMs += duration;

            log.debug("Iteration {}: LLM response ({}ms): {}", i, duration, llmResponse.substring(0, Math.min(200, llmResponse.length())));

            // Check for tool calls
            Matcher matcher = TOOL_CALL_PATTERN.matcher(llmResponse);
            if (matcher.find()) {
                String toolName = matcher.group(1);
                String toolArgs = matcher.group(2).trim();
                String thought = llmResponse.substring(0, matcher.start()).trim();

                // Execute tool
                long toolStart = System.currentTimeMillis();
                String toolResult = executeTool(knowledgeSetId, toolName, toolArgs, citations);
                long toolDuration = System.currentTimeMillis() - toolStart;

                steps.add(new ToolStep(i, thought, toolName, toolArgs, toolResult, toolDuration));

                // Feed result back into conversation
                messages.add(Map.of("role", "assistant", "content", llmResponse));
                messages.add(Map.of("role", "user", "content",
                        "[TOOL_RESULT]\n" + toolResult + "\n[/TOOL_RESULT]\n\nContinue answering the question based on this information. " +
                                "If you have enough information, provide your [FINAL_ANSWER]. If you need more, use another tool."));
            } else if (llmResponse.contains("[FINAL_ANSWER]")) {
                // Extract final answer
                int idx = llmResponse.indexOf("[FINAL_ANSWER]");
                String afterTag = llmResponse.substring(idx + "[FINAL_ANSWER]".length()).trim();
                // Remove closing tag if present
                afterTag = afterTag.replace("[/FINAL_ANSWER]", "").trim();
                finalAnswer = afterTag.isEmpty() ? llmResponse.substring(0, idx).trim() : afterTag;
                steps.add(new ToolStep(i, "Final answer generated", "none", "", "", duration));
                break;
            } else {
                // No tool call and no FINAL_ANSWER tag — treat the whole response as the answer
                finalAnswer = llmResponse;
                steps.add(new ToolStep(i, "Direct answer (no tool needed)", "none", "", "", duration));
                break;
            }
        }

        if (finalAnswer == null) {
            finalAnswer = "I wasn't able to fully answer your question after several attempts. Would you like a human to help?";
        }

        double confidence = estimateConfidence(finalAnswer, steps);

        // Run grounding verification against tool results
        String allToolContext = steps.stream()
                .filter(s -> !s.toolName().equals("none"))
                .map(ToolStep::toolResult)
                .reduce("", (a, b) -> a + "\n" + b);
        var grounding = groundingService.verify(finalAnswer, allToolContext);
        // Blend grounding score with heuristic confidence
        confidence = (confidence + grounding.score()) / 2.0;

        boolean suggestHuman = confidence < 0.6;

        // If confidence is very low, record a knowledge gap
        if (confidence < 0.5) {
            knowledgeGapService.recordGap(knowledgeSetId, question);
        }

        if (suggestHuman && !finalAnswer.contains("human")) {
            finalAnswer += "\n\n---\n*I'm not fully confident in this answer. Would you like a human to help?*";
        }

        // Store answer in cache
        answerCacheService.store(knowledgeSetId, question, finalAnswer, confidence, null);

        // Record interaction
        var interaction = new InteractionEntity();
        interaction.setKnowledgeSetId(knowledgeSetId);
        interaction.setQuestion(question);
        interaction.setAnswer(finalAnswer);
        interaction.setAnswerSource("AGENTIC");
        interaction.setConfidence(confidence);
        interaction.setSocialPostId(socialPostId);
        interaction.setSocialUserId(socialUserId);
        interaction.setEscalated(suggestHuman);
        interaction = interactionRepository.save(interaction);

        // Save trace
        var trace = traceService.builder();
        trace.knowledgeSetId = knowledgeSetId;
        trace.question = question;
        trace.method = "AGENTIC";
        trace.confidence = confidence;
        trace.totalKnowledgeTokens = totalTokens;
        trace.llmModel = ollamaConfig.getChatModel();
        trace.llmDurationMs = totalLlmMs;
        trace.systemPrompt = systemPrompt;
        trace.answer = finalAnswer;
        trace.suggestHuman = suggestHuman;
        trace.interactionId = interaction.getId();

        // Encode steps as context chunks for trace visibility
        trace.contextChunks = steps.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("iteration", s.iteration());
            m.put("thought", s.thought());
            m.put("tool", s.toolName());
            m.put("args", s.toolArgs());
            m.put("resultPreview", s.toolResult().length() > 300 ? s.toolResult().substring(0, 300) + "..." : s.toolResult());
            m.put("durationMs", s.durationMs());
            return m;
        }).toList();
        trace.contextTokenCount = steps.stream().mapToInt(s -> s.toolResult().length() / 4).sum();
        trace.citations = citations;

        // Save grounding score to trace
        Map<String, Object> groundingInfo = new LinkedHashMap<>();
        groundingInfo.put("groundingScore", grounding.score());
        groundingInfo.put("supportedClaims", grounding.supportedClaims().size());
        groundingInfo.put("unsupportedClaims", grounding.unsupportedClaims().size());
        trace.contextChunks = new ArrayList<>(trace.contextChunks);
        ((List<Map<String, Object>>) trace.contextChunks).add(groundingInfo);

        long traceId = traceService.save(trace);

        return new AgenticResult(finalAnswer, confidence, steps, citations, suggestHuman, interaction.getId(), traceId);
    }

    private String executeTool(long knowledgeSetId, String toolName, String args, List<Map<String, Object>> citations) {
        try {
            return switch (toolName) {
                case "search_lexical" -> {
                    var results = unifiedSearch.searchLexical(knowledgeSetId, args, 5);
                    StringBuilder sb = new StringBuilder("Lexical search results for '" + args + "':\n\n");
                    for (var r : results) {
                        sb.append("--- Chunk ").append(r.get("chunkId")).append(" (doc: ").append(r.get("documentId"))
                                .append(", score: ").append(String.format("%.3f", ((Number) r.get("score")).doubleValue()))
                                .append(") ---\n");
                        if (r.get("title") != null) sb.append("Title: ").append(r.get("title")).append("\n");
                        if (r.get("content") != null) sb.append(r.get("content")).append("\n\n");
                        addCitation(citations, r);
                    }
                    if (results.isEmpty()) sb.append("No results found.");
                    yield sb.toString();
                }
                case "search_semantic" -> {
                    var results = unifiedSearch.searchSemantic(knowledgeSetId, args, 5);
                    StringBuilder sb = new StringBuilder("Semantic search results for '" + args + "':\n\n");
                    for (var r : new ArrayList<>(results)) {
                        sb.append("--- Chunk ").append(r.get("chunkId"))
                                .append(" (doc: ").append(r.get("documentId"))
                                .append(", similarity: ").append(String.format("%.3f", ((Number) r.get("score")).doubleValue()))
                                .append(") ---\n");
                        if (r.get("content") != null) sb.append(r.get("content")).append("\n\n");
                        addCitation(citations, r);
                        // Also get neighbor chunks for expanded context
                        var neighbors = unifiedSearch.getNeighborChunks(knowledgeSetId,
                                ((Number) r.get("documentId")).longValue(),
                                ((Number) r.get("chunkId")).longValue(), 1);
                        for (var n : neighbors) {
                            sb.append("[Neighbor chunk ").append(n.get("chunkId")).append("] ");
                            if (n.get("content") != null) sb.append(n.get("content")).append("\n\n");
                        }
                    }
                    if (results.isEmpty()) sb.append("No results found.");
                    yield sb.toString();
                }
                case "search_expanded" -> {
                    var expanded = queryExpansionService.expand(args);
                    StringBuilder sb = new StringBuilder("Expanded search across " + expanded.size() + " queries:\n\n");
                    Set<Long> seenChunks = new HashSet<>();
                    for (String q : expanded) {
                        var results = unifiedSearch.searchHybrid(knowledgeSetId, q, 3);
                        for (var r : results) {
                            long cId = ((Number) r.get("chunkId")).longValue();
                            if (seenChunks.add(cId)) {
                                sb.append("--- Chunk ").append(cId)
                                        .append(" (doc: ").append(r.get("documentId"))
                                        .append(", score: ").append(String.format("%.3f", ((Number) r.get("score")).doubleValue()))
                                        .append(") ---\n");
                                if (r.get("title") != null) sb.append("Title: ").append(r.get("title")).append("\n");
                                if (r.get("content") != null) sb.append(r.get("content")).append("\n\n");
                                addCitation(citations, r);
                            }
                        }
                    }
                    if (seenChunks.isEmpty()) sb.append("No results found.");
                    yield sb.toString();
                }
                case "get_document" -> {
                    long docId;
                    try {
                        docId = Long.parseLong(args.trim());
                    } catch (NumberFormatException e) {
                        yield "Error: Invalid document ID '" + args + "'";
                    }
                    var doc = documentRepository.findById(Long.parseLong(args.trim())).orElse(null);
                    if (doc == null) yield "Document not found: " + args;
                    else {
                        String content = doc.getContent();
                        // Truncate if too large
                        if (content.length() > 4000) {
                            content = content.substring(0, 4000) + "\n\n[... truncated, " + content.length() + " total chars]";
                        }
                        yield "Document '" + doc.getTitle() + "' (ID: " + doc.getId() + "):\n\n" + content;
                    }
                }
                case "get_all_content" -> {
                    String allContent = knowledgeService.getAllContent(knowledgeSetId);
                    if (allContent.length() > 8000) {
                        allContent = allContent.substring(0, 8000) + "\n\n[... truncated. Total: " + allContent.length() + " chars. Use search tools for more targeted retrieval.]";
                    }
                    yield "All knowledge content:\n\n" + allContent;
                }
                case "list_documents" -> {
                    var docs = knowledgeService.getDocuments(knowledgeSetId);
                    StringBuilder sb = new StringBuilder("Available documents in this knowledge set:\n\n");
                    for (var doc : docs) {
                        sb.append("- ID: ").append(doc.getId()).append(" | ").append(doc.getTitle())
                                .append(" (").append(doc.getContent().length()).append(" chars, source: ")
                                .append(doc.getSourceType()).append(")\n");
                    }
                    yield sb.toString();
                }
                default -> "Unknown tool: " + toolName;
            };
        } catch (Exception e) {
            return "Error executing tool " + toolName + ": " + e.getMessage();
        }
    }

    private void addCitation(List<Map<String, Object>> citations, Map<String, Object> searchResult) {
        long docId = ((Number) searchResult.get("documentId")).longValue();
        // Avoid duplicate citations
        if (citations.stream().anyMatch(c -> ((Number) c.get("documentId")).longValue() == docId)) return;
        documentRepository.findById(docId).ifPresent(doc ->
                citations.add(Map.of(
                        "documentId", doc.getId(),
                        "title", doc.getTitle(),
                        "snippet", doc.getContent().substring(0, Math.min(200, doc.getContent().length()))
                ))
        );
    }

    private String buildAgenticSystemPrompt(String ksName, long knowledgeSetId, long totalTokens) {
        return """
                You are a support assistant for "%s". You MUST search the knowledge base before answering. Do NOT answer from your own knowledge.

                IMPORTANT: You MUST use a tool on your FIRST response. Start by searching for relevant information.

                To use a tool, write EXACTLY this format on its own line:
                [TOOL:tool_name(arguments)]

                Available tools:
                [TOOL:search_semantic(your search query)] — Find relevant content by meaning. USE THIS FIRST.
                [TOOL:search_lexical(keywords)] — Keyword search for specific terms.
                [TOOL:search_expanded(query)] — Expanded search using multiple query variations for broader coverage.
                [TOOL:get_document(document_id)] — Get full document by numeric ID.
                [TOOL:list_documents()] — List all documents and their IDs.
                %s

                When you have enough information from tool results, write your final answer like:
                [FINAL_ANSWER]
                Your complete answer here in markdown format.
                [/FINAL_ANSWER]

                Example interaction:
                User: How do I fix the display on my device?
                Assistant: I'll search for display troubleshooting information.
                [TOOL:search_semantic(display troubleshooting fix)]

                Then after receiving results:
                Assistant: Based on the search results, I found relevant information. Let me also check the full document.
                [TOOL:get_document(2)]

                Then with enough info:
                Assistant: [FINAL_ANSWER]
                ## Display Troubleshooting
                Based on document "Hardware Guide" (doc 2):
                1. Check the cable connections...
                [/FINAL_ANSWER]

                Rules:
                - ALWAYS search first. Never guess.
                - Only use information from tool results.
                - ALWAYS include a "Sources" section at the end of your answer listing the document titles you used.
                - Format: "**Sources:** Document Title 1, Document Title 2"
                - Reference specific document names in your answer text (e.g. "According to the Amiga Graphics Programming guide...").
                - Knowledge base: %s (%d tokens)
                """.formatted(
                ksName,
                totalTokens <= 6000 ?
                        "[TOOL:get_all_content()] — Get ALL content (knowledge base is small enough)." : "",
                ksName,
                totalTokens
        );
    }

    private double estimateConfidence(String answer, List<ToolStep> steps) {
        if (answer.contains("I wasn't able") || answer.contains("couldn't find") ||
                answer.contains("don't have") || answer.contains("no information")) {
            return 0.2;
        }
        // More tool calls with results = more grounded = higher confidence
        long toolCallsWithResults = steps.stream()
                .filter(s -> !s.toolName().equals("none") && !s.toolResult().contains("No results found"))
                .count();
        if (toolCallsWithResults == 0) return 0.4;
        if (toolCallsWithResults == 1) return 0.7;
        return Math.min(0.5 + toolCallsWithResults * 0.15, 0.95);
    }
}
