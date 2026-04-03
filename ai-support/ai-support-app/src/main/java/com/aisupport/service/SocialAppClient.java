package com.aisupport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class SocialAppClient {
    private static final Logger log = LoggerFactory.getLogger(SocialAppClient.class);
    private static final long BOT_USER_ID = 72057594037999999L;

    private final OkHttpClient client;
    private final ObjectMapper mapper;

    @Value("${aisupport.social-app.base-url}")
    private String baseUrl;

    @Value("${aisupport.social-app.api-key:}")
    private String apiKey;

    @Value("${aisupport.social-app.app-id:}")
    private String appId;

    public SocialAppClient(ObjectMapper mapper) {
        this.mapper = mapper;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Post a comment on a post via the App API.
     */
    public void postComment(long postId, String content) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("No API key configured, cannot post comment");
            return;
        }
        try {
            var body = mapper.writeValueAsString(Map.of(
                    "postId", String.valueOf(postId),
                    "content", content
            ));
            var request = new Request.Builder()
                    .url(baseUrl + "/api/apps/comments")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("X-App-Id", appId)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(body, MediaType.parse("application/json")))
                    .build();

            try (var response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    log.info("Posted comment on post {}", postId);
                } else {
                    log.warn("Failed to post comment: {} {}", response.code(), response.body() != null ? response.body().string() : "");
                }
            }
        } catch (IOException e) {
            log.error("Error posting comment: {}", e.getMessage());
        }
    }

    /**
     * Create a support case via the App API.
     */
    public void createSupportCase(String title, String description, Long sourcePostId, Long sourceCommentId) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("No API key configured, cannot create support case");
            return;
        }
        try {
            var caseData = new java.util.LinkedHashMap<String, Object>();
            caseData.put("title", title);
            caseData.put("description", description);
            caseData.put("priority", "NORMAL");
            if (sourcePostId != null) caseData.put("sourcePostId", String.valueOf(sourcePostId));
            if (sourceCommentId != null) caseData.put("sourceCommentId", String.valueOf(sourceCommentId));

            var body = mapper.writeValueAsString(caseData);
            var request = new Request.Builder()
                    .url(baseUrl + "/api/apps/cases")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("X-App-Id", appId)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(body, MediaType.parse("application/json")))
                    .build();

            try (var response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    log.info("Created support case: {}", title);
                } else {
                    log.warn("Failed to create support case: {} {}", response.code(), response.body() != null ? response.body().string() : "");
                }
            }
        } catch (IOException e) {
            log.error("Error creating support case: {}", e.getMessage());
        }
    }

    /**
     * Check if a user ID is the bot's own ID (to prevent response loops).
     */
    public boolean isBotUser(long userId) {
        return userId == BOT_USER_ID;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }
}
