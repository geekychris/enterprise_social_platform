package com.aisupport.persistence.repository;

import com.aisupport.persistence.entity.CapturedSolutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CapturedSolutionRepository extends JpaRepository<CapturedSolutionEntity, Long> {

    List<CapturedSolutionEntity> findByStatusOrderByCreatedAtDesc(String status);

    List<CapturedSolutionEntity> findByKnowledgeSetIdAndStatusOrderByCreatedAtDesc(Long knowledgeSetId, String status);

    long countByStatus(String status);
}
