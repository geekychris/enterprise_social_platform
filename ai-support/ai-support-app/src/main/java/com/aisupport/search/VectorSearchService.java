package com.aisupport.search;

import com.aisupport.service.OllamaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VectorSearchService {
    private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);

    private final OllamaService ollamaService;
    private final Path storageDir;

    // In-memory store: knowledgeSetId -> list of (chunkId, embedding)
    private final Map<Long, List<VectorEntry>> vectorStore = new ConcurrentHashMap<>();

    public VectorSearchService(OllamaService ollamaService,
                               @Value("${aisupport.vectors.storage-dir}") String storageDir) {
        this.ollamaService = ollamaService;
        this.storageDir = Path.of(storageDir);
        try { Files.createDirectories(this.storageDir); } catch (IOException e) { /* ignore */ }
        loadAll();
    }

    public record VectorEntry(long chunkId, long documentId, float[] embedding) {}

    /**
     * Store an embedding for a chunk.
     */
    public void store(long knowledgeSetId, long chunkId, long documentId, float[] embedding) {
        vectorStore.computeIfAbsent(knowledgeSetId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new VectorEntry(chunkId, documentId, embedding));
    }

    /**
     * Persist all embeddings for a knowledge set to disk.
     */
    public void persist(long knowledgeSetId) {
        var entries = vectorStore.get(knowledgeSetId);
        if (entries == null) return;
        try {
            Path file = storageDir.resolve("ks-" + knowledgeSetId + ".vec");
            try (var dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file.toFile())))) {
                synchronized (entries) {
                    dos.writeInt(entries.size());
                    for (var entry : entries) {
                        dos.writeLong(entry.chunkId);
                        dos.writeLong(entry.documentId);
                        dos.writeInt(entry.embedding.length);
                        for (float f : entry.embedding) dos.writeFloat(f);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to persist vectors for ks-{}: {}", knowledgeSetId, e.getMessage());
        }
    }

    /**
     * Load all persisted vector stores from disk.
     */
    private void loadAll() {
        try (var stream = Files.list(storageDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".vec")).forEach(this::loadFile);
        } catch (IOException e) {
            log.debug("No existing vector stores to load");
        }
    }

    private void loadFile(Path file) {
        try (var dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file.toFile())))) {
            String name = file.getFileName().toString();
            long ksId = Long.parseLong(name.replace("ks-", "").replace(".vec", ""));
            int count = dis.readInt();
            var entries = Collections.synchronizedList(new ArrayList<VectorEntry>(count));
            for (int i = 0; i < count; i++) {
                long chunkId = dis.readLong();
                long documentId = dis.readLong();
                int dim = dis.readInt();
                float[] embedding = new float[dim];
                for (int j = 0; j < dim; j++) embedding[j] = dis.readFloat();
                entries.add(new VectorEntry(chunkId, documentId, embedding));
            }
            vectorStore.put(ksId, entries);
            log.info("Loaded {} vectors for ks-{}", count, ksId);
        } catch (Exception e) {
            log.warn("Failed to load vector file {}: {}", file, e.getMessage());
        }
    }

    /**
     * Semantic search: embed the query and find top-K most similar chunks.
     */
    public List<Map<String, Object>> search(long knowledgeSetId, String query, int topK) {
        try {
            float[] queryVec = ollamaService.embed(query);
            return searchByVector(knowledgeSetId, queryVec, topK);
        } catch (Exception e) {
            log.warn("Vector search failed for ks-{}: {}", knowledgeSetId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Search by pre-computed vector.
     */
    public List<Map<String, Object>> searchByVector(long knowledgeSetId, float[] queryVec, int topK) {
        var entries = vectorStore.get(knowledgeSetId);
        if (entries == null || entries.isEmpty()) return List.of();

        // Compute cosine similarity for all entries
        record Scored(long chunkId, long documentId, double score) {}
        List<Scored> scored;
        synchronized (entries) {
            scored = entries.stream()
                    .map(e -> new Scored(e.chunkId, e.documentId, cosineSimilarity(queryVec, e.embedding)))
                    .sorted((a, b) -> Double.compare(b.score, a.score))
                    .limit(topK)
                    .toList();
        }

        return scored.stream().map(s -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("chunkId", s.chunkId);
            result.put("documentId", s.documentId);
            result.put("score", s.score);
            return result;
        }).toList();
    }

    /**
     * Search across ALL knowledge sets (for cross-routing).
     */
    public List<Map<String, Object>> searchAll(String query, int topK) {
        try {
            float[] queryVec = ollamaService.embed(query);
            List<Map<String, Object>> allResults = new ArrayList<>();
            for (var entry : vectorStore.entrySet()) {
                var results = searchByVector(entry.getKey(), queryVec, topK);
                for (var r : results) {
                    r.put("knowledgeSetId", entry.getKey());
                    allResults.add(r);
                }
            }
            allResults.sort((a, b) -> Double.compare((double) b.get("score"), (double) a.get("score")));
            return allResults.stream().limit(topK).toList();
        } catch (Exception e) {
            log.warn("Cross-KS vector search failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Remove all vectors for a document.
     */
    public void deleteDocument(long knowledgeSetId, long documentId) {
        var entries = vectorStore.get(knowledgeSetId);
        if (entries != null) {
            synchronized (entries) {
                entries.removeIf(e -> e.documentId == documentId);
            }
        }
    }

    public int getVectorCount(long knowledgeSetId) {
        var entries = vectorStore.get(knowledgeSetId);
        return entries != null ? entries.size() : 0;
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }
}
