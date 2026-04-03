package com.aisupport.service;

import com.aisupport.config.OllamaConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class OllamaService {
    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);
    private final OllamaConfig config;
    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public OllamaService(OllamaConfig config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)  // LLM can be slow
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Generate embeddings for a text using the embedding model.
     * Returns a float array of dimension embedDimensions.
     */
    public float[] embed(String text) throws IOException {
        var body = mapper.writeValueAsString(Map.of(
                "model", config.getEmbedModel(),
                "input", text
        ));
        var request = new Request.Builder()
                .url(config.getBaseUrl() + "/api/embed")
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

        try (var response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Ollama embed failed: " + response.code());
            }
            var json = mapper.readTree(response.body().string());
            var embeddingsNode = json.get("embeddings");
            if (embeddingsNode != null && embeddingsNode.isArray() && embeddingsNode.size() > 0) {
                var vec = embeddingsNode.get(0);
                float[] result = new float[vec.size()];
                for (int i = 0; i < vec.size(); i++) {
                    result[i] = (float) vec.get(i).asDouble();
                }
                return result;
            }
            throw new IOException("No embeddings in response");
        }
    }

    /**
     * Chat completion — send a system prompt and user message, get a response.
     */
    public String chat(String systemPrompt, String userMessage) throws IOException {
        var messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
        );
        var body = mapper.writeValueAsString(Map.of(
                "model", config.getChatModel(),
                "messages", messages,
                "stream", false
        ));
        var request = new Request.Builder()
                .url(config.getBaseUrl() + "/api/chat")
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

        try (var response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Ollama chat failed: " + response.code());
            }
            var json = mapper.readTree(response.body().string());
            return json.get("message").get("content").asText();
        }
    }

    /**
     * Multi-turn chat — send a full conversation history.
     */
    public String chatWithMessages(List<Map<String, String>> messages) throws IOException {
        var body = mapper.writeValueAsString(Map.of(
                "model", config.getChatModel(),
                "messages", messages,
                "stream", false
        ));
        var request = new Request.Builder()
                .url(config.getBaseUrl() + "/api/chat")
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

        try (var response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Ollama chat failed: " + response.code());
            }
            var json = mapper.readTree(response.body().string());
            return json.get("message").get("content").asText();
        }
    }

    /**
     * Check if Ollama is available and the required models are present.
     */
    public boolean isAvailable() {
        try {
            var request = new Request.Builder()
                    .url(config.getBaseUrl() + "/api/tags")
                    .get()
                    .build();
            try (var response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            return false;
        }
    }
}
