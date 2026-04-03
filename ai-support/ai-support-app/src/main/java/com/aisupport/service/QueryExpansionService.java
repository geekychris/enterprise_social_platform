package com.aisupport.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class QueryExpansionService {
    private static final Logger log = LoggerFactory.getLogger(QueryExpansionService.class);

    private final OllamaService ollamaService;

    public QueryExpansionService(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
    }

    /**
     * Generate alternate search queries from the original question.
     * Returns the original query plus 2-3 expansions.
     */
    public List<String> expand(String question) {
        List<String> queries = new ArrayList<>();
        queries.add(question);

        try {
            String prompt = "Generate 3 alternative search queries for this question. Return ONLY the queries, one per line, no numbering:\n\n" + question;
            String response = ollamaService.chat("You are a search query generator. Output only search queries, one per line.", prompt);

            for (String line : response.split("\n")) {
                String trimmed = line.trim().replaceAll("^\\d+[.)\\s]+", "").trim();
                if (!trimmed.isEmpty() && trimmed.length() > 5 && trimmed.length() < 200) {
                    queries.add(trimmed);
                }
                if (queries.size() >= 4) break;
            }
        } catch (Exception e) {
            log.debug("Query expansion failed: {}", e.getMessage());
        }

        return queries;
    }

    /**
     * Extract key search terms from a question (lightweight, no LLM call).
     */
    public String extractKeyTerms(String question) {
        // Remove stop words and question words
        return question.replaceAll("(?i)\\b(how|what|why|when|where|which|can|do|does|is|are|the|a|an|to|in|on|of|for|with|my|i|am|hi|hello|please|help|trying)\\b", "")
                .replaceAll("\\s+", " ").trim();
    }
}
