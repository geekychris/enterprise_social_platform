package com.aisupport.persistence.repository;

import com.aisupport.persistence.entity.InteractionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InteractionRepository extends JpaRepository<InteractionEntity, Long> {

    List<InteractionEntity> findByKnowledgeSetIdOrderByCreatedAtDesc(Long knowledgeSetId, Pageable pageable);

    long countByKnowledgeSetId(Long knowledgeSetId);

    long countByKnowledgeSetIdAndEscalated(Long knowledgeSetId, boolean escalated);

    long countByKnowledgeSetIdAndHelpful(Long knowledgeSetId, Boolean helpful);
}
