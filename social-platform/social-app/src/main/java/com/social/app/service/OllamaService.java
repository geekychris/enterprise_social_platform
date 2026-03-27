package com.social.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@Service
public class OllamaService {

    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${social.ai.ollama-url}")
    private String ollamaUrl;

    @Value("${social.ai.model}")
    private String model;

    /**
     * Stream a chat completion to the SSE emitter. Each token is sent as an SSE event.
     */
    public void streamChat(String systemPrompt, String userMessage, SseEmitter emitter) {
        try {
            var url = URI.create(ollamaUrl + "/api/chat").toURL();
            var conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(120000);

            String body = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
                put("model", model);
                put("messages", new Object[]{
                        new java.util.LinkedHashMap<>() {{
                            put("role", "system");
                            put("content", systemPrompt);
                        }},
                        new java.util.LinkedHashMap<>() {{
                            put("role", "user");
                            put("content", userMessage);
                        }}
                });
                put("stream", true);
            }});

            conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            conn.getOutputStream().flush();

            if (conn.getResponseCode() != 200) {
                emitter.send(SseEmitter.event().name("error").data("Ollama returned status " + conn.getResponseCode()));
                emitter.complete();
                return;
            }

            try (var reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    JsonNode node = objectMapper.readTree(line);
                    if (node.has("message") && node.get("message").has("content")) {
                        String token = node.get("message").get("content").asText();
                        // Replace newlines with placeholder since SSE data fields can't contain raw newlines
                        emitter.send(SseEmitter.event().name("token").data(token.replace("\n", "⏎")));
                    }
                    if (node.has("done") && node.get("done").asBoolean()) {
                        break;
                    }
                }
            }

            emitter.send(SseEmitter.event().name("done").data(""));
            emitter.complete();

        } catch (Exception e) {
            log.error("Ollama streaming failed", e);
            try {
                emitter.send(SseEmitter.event().name("error").data("AI service unavailable: " + e.getMessage()));
                emitter.complete();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Blocking chat completion — gathers the full response into the StringBuilder.
     */
    public void chatBlocking(String systemPrompt, String userMessage, StringBuilder result) throws Exception {
        var url = URI.create(ollamaUrl + "/api/chat").toURL();
        var conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(120000);

        String body = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("model", model);
            put("messages", new Object[]{
                    new java.util.LinkedHashMap<>() {{
                        put("role", "system");
                        put("content", systemPrompt);
                    }},
                    new java.util.LinkedHashMap<>() {{
                        put("role", "user");
                        put("content", userMessage);
                    }}
            });
            put("stream", true);
        }});

        conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        conn.getOutputStream().flush();

        if (conn.getResponseCode() != 200) {
            throw new RuntimeException("Ollama returned status " + conn.getResponseCode());
        }

        try (var reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonNode node = objectMapper.readTree(line);
                if (node.has("message") && node.get("message").has("content")) {
                    result.append(node.get("message").get("content").asText());
                }
                if (node.has("done") && node.get("done").asBoolean()) {
                    break;
                }
            }
        }
    }
}
