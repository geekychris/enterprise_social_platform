package com.aisupport.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GroundingService {
    private static final Logger log = LoggerFactory.getLogger(GroundingService.class);

    /**
     * Check how well an answer is grounded in the provided context.
     * Returns a score 0-1 and details about which claims are/aren't supported.
     */
    public GroundingResult verify(String answer, String context) {
        if (answer == null || context == null) return new GroundingResult(0, List.of(), List.of());

        // Extract key noun phrases / technical terms from the answer
        String[] answerSentences = answer.split("[.!?]+");
        List<String> supported = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        String lowerContext = context.toLowerCase();

        for (String sentence : answerSentences) {
            String trimmed = sentence.trim();
            if (trimmed.length() < 10) continue;
            if (trimmed.startsWith("*") || trimmed.startsWith("#") || trimmed.startsWith("---")) continue;

            // Extract significant words (4+ chars) from the sentence
            Set<String> sigWords = Arrays.stream(trimmed.toLowerCase().split("\\W+"))
                    .filter(w -> w.length() >= 4)
                    .filter(w -> !Set.of("this", "that", "with", "from", "your", "have", "will",
                            "been", "were", "they", "their", "about", "would", "could", "should",
                            "also", "more", "some", "then", "than", "when", "here", "based").contains(w))
                    .collect(Collectors.toSet());

            if (sigWords.isEmpty()) continue;

            // Check what fraction of significant words appear in context
            long found = sigWords.stream().filter(w -> lowerContext.contains(w)).count();
            double ratio = (double) found / sigWords.size();

            if (ratio >= 0.5) {
                supported.add(trimmed.substring(0, Math.min(100, trimmed.length())));
            } else {
                unsupported.add(trimmed.substring(0, Math.min(100, trimmed.length())));
            }
        }

        int total = supported.size() + unsupported.size();
        double score = total > 0 ? (double) supported.size() / total : 0;
        return new GroundingResult(Math.round(score * 100) / 100.0, supported, unsupported);
    }

    public record GroundingResult(double score, List<String> supportedClaims, List<String> unsupportedClaims) {}
}
