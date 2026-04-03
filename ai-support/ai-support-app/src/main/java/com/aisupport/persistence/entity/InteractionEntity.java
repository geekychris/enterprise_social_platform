package com.aisupport.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "interactions")
public class InteractionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "knowledge_set_id")
    private Long knowledgeSetId;

    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "answer", columnDefinition = "TEXT")
    private String answer;

    @Column(name = "answer_source", length = 50)
    private String answerSource;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "social_post_id")
    private Long socialPostId;

    @Column(name = "social_comment_id")
    private Long socialCommentId;

    @Column(name = "social_user_id")
    private Long socialUserId;

    @Column(name = "helpful")
    private Boolean helpful;

    @Column(name = "escalated", nullable = false)
    private boolean escalated;

    @Column(name = "support_case_id")
    private Long supportCaseId;

    @Column(name = "metadata", columnDefinition = "jsonb", insertable = false, updatable = false)
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getKnowledgeSetId() {
        return knowledgeSetId;
    }

    public void setKnowledgeSetId(Long knowledgeSetId) {
        this.knowledgeSetId = knowledgeSetId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getAnswerSource() {
        return answerSource;
    }

    public void setAnswerSource(String answerSource) {
        this.answerSource = answerSource;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Long getSocialPostId() {
        return socialPostId;
    }

    public void setSocialPostId(Long socialPostId) {
        this.socialPostId = socialPostId;
    }

    public Long getSocialCommentId() {
        return socialCommentId;
    }

    public void setSocialCommentId(Long socialCommentId) {
        this.socialCommentId = socialCommentId;
    }

    public Long getSocialUserId() {
        return socialUserId;
    }

    public void setSocialUserId(Long socialUserId) {
        this.socialUserId = socialUserId;
    }

    public Boolean getHelpful() {
        return helpful;
    }

    public void setHelpful(Boolean helpful) {
        this.helpful = helpful;
    }

    public boolean isEscalated() {
        return escalated;
    }

    public void setEscalated(boolean escalated) {
        this.escalated = escalated;
    }

    public Long getSupportCaseId() {
        return supportCaseId;
    }

    public void setSupportCaseId(Long supportCaseId) {
        this.supportCaseId = supportCaseId;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
