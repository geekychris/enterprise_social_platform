package com.social.app.persistence.repository;

import com.social.app.persistence.entity.TeamEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<TeamEntity, Long> {

    Optional<TeamEntity> findBySlug(String slug);

    @Query("SELECT MAX(t.id) FROM TeamEntity t")
    Long findMaxId();
}
