package com.social.app.persistence.repository;

import com.social.app.persistence.entity.PageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Long> {

    Optional<PageEntity> findBySlug(String slug);

    @Query("SELECT p FROM PageEntity p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<PageEntity> searchByName(String query);

    @Query("SELECT MAX(p.id) FROM PageEntity p")
    Long findMaxId();
}
