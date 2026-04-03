package com.aisupport.persistence.repository;

import com.aisupport.persistence.entity.CrawlJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CrawlJobRepository extends JpaRepository<CrawlJobEntity, Long> {

    List<CrawlJobEntity> findByKnowledgeSetIdOrderByCreatedAtDesc(Long knowledgeSetId);

    List<CrawlJobEntity> findByStatus(String status);
}
