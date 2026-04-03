package com.aisupport.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "qa_traces")
public class QATraceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "interaction_id")
    private Long interactionId;

    @Column(name = "knowledge_set_id")
    private Long knowledgeSetId;

    @Column(name = "question", columnDefinition = "TEXT", nullable = false)
    private String question;

    @Column(name = "method", length = 50, nullable = false)
    private String method;

    @Column(name = "confidence")
    private Double confidence;

    // Search step
    @Column(name = "lexical_query", columnDefinition = "TEXT")
    private String lexicalQuery;

    @Column(name = "lexical_results", columnDefinition = "jsonb", insertable = false, updatable = false)
    private String lexicalResults;

    @Column(name = "semantic_query", columnDefinition = "TEXT")
    private String semanticQuery;

    @Column(name = "semantic_results", columnDefinition = "jsonb", insertable = false, updatable = false)
    private String semanticResults;

    @Column(name = "merged_results", columnDefinition = "jsonb", insertable = false, updatable = false)
    private String mergedResults;

    // Context step
    @Column(name = "context_chunks", columnDefinition = "jsonb", insertable = false, updatable = false)
    private String contextChunks;

    @Column(name = "context_token_count")
    private Integer contextTokenCount;

    @Column(name = "total_knowledge_tokens")
    private Long totalKnowledgeTokens;

    // LLM step
    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "user_prompt", columnDefinition = "TEXT")
    private String userPrompt;

    @Column(name = "llm_model", length = 100)
    private String llmModel;

    @Column(name = "llm_response", columnDefinition = "TEXT")
    private String llmResponse;

    @Column(name = "llm_duration_ms")
    private Long llmDurationMs;

    // Answer
    @Column(name = "answer", columnDefinition = "TEXT")
    private String answer;

    @Column(name = "citations", columnDefinition = "jsonb", insertable = false, updatable = false)
    private String citations;

    @Column(name = "suggest_human")
    private Boolean suggestHuman;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    // All getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInteractionId() { return interactionId; }
    public void setInteractionId(Long interactionId) { this.interactionId = interactionId; }
    public Long getKnowledgeSetId() { return knowledgeSetId; }
    public void setKnowledgeSetId(Long knowledgeSetId) { this.knowledgeSetId = knowledgeSetId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public String getLexicalQuery() { return lexicalQuery; }
    public void setLexicalQuery(String lexicalQuery) { this.lexicalQuery = lexicalQuery; }
    public String getLexicalResults() { return lexicalResults; }
    public String getSemanticQuery() { return semanticQuery; }
    public void setSemanticQuery(String semanticQuery) { this.semanticQuery = semanticQuery; }
    public String getSemanticResults() { return semanticResults; }
    public String getMergedResults() { return mergedResults; }
    public String getContextChunks() { return contextChunks; }
    public Integer getContextTokenCount() { return contextTokenCount; }
    public void setContextTokenCount(Integer contextTokenCount) { this.contextTokenCount = contextTokenCount; }
    public Long getTotalKnowledgeTokens() { return totalKnowledgeTokens; }
    public void setTotalKnowledgeTokens(Long totalKnowledgeTokens) { this.totalKnowledgeTokens = totalKnowledgeTokens; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public String getUserPrompt() { return userPrompt; }
    public void setUserPrompt(String userPrompt) { this.userPrompt = userPrompt; }
    public String getLlmModel() { return llmModel; }
    public void setLlmModel(String llmModel) { this.llmModel = llmModel; }
    public String getLlmResponse() { return llmResponse; }
    public void setLlmResponse(String llmResponse) { this.llmResponse = llmResponse; }
    public Long getLlmDurationMs() { return llmDurationMs; }
    public void setLlmDurationMs(Long llmDurationMs) { this.llmDurationMs = llmDurationMs; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getCitations() { return citations; }
    public Boolean getSuggestHuman() { return suggestHuman; }
    public void setSuggestHuman(Boolean suggestHuman) { this.suggestHuman = suggestHuman; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
