package com.aisupport.search;

import com.aisupport.service.OllamaService;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified search service using a single Lucene index per knowledge set
 * for both lexical (BM25) and vector (HNSW KNN) search.
 *
 * Replaces the separate LuceneSearchService and VectorSearchService.
 */
@Service
public class UnifiedSearchService {
    private static final Logger log = LoggerFactory.getLogger(UnifiedSearchService.class);

    private final Path baseIndexDir;
    private final StandardAnalyzer analyzer = new StandardAnalyzer();
    private final Map<Long, IndexWriter> writers = new ConcurrentHashMap<>();
    private final OllamaService ollamaService;

    @Value("${aisupport.ollama.embed-dimensions:768}")
    private int embeddingDimensions;

    public UnifiedSearchService(@Value("${aisupport.lucene.index-dir}") String indexDir,
                                 OllamaService ollamaService) {
        this.baseIndexDir = Path.of(indexDir);
        this.ollamaService = ollamaService;
    }

    private IndexWriter getWriter(long knowledgeSetId) throws IOException {
        return writers.computeIfAbsent(knowledgeSetId, ksId -> {
            try {
                Path dir = baseIndexDir.resolve("ks-" + ksId);
                Files.createDirectories(dir);
                var iwc = new IndexWriterConfig(analyzer);
                iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
                return new IndexWriter(FSDirectory.open(dir), iwc);
            } catch (IOException e) {
                throw new RuntimeException("Failed to open Lucene index for ks-" + ksId, e);
            }
        });
    }

    private IndexSearcher getSearcher(long knowledgeSetId) throws IOException {
        var writer = getWriter(knowledgeSetId);
        var reader = DirectoryReader.open(writer);
        return new IndexSearcher(reader);
    }

    // ── Indexing ─────────────────────────────────────────────────

    /**
     * Index a chunk with both text fields (for BM25) and vector field (for KNN).
     */
    public void indexChunk(long knowledgeSetId, long chunkId, long documentId,
                           String title, String content, float[] embedding) throws IOException {
        var writer = getWriter(knowledgeSetId);
        var doc = new Document();
        doc.add(new StringField("chunkId", String.valueOf(chunkId), Field.Store.YES));
        doc.add(new StringField("documentId", String.valueOf(documentId), Field.Store.YES));
        doc.add(new TextField("title", title, Field.Store.YES));
        doc.add(new TextField("content", content, Field.Store.YES));
        doc.add(new StoredField("knowledgeSetId", knowledgeSetId));
        // Store chunk index for neighbor retrieval
        doc.add(new NumericDocValuesField("chunkIndex", chunkId));
        doc.add(new StoredField("chunkIndex", chunkId));

        // KNN vector field for semantic search (HNSW index)
        if (embedding != null && embedding.length > 0) {
            doc.add(new KnnFloatVectorField("embedding", embedding, VectorSimilarityFunction.COSINE));
        }

        writer.updateDocument(new Term("chunkId", String.valueOf(chunkId)), doc);
        writer.commit();
    }

    /**
     * Index without embedding (backward compat).
     */
    public void indexChunk(long knowledgeSetId, long chunkId, long documentId,
                           String title, String content) throws IOException {
        indexChunk(knowledgeSetId, chunkId, documentId, title, content, null);
    }

    public void deleteDocument(long knowledgeSetId, long documentId) throws IOException {
        var writer = getWriter(knowledgeSetId);
        writer.deleteDocuments(new Term("documentId", String.valueOf(documentId)));
        writer.commit();
    }

    // ── Lexical Search (BM25) ────────────────────────────────────

