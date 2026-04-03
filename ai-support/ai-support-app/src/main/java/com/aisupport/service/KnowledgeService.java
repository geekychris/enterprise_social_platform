package com.aisupport.service;

import com.aisupport.persistence.entity.*;
import com.aisupport.persistence.repository.*;
import com.aisupport.search.UnifiedSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class KnowledgeService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);
    private static final int CHUNK_SIZE = 500; // approximate tokens
    private static final int CHUNK_OVERLAP = 50;

    private final KnowledgeSetRepository knowledgeSetRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final UnifiedSearchService unifiedSearch;
    private final OllamaService ollamaService;

    public KnowledgeService(KnowledgeSetRepository knowledgeSetRepository,
                            DocumentRepository documentRepository,
                            DocumentChunkRepository chunkRepository,
                            UnifiedSearchService unifiedSearch,
                            OllamaService ollamaService) {
        this.knowledgeSetRepository = knowledgeSetRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.unifiedSearch = unifiedSearch;
        this.ollamaService = ollamaService;
    }

    // ── Knowledge Sets ───────────────────────────────────────────

    @Transactional
    public KnowledgeSetEntity createKnowledgeSet(String name, String slug, String description,
                                                   Long socialPageId, String socialPageType) {
        var ks = new KnowledgeSetEntity();
        ks.setName(name);
        ks.setSlug(slug);
        ks.setDescription(description);
        ks.setSocialPageId(socialPageId);
        ks.setSocialPageType(socialPageType);
        return knowledgeSetRepository.save(ks);
    }

    public List<KnowledgeSetEntity> getAllKnowledgeSets() {
        return knowledgeSetRepository.findAll();
    }

    public Optional<KnowledgeSetEntity> getKnowledgeSet(long id) {
        return knowledgeSetRepository.findById(id);
    }

    public Optional<KnowledgeSetEntity> getBySlug(String slug) {
        return knowledgeSetRepository.findBySlug(slug);
    }

    public Optional<KnowledgeSetEntity> getBySocialPageId(Long pageId) {
        return knowledgeSetRepository.findBySocialPageId(pageId);
    }

    @Transactional
    public KnowledgeSetEntity saveKnowledgeSet(KnowledgeSetEntity ks) {
        return knowledgeSetRepository.save(ks);
    }

    // ── Documents ────────────────────────────────────────────────

    @Transactional
    public DocumentEntity addDocument(long knowledgeSetId, String title, String content,
                                       String sourceUrl, String sourceType) {
        String hash = sha256(content);

        // Check for duplicates within this knowledge set
        var existing = documentRepository.findByContentHash(hash);
        if (existing.isPresent() && existing.get().getKnowledgeSetId().equals(knowledgeSetId)) {
            log.info("Document already exists (hash={}), skipping", hash);
            return existing.get();
        }

        var doc = new DocumentEntity();
        doc.setKnowledgeSetId(knowledgeSetId);
        doc.setTitle(title);
        doc.setContent(content);
        doc.setSourceUrl(sourceUrl);
        doc.setSourceType(sourceType != null ? sourceType : "MANUAL");
        doc.setContentHash(hash);
        doc.setIndexed(false);
        return documentRepository.save(doc);
    }

    public List<DocumentEntity> getDocuments(long knowledgeSetId) {
        return documentRepository.findByKnowledgeSetId(knowledgeSetId);
    }

    @Transactional
    public void deleteDocument(long documentId) {
        var doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null) return;

        long ksId = doc.getKnowledgeSetId();
        chunkRepository.deleteByDocumentId(documentId);
        documentRepository.deleteById(documentId);

        try {
            unifiedSearch.deleteDocument(ksId, documentId);
        } catch (IOException e) {
            log.warn("Failed to delete from search index: {}", e.getMessage());
        }
    }

    // ── Chunking & Indexing ──────────────────────────────────────

    /**
     * Chunk a document and index all chunks in Lucene + vector store.
     */
    @Transactional
    public int indexDocument(long documentId) {
        var doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null) return 0;

        long ksId = doc.getKnowledgeSetId();

        // Delete old chunks
        chunkRepository.deleteByDocumentId(documentId);

        // Split into chunks
        List<String> chunks = chunkText(doc.getContent(), CHUNK_SIZE, CHUNK_OVERLAP);

        int indexed = 0;
        for (int i = 0; i < chunks.size(); i++) {
            String chunkContent = chunks.get(i);
            int tokenCount = estimateTokens(chunkContent);

            var chunk = new DocumentChunkEntity();
            chunk.setDocumentId(documentId);
            chunk.setKnowledgeSetId(ksId);
            chunk.setChunkIndex(i);
            chunk.setContent(chunkContent);
            chunk.setTokenCount(tokenCount);

            // Generate embedding
            try {
                float[] embedding = ollamaService.embed(chunkContent);
                chunk.setEmbedding(floatsToBytes(embedding));

                chunk = chunkRepository.save(chunk);

                // Index in unified search (lexical + vector)
                unifiedSearch.indexChunk(ksId, chunk.getId(), documentId, doc.getTitle(), chunkContent, embedding);

                indexed++;
            } catch (Exception e) {
                log.warn("Failed to index chunk {} of document {}: {}", i, documentId, e.getMessage());
                chunk = chunkRepository.save(chunk);
            }
        }

        // Mark document as indexed
        doc.setIndexed(true);
        doc.setUpdatedAt(OffsetDateTime.now());
        documentRepository.save(doc);

        log.info("Indexed document {} ({} chunks, {} successfully indexed)", documentId, chunks.size(), indexed);
        return indexed;
    }

    /**
     * Index all unindexed documents in a knowledge set.
     */
    @Async
    @Transactional
    public void indexAllUnindexed(long knowledgeSetId) {
        var unindexed = documentRepository.findByKnowledgeSetIdAndIndexedFalse(knowledgeSetId);
        log.info("Indexing {} unindexed documents for ks-{}", unindexed.size(), knowledgeSetId);
        for (var doc : unindexed) {
            indexDocument(doc.getId());
        }
    }

    /**
     * Get total token count for a knowledge set (to decide context injection vs RAG).
     */
    public long getTotalTokenCount(long knowledgeSetId) {
        var chunks = chunkRepository.findByKnowledgeSetId(knowledgeSetId);
        return chunks.stream().mapToLong(DocumentChunkEntity::getTokenCount).sum();
    }

    /**
     * Get all chunk content for a knowledge set (for direct context injection).
     */
    public String getAllContent(long knowledgeSetId) {
        var docs = documentRepository.findByKnowledgeSetId(knowledgeSetId);
        StringBuilder sb = new StringBuilder();
        for (var doc : docs) {
            sb.append("## ").append(doc.getTitle()).append("\n\n");
            sb.append(doc.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    // ── Stats ────────────────────────────────────────────────────

    public Map<String, Object> getStats(long knowledgeSetId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("documentCount", documentRepository.countByKnowledgeSetId(knowledgeSetId));
        stats.put("chunkCount", chunkRepository.countByKnowledgeSetId(knowledgeSetId));
        stats.put("totalTokens", getTotalTokenCount(knowledgeSetId));
        stats.put("luceneDocCount", unifiedSearch.getDocCount(knowledgeSetId));
        stats.put("vectorCount", unifiedSearch.getVectorCount(knowledgeSetId));
        return stats;
    }

    // ── Helpers ──────────────────────────────────────────────────

    /**
     * Split text into chunks of approximately targetTokens size with overlap.
     */
    static List<String> chunkText(String text, int targetTokens, int overlapTokens) {
        // Approximate: 1 token ≈ 4 chars
        int charsPerChunk = targetTokens * 4;
        int overlapChars = overlapTokens * 4;

        if (text.length() <= charsPerChunk) {
            return List.of(text);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + charsPerChunk, text.length());

            // Try to break at a sentence boundary
            if (end < text.length()) {
                int lastPeriod = text.substring(start, end).lastIndexOf(". ");
                if (lastPeriod >= 0) lastPeriod += start;
                int lastNewline = text.substring(start, end).lastIndexOf('\n');
                if (lastNewline >= 0) lastNewline += start;
                int breakPoint = Math.max(lastPeriod, lastNewline);
                if (breakPoint > start + charsPerChunk / 2) {
                    end = breakPoint + 1;
                }
            }

            chunks.add(text.substring(start, end).trim());
            start = end - overlapChars;
            if (start < 0) start = 0;
            if (end >= text.length()) break;
        }
        return chunks;
    }

    static int estimateTokens(String text) {
        return text.length() / 4;
    }

    static String sha256(String text) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            var hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }

    static byte[] floatsToBytes(float[] floats) {
        var buf = java.nio.ByteBuffer.allocate(floats.length * 4);
        for (float f : floats) buf.putFloat(f);
        return buf.array();
    }

    static float[] bytesToFloats(byte[] bytes) {
        var buf = java.nio.ByteBuffer.wrap(bytes);
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) floats[i] = buf.getFloat();
        return floats;
    }
}
