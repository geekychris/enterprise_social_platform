package com.aisupport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "aisupport.ollama")
public class OllamaConfig {
    private String baseUrl = "http://localhost:11434";
    private String chatModel = "llama3";
    private String embedModel = "nomic-embed-text";
    private int embedDimensions = 768;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getChatModel() { return chatModel; }
    public void setChatModel(String chatModel) { this.chatModel = chatModel; }
    public String getEmbedModel() { return embedModel; }
    public void setEmbedModel(String embedModel) { this.embedModel = embedModel; }
    public int getEmbedDimensions() { return embedDimensions; }
    public void setEmbedDimensions(int embedDimensions) { this.embedDimensions = embedDimensions; }
}