    public List<Map<String, Object>> searchLexical(long knowledgeSetId, String query, int topK) {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            var searcher = getSearcher(knowledgeSetId);
            var parser = new MultiFieldQueryParser(new String[]{"title", "content"}, analyzer);
            parser.setDefaultOperator(QueryParser.Operator.OR);
            var luceneQuery = parser.parse(QueryParser.escape(query));

            var topDocs = searcher.search(luceneQuery, topK);
            for (var scoreDoc : topDocs.scoreDocs) {
                results.add(docToMap(searcher, scoreDoc, "lexical"));
            }
            searcher.getIndexReader().close();
        } catch (Exception e) {
            log.warn("Lexical search failed for ks-{}: {}", knowledgeSetId, e.getMessage());
        }
        return results;
    }

    // ── Vector Search (KNN HNSW) ─────────────────────────────────

    /**
     * Semantic search using Lucene's KNN vector query (HNSW).
     * Embeds the query text, then finds nearest neighbors.
     */
    public List<Map<String, Object>> searchSemantic(long knowledgeSetId, String query, int topK) {
        try {
            float[] queryVec = ollamaService.embed(query);
            return searchByVector(knowledgeSetId, queryVec, topK);
        } catch (Exception e) {
            log.warn("Semantic search failed for ks-{}: {}", knowledgeSetId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Search by pre-computed vector using Lucene KNN.
     */
    public List<Map<String, Object>> searchByVector(long knowledgeSetId, float[] queryVec, int topK) {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            var searcher = getSearcher(knowledgeSetId);
            var knnQuery = new KnnFloatVectorQuery("embedding", queryVec, topK);
            var topDocs = searcher.search(knnQuery, topK);

            for (var scoreDoc : topDocs.scoreDocs) {
                results.add(docToMap(searcher, scoreDoc, "semantic"));
            }
            searcher.getIndexReader().close();
        } catch (Exception e) {
            log.warn("KNN search failed for ks-{}: {}", knowledgeSetId, e.getMessage());
        }
        return results;
    }

    // ── Hybrid Search ────────────────────────────────────────────

    /**
     * Combined lexical + semantic search with score fusion.
     */
    public List<Map<String, Object>> searchHybrid(long knowledgeSetId, String query, int topK) {
        var lexical = searchLexical(knowledgeSetId, query, topK);
        var semantic = searchSemantic(knowledgeSetId, query, topK);

        // Reciprocal rank fusion
        Map<Long, Map<String, Object>> merged = new LinkedHashMap<>();
        Map<Long, Double> scores = new HashMap<>();

        for (int i = 0; i < lexical.size(); i++) {
            long chunkId = ((Number) lexical.get(i).get("chunkId")).longValue();
            scores.merge(chunkId, 1.0 / (i + 1 + 60), Double::sum); // RRF with k=60
            merged.putIfAbsent(chunkId, lexical.get(i));
        }
        for (int i = 0; i < semantic.size(); i++) {
            long chunkId = ((Number) semantic.get(i).get("chunkId")).longValue();
            scores.merge(chunkId, 1.0 / (i + 1 + 60), Double::sum);
            merged.putIfAbsent(chunkId, semantic.get(i));
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    var doc = new LinkedHashMap<>(merged.get(e.getKey()));
                    doc.put("hybridScore", e.getValue());
                    return (Map<String, Object>) doc;
                })
                .toList();
    }

    // ── Cross-Knowledge-Set Search ───────────────────────────────

    /**
     * Search across ALL knowledge sets for routing.
     */
    public List<Map<String, Object>> searchAllSemantic(String query, int topK) {
        try {
            float[] queryVec = ollamaService.embed(query);
            List<Map<String, Object>> allResults = new ArrayList<>();

            for (var entry : writers.entrySet()) {
                long ksId = entry.getKey();
                var results = searchByVector(ksId, queryVec, topK);
                for (var r : results) {
                    r.put("knowledgeSetId", ksId);
                    allResults.add(r);
                }
            }

            allResults.sort((a, b) -> Double.compare(
                    ((Number) b.get("score")).doubleValue(),
                    ((Number) a.get("score")).doubleValue()));
            return allResults.stream().limit(topK).toList();
        } catch (Exception e) {
            log.warn("Cross-KS search failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ── Neighbor Chunk Retrieval ──────────────────────────────────

    /**
     * Given a chunk ID, retrieve its neighboring chunks (previous and next)
     * from the same document for expanded context.
     */
    public List<Map<String, Object>> getNeighborChunks(long knowledgeSetId, long documentId,
                                                        long chunkId, int windowSize) {
        List<Map<String, Object>> neighbors = new ArrayList<>();
        try {
            var searcher = getSearcher(knowledgeSetId);
            // Search for chunks from same document
            var docQuery = new TermQuery(new Term("documentId", String.valueOf(documentId)));
            var topDocs = searcher.search(docQuery, 100);

            // Collect all chunks from this document, sorted by chunkId
            List<Map<String, Object>> docChunks = new ArrayList<>();
            for (var scoreDoc : topDocs.scoreDocs) {
                docChunks.add(docToMap(searcher, scoreDoc, "neighbor"));
            }
            docChunks.sort((a, b) -> Long.compare(
                    ((Number) a.get("chunkId")).longValue(),
                    ((Number) b.get("chunkId")).longValue()));

            // Find the target chunk and get window around it
            int targetIdx = -1;
            for (int i = 0; i < docChunks.size(); i++) {
                if (((Number) docChunks.get(i).get("chunkId")).longValue() == chunkId) {
                    targetIdx = i;
                    break;
                }
            }

            if (targetIdx >= 0) {
                int start = Math.max(0, targetIdx - windowSize);
                int end = Math.min(docChunks.size(), targetIdx + windowSize + 1);
                for (int i = start; i < end; i++) {
                    if (i != targetIdx) {
                        neighbors.add(docChunks.get(i));
                    }
                }
            }
            searcher.getIndexReader().close();
        } catch (Exception e) {
            log.debug("Neighbor chunk retrieval failed: {}", e.getMessage());
        }
        return neighbors;
    }

    // ── Stats ────────────────────────────────────────────────────

    public int getDocCount(long knowledgeSetId) {
        try {
            var writer = getWriter(knowledgeSetId);
            var reader = DirectoryReader.open(writer);
            int count = reader.numDocs();
            reader.close();
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    public int getVectorCount(long knowledgeSetId) {
        return getDocCount(knowledgeSetId); // In unified index, doc count = vector count
    }

    // ── Helpers ──────────────────────────────────────────────────

    private Map<String, Object> docToMap(IndexSearcher searcher, ScoreDoc scoreDoc, String searchType) throws IOException {
        var doc = searcher.storedFields().document(scoreDoc.doc);
        Map<String, Object> hit = new LinkedHashMap<>();
        hit.put("chunkId", Long.parseLong(doc.get("chunkId")));
        hit.put("documentId", Long.parseLong(doc.get("documentId")));
        hit.put("title", doc.get("title"));
        hit.put("content", doc.get("content"));
        hit.put("score", scoreDoc.score);
        hit.put("searchType", searchType);
        return hit;
    }

    @PreDestroy
    public void close() {
        for (var writer : writers.values()) {
            try {
                writer.close();
            } catch (IOException e) {
                log.warn("Failed to close index writer", e);
            }
        }
    }
}
