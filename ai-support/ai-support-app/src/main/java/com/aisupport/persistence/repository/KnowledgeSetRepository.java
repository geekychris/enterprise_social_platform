package com.aisupport.persistence.repository;

import com.aisupport.persistence.entity.KnowledgeSetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KnowledgeSetRepository extends JpaRepository<KnowledgeSetEntity, Long> {

    Optional<KnowledgeSetEntity> findBySlug(String slug);

    Optional<KnowledgeSetEntity> findBySocialPageId(Long pageId);
}
