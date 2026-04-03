package com.aisupport.persistence.repository;

import com.aisupport.persistence.entity.QATraceEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QATraceRepository extends JpaRepository<QATraceEntity, Long> {
    List<QATraceEntity> findByKnowledgeSetIdOrderByCreatedAtDesc(Long knowledgeSetId, Pageable pageable);
    List<QATraceEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Optional<QATraceEntity> findByInteractionId(Long interactionId);
}
