package com.aisupport.persistence.repository;

import com.aisupport.persistence.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {

    List<DocumentEntity> findByKnowledgeSetId(Long knowledgeSetId);

    Optional<DocumentEntity> findByContentHash(String contentHash);

    long countByKnowledgeSetId(Long knowledgeSetId);

    List<DocumentEntity> findByKnowledgeSetIdAndIndexedFalse(Long knowledgeSetId);
}
