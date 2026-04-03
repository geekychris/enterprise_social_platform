package com.aisupport.qa;

import com.aisupport.config.OllamaConfig;
import com.aisupport.persistence.entity.DocumentChunkEntity;
import com.aisupport.persistence.entity.InteractionEntity;
import com.aisupport.persistence.repository.DocumentChunkRepository;
import com.aisupport.persistence.repository.DocumentRepository;
import com.aisupport.persistence.repository.InteractionRepository;
import com.aisupport.search.LuceneSearchService;
import com.aisupport.search.VectorSearchService;
import com.aisupport.service.KnowledgeService;
import com.aisupport.service.OllamaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class QAService {
    private static final Logger log = LoggerFactory.getLogger(QAService.class);

    private final KnowledgeService knowledgeService;
    private final LuceneSearchService luceneSearch;
    private final VectorSearchService vectorSearch;
    private final OllamaService ollamaService;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;
    private final InteractionRepository interactionRepository;
    private final QATraceService traceService;
    private final OllamaConfig ollamaConfig;

    @Value("${aisupport.qa.max-context-tokens:4000}")
    private int maxContextTokens;
    @Value("${aisupport.qa.direct-context-threshold:3000}")
    private int directContextThreshold;
    @Value("${aisupport.qa.top-k-results:5}")
    private int topK;
    @Value("${aisupport.qa.confidence-threshold:0.6}")
    private double confidenceThreshold;

    public QAService(KnowledgeService knowledgeService, LuceneSearchService luceneSearch,
                     VectorSearchService vectorSearch, OllamaService ollamaService,
                     DocumentChunkRepository chunkRepository, DocumentRepository documentRepository,
                     InteractionRepository interactionRepository, QATraceService traceService,
                     OllamaConfig ollamaConfig) {
        this.knowledgeService = knowledgeService;
        this.luceneSearch = luceneSearch;
        this.vectorSearch = vectorSearch;
        this.ollamaService = ollamaService;
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.interactionRepository = interactionRepository;
        this.traceService = traceService;
        this.ollamaConfig = ollamaConfig;
    }

    public record QAResult(
        String answer,
        double confidence,
        String method, // "DIRECT_CONTEXT" or "RAG"
        List<Citation> citations,
        boolean suggestHuman,
        Long interactionId,
        Long traceId
    ) {}

    public record Citation(long documentId, String title, String snippet) {}

    /**
     * Answer a question using the knowledge set.
     */
    @Transactional
    public QAResult answer(long knowledgeSetId, String question, Long socialPostId, Long socialUserId) {
        var trace = traceService.builder();
        trace.knowledgeSetId = knowledgeSetId;
        trace.question = question;

        long totalTokens = knowledgeService.getTotalTokenCount(knowledgeSetId);
        trace.totalKnowledgeTokens = totalTokens;
        String method;
        String context;
        List<Citation> citations = new ArrayList<>();

        if (totalTokens == 0) {
            // No knowledge — can't answer
            trace.method = "NONE";
            trace.confidence = 0.0;
            trace.answer = "I don't have any knowledge to answer this question. Would you like to ask a human support agent?";
            trace.suggestHuman = true;
            var result = recordInteraction(knowledgeSetId, question, socialPostId, socialUserId,
                    trace.answer, 0.0, "NONE", List.of(), true);
            trace.interactionId = result.interactionId();
            long traceId = traceService.save(trace);
            return new QAResult(result.answer(), result.confidence(), result.method(),
                    result.citations(), result.suggestHuman(), result.interactionId(), traceId);
        }

        if (totalTokens <= directContextThreshold) {
            // Small knowledge set — inject all content directly
            method = "DIRECT_CONTEXT";
            context = knowledgeService.getAllContent(knowledgeSetId);
            // All docs are citations
            var docs = knowledgeService.getDocuments(knowledgeSetId);
            for (var doc : docs) {
                citations.add(new Citation(doc.getId(), doc.getTitle(),
                        doc.getContent().substring(0, Math.min(200, doc.getContent().length()))));
            }
        } else {
            // Large knowledge set — use RAG
            method = "RAG";
            context = buildRAGContext(knowledgeSetId, question, citations, trace);
        }

        trace.method = method;

        if (context.isBlank()) {
            trace.confidence = 0.1;
            trace.answer = "I couldn't find relevant information to answer your question. Would you like to speak with a human?";
            trace.suggestHuman = true;
            var result = recordInteraction(knowledgeSetId, question, socialPostId, socialUserId,
                    trace.answer, 0.1, method, List.of(), true);
            trace.interactionId = result.interactionId();
            long traceId = traceService.save(trace);
            return new QAResult(result.answer(), result.confidence(), result.method(),
                    result.citations(), result.suggestHuman(), result.interactionId(), traceId);
        }

        // Build prompt and call LLM
        String systemPrompt = buildSystemPrompt(knowledgeSetId);
        String userPrompt = buildUserPrompt(context, question);
        trace.systemPrompt = systemPrompt;
        trace.userPrompt = userPrompt;
        trace.llmModel = ollamaConfig.getChatModel();

        try {
            long llmStart = System.currentTimeMillis();
            String answer = ollamaService.chat(systemPrompt, userPrompt);
            trace.llmDurationMs = System.currentTimeMillis() - llmStart;
            trace.llmResponse = answer;

            double confidence = estimateConfidence(answer, context, question);
            boolean suggestHuman = confidence < confidenceThreshold;

            if (suggestHuman) {
                answer += "\n\n---\n*I'm not fully confident in this answer. Would you like a human to help? Reply \"yes\" to create a support ticket.*";
            }

            trace.confidence = confidence;
            trace.answer = answer;
            trace.suggestHuman = suggestHuman;
            trace.citations = citations.stream().map(c -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("documentId", c.documentId());
                m.put("title", c.title());
                m.put("snippet", c.snippet());
                return m;
            }).toList();

            var result = recordInteraction(knowledgeSetId, question, socialPostId, socialUserId,
                    answer, confidence, method, citations, suggestHuman);
            trace.interactionId = result.interactionId();
            long traceId = traceService.save(trace);
            return new QAResult(result.answer(), result.confidence(), result.method(),
                    result.citations(), result.suggestHuman(), result.interactionId(), traceId);
        } catch (Exception e) {
            log.error("LLM call failed: {}", e.getMessage());
            trace.method = "ERROR";
            trace.confidence = 0.0;
            trace.answer = "I'm having trouble generating an answer right now. Let me connect you with a human.";
            trace.suggestHuman = true;
            var result = recordInteraction(knowledgeSetId, question, socialPostId, socialUserId,
                    trace.answer, 0.0, "ERROR", List.of(), true);
            trace.interactionId = result.interactionId();
            long traceId = traceService.save(trace);
            return new QAResult(result.answer(), result.confidence(), result.method(),
                    result.citations(), result.suggestHuman(), result.interactionId(), traceId);
        }
    }

    /**
     * Search across all knowledge sets to find the best one for a question.
     * Returns ranked list of (knowledgeSetId, score, knowledgeSetName).
     */
    public List<Map<String, Object>> routeQuestion(String question) {
        var results = vectorSearch.searchAll(question, 10);
        // Group by knowledge set and take best score
        Map<Long, Double> bestScores = new LinkedHashMap<>();
        for (var r : results) {
            long ksId = (long) r.get("knowledgeSetId");
            double score = (double) r.get("score");
            bestScores.merge(ksId, score, Math::max);
        }

        return bestScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("knowledgeSetId", e.getKey());
                    m.put("score", e.getValue());
                    knowledgeService.getKnowledgeSet(e.getKey()).ifPresent(ks -> {
                        m.put("name", ks.getName());
                        m.put("slug", ks.getSlug());
                        m.put("socialPageId", ks.getSocialPageId());
                    });
                    return m;
                })
                .toList();
    }

    // ── Private helpers ──────────────────────────────────────────

    private String buildRAGContext(long knowledgeSetId, String question, List<Citation> citations,
                                   QATraceService.TraceBuilder trace) {
        // Hybrid search: combine lexical and semantic results
        trace.lexicalQuery = question;
        var lexicalResults = luceneSearch.search(knowledgeSetId, question, topK);
        trace.lexicalResults = lexicalResults.stream().map(LinkedHashMap::new).collect(Collectors.toList());

        trace.semanticQuery = question;
        var semanticResults = vectorSearch.search(knowledgeSetId, question, topK);
        trace.semanticResults = semanticResults.stream().map(LinkedHashMap::new).collect(Collectors.toList());

        // Merge and deduplicate by chunkId
        Map<Long, Map<String, Object>> merged = new LinkedHashMap<>();
        for (var r : semanticResults) {
            long chunkId = (long) r.get("chunkId");
            r.put("combinedScore", (double) r.get("score") * 2); // Boost semantic
            merged.put(chunkId, r);
        }
        for (var r : lexicalResults) {
            long chunkId = (long) r.get("chunkId");
            if (merged.containsKey(chunkId)) {
                var existing = merged.get(chunkId);
                existing.put("combinedScore",
                        (double) existing.get("combinedScore") + ((Number) r.get("score")).doubleValue());
            } else {
                r.put("combinedScore", ((Number) r.get("score")).doubleValue());
                merged.put(chunkId, r);
            }
        }

        // Sort by combined score and take top-K
        var sorted = merged.values().stream()
                .sorted((a, b) -> Double.compare(
                        ((Number) b.get("combinedScore")).doubleValue(),
                        ((Number) a.get("combinedScore")).doubleValue()))
                .limit(topK)
                .toList();

        trace.mergedResults = sorted.stream().map(LinkedHashMap::new).collect(Collectors.toList());

        // Build context string from chunks
        StringBuilder context = new StringBuilder();
        Set<Long> seenDocs = new HashSet<>();
        int tokenCount = 0;
        List<Map<String, Object>> contextChunksList = new ArrayList<>();
        for (var result : sorted) {
            long chunkId = (long) result.get("chunkId");
            var chunk = chunkRepository.findById(chunkId).orElse(null);
            if (chunk == null) continue;

            String content = chunk.getContent();
            int tokens = content.length() / 4;
            if (tokenCount + tokens > maxContextTokens) break;

            context.append(content).append("\n\n");
            tokenCount += tokens;

            Map<String, Object> chunkInfo = new LinkedHashMap<>();
            chunkInfo.put("chunkId", chunkId);
            chunkInfo.put("tokens", tokens);
            chunkInfo.put("contentPreview", content.substring(0, Math.min(200, content.length())));
            contextChunksList.add(chunkInfo);

            // Add citation
            long docId = chunk.getDocumentId();
            if (seenDocs.add(docId)) {
                documentRepository.findById(docId).ifPresent(doc ->
                        citations.add(new Citation(docId, doc.getTitle(),
                                content.substring(0, Math.min(200, content.length()))))
                );
            }
        }

        trace.contextChunks = contextChunksList;
        trace.contextTokenCount = tokenCount;

        return context.toString();
    }

    private String buildSystemPrompt(long knowledgeSetId) {
        var ks = knowledgeService.getKnowledgeSet(knowledgeSetId).orElse(null);
        String name = ks != null ? ks.getName() : "this topic";
        return """
            You are a helpful support assistant for "%s". Answer questions accurately based ONLY on the provided context.

            Rules:
            - Only answer based on the provided context. Do not make up information.
            - If the context doesn't contain enough information, say so clearly.
            - Be concise but thorough.
            - If relevant, mention which document or section the information comes from.
            - Format your response in markdown for readability.
            """.formatted(name);
    }

    private String buildUserPrompt(String context, String question) {
        return """
            Context:
            ---
            %s
            ---

            Question: %s

            Please answer the question based on the context above.
            """.formatted(context, question);
    }

    private double estimateConfidence(String answer, String context, String question) {
        // Simple heuristic-based confidence scoring
        String lowerAnswer = answer.toLowerCase();

        // Low confidence indicators
        if (lowerAnswer.contains("i don't have") || lowerAnswer.contains("i don't know") ||
            lowerAnswer.contains("not sure") || lowerAnswer.contains("cannot find") ||
            lowerAnswer.contains("no information") || lowerAnswer.contains("not mentioned")) {
            return 0.2;
        }

        // Check if answer references context content
        String[] answerWords = answer.split("\\s+");
        String[] contextWords = context.split("\\s+");
        Set<String> contextWordSet = Arrays.stream(contextWords)
                .map(String::toLowerCase)
                .filter(w -> w.length() > 4)
                .collect(Collectors.toSet());

        long overlap = Arrays.stream(answerWords)
                .map(String::toLowerCase)
                .filter(w -> w.length() > 4)
                .filter(contextWordSet::contains)
                .count();

        double overlapRatio = answerWords.length > 0 ? (double) overlap / answerWords.length : 0;

        // Higher overlap with context = higher confidence
        double confidence = Math.min(0.5 + overlapRatio, 0.95);
        return Math.round(confidence * 100) / 100.0;
    }

    @Transactional
    private QAResult recordInteraction(long knowledgeSetId, String question, Long socialPostId,
                                        Long socialUserId, String answer, double confidence,
                                        String method, List<Citation> citations, boolean suggestHuman) {
        var interaction = new InteractionEntity();
        interaction.setKnowledgeSetId(knowledgeSetId);
        interaction.setQuestion(question);
        interaction.setAnswer(answer);
        interaction.setAnswerSource(method);
        interaction.setConfidence(confidence);
        interaction.setSocialPostId(socialPostId);
        interaction.setSocialUserId(socialUserId);
        interaction.setEscalated(suggestHuman);
        interaction = interactionRepository.save(interaction);

        return new QAResult(answer, confidence, method, citations, suggestHuman, interaction.getId(), null);
    }
}
