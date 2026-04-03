package com.aisupport.persistence.repository;

import com.aisupport.persistence.entity.DocumentChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, Long> {

    List<DocumentChunkEntity> findByDocumentId(Long documentId);

    List<DocumentChunkEntity> findByKnowledgeSetId(Long knowledgeSetId);

    void deleteByDocumentId(Long documentId);

    long countByKnowledgeSetId(Long knowledgeSetId);
}
