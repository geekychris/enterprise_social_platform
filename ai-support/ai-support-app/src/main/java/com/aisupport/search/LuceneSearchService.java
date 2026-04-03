package com.aisupport.search;

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

@Service
public class LuceneSearchService {
    private static final Logger log = LoggerFactory.getLogger(LuceneSearchService.class);

    private final Path baseIndexDir;
    private final StandardAnalyzer analyzer = new StandardAnalyzer();
    private final Map<Long, IndexWriter> writers = new ConcurrentHashMap<>();

    public LuceneSearchService(@Value("${aisupport.lucene.index-dir}") String indexDir) {
        this.baseIndexDir = Path.of(indexDir);
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

    /**
     * Index a document chunk.
     */
    public void indexChunk(long knowledgeSetId, long chunkId, long documentId, String title, String content) throws IOException {
        var writer = getWriter(knowledgeSetId);
        var doc = new Document();
        doc.add(new StringField("chunkId", String.valueOf(chunkId), Field.Store.YES));
        doc.add(new StringField("documentId", String.valueOf(documentId), Field.Store.YES));
        doc.add(new TextField("title", title, Field.Store.YES));
        doc.add(new TextField("content", content, Field.Store.YES));
        doc.add(new StoredField("knowledgeSetId", knowledgeSetId));

        // Update or add
        writer.updateDocument(new Term("chunkId", String.valueOf(chunkId)), doc);
        writer.commit();
    }

    /**
     * Delete all chunks for a document.
     */
    public void deleteDocument(long knowledgeSetId, long documentId) throws IOException {
        var writer = getWriter(knowledgeSetId);
        writer.deleteDocuments(new Term("documentId", String.valueOf(documentId)));
        writer.commit();
    }

    /**
     * Search within a knowledge set. Returns list of {chunkId, documentId, title, content, score}.
     */
    public List<Map<String, Object>> search(long knowledgeSetId, String query, int topK) {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            var writer = getWriter(knowledgeSetId);
            var reader = DirectoryReader.open(writer);
            var searcher = new IndexSearcher(reader);

            var parser = new MultiFieldQueryParser(new String[]{"title", "content"}, analyzer);
            parser.setDefaultOperator(QueryParser.Operator.OR);
            var luceneQuery = parser.parse(QueryParser.escape(query));

            var topDocs = searcher.search(luceneQuery, topK);
            for (var scoreDoc : topDocs.scoreDocs) {
                var doc = searcher.storedFields().document(scoreDoc.doc);
                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("chunkId", Long.parseLong(doc.get("chunkId")));
                hit.put("documentId", Long.parseLong(doc.get("documentId")));
                hit.put("title", doc.get("title"));
                hit.put("content", doc.get("content"));
                hit.put("score", scoreDoc.score);
                results.add(hit);
            }
            reader.close();
        } catch (Exception e) {
            log.warn("Lucene search failed for ks-{}: {}", knowledgeSetId, e.getMessage());
        }
        return results;
    }

    /**
     * Get total document count for a knowledge set index.
     */
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

    @PreDestroy
    public void close() {
        for (var writer : writers.values()) {
            try { writer.close(); } catch (IOException e) { log.warn("Failed to close index writer", e); }
        }
    }
}
