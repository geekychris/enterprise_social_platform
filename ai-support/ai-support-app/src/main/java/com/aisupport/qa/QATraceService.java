package com.aisupport.qa;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class QATraceService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public QATraceService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public static class TraceBuilder {
        Long knowledgeSetId;
        String question;
        String method;
        Double confidence;
        String lexicalQuery;
        List<Map<String, Object>> lexicalResults;
        String semanticQuery;
        List<Map<String, Object>> semanticResults;
        List<Map<String, Object>> mergedResults;
        List<Map<String, Object>> contextChunks;
        Integer contextTokenCount;
        Long totalKnowledgeTokens;
        String systemPrompt;
        String userPrompt;
        String llmModel;
        String llmResponse;
        Long llmDurationMs;
        String answer;
        List<Map<String, Object>> citations;
        Boolean suggestHuman;
        Long interactionId;
    }

    public TraceBuilder builder() { return new TraceBuilder(); }

    public long save(TraceBuilder b) {
        try {
            String sql = """
                INSERT INTO qa_traces (
                    knowledge_set_id, question, method, confidence,
                    lexical_query, lexical_results, semantic_query, semantic_results,
                    merged_results, context_chunks, context_token_count, total_knowledge_tokens,
                    system_prompt, user_prompt, llm_model, llm_response, llm_duration_ms,
                    answer, citations, suggest_human, interaction_id, created_at
                ) VALUES (?,?,?,?, ?,?::jsonb,?,?::jsonb, ?::jsonb,?::jsonb,?,?, ?,?,?,?,?, ?,?::jsonb,?,?, now())
                RETURNING id
                """;
            return jdbc.queryForObject(sql, Long.class,
                    b.knowledgeSetId, b.question, b.method, b.confidence,
                    b.lexicalQuery, toJson(b.lexicalResults), b.semanticQuery, toJson(b.semanticResults),
                    toJson(b.mergedResults), toJson(b.contextChunks), b.contextTokenCount, b.totalKnowledgeTokens,
                    b.systemPrompt, b.userPrompt, b.llmModel, b.llmResponse, b.llmDurationMs,
                    b.answer, toJson(b.citations), b.suggestHuman, b.interactionId
            );
        } catch (Exception e) {
            return -1;
        }
    }

    public Map<String, Object> getTrace(long traceId) {
        var rows = jdbc.queryForList("SELECT * FROM qa_traces WHERE id = ?", traceId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> getRecentTraces(Long knowledgeSetId, int limit) {
        if (knowledgeSetId != null) {
            return jdbc.queryForList(
                "SELECT id, knowledge_set_id, question, method, confidence, llm_duration_ms, suggest_human, created_at FROM qa_traces WHERE knowledge_set_id = ? ORDER BY created_at DESC LIMIT ?",
                knowledgeSetId, limit);
        }
        return jdbc.queryForList(
            "SELECT id, knowledge_set_id, question, method, confidence, llm_duration_ms, suggest_human, created_at FROM qa_traces ORDER BY created_at DESC LIMIT ?",
            limit);
    }

    private String toJson(Object obj) {
        if (obj == null) return "[]";
        try { return mapper.writeValueAsString(obj); } catch (Exception e) { return "[]"; }
    }
}
