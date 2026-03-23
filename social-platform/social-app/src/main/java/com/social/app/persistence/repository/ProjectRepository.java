package com.social.app.persistence.repository;

import com.social.app.persistence.entity.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {

    Optional<ProjectEntity> findBySlug(String slug);

    @Query("SELECT MAX(p.id) FROM ProjectEntity p")
    Long findMaxId();
}
