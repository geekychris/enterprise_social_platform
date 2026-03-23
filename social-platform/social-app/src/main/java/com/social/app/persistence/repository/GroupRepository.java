package com.social.app.persistence.repository;

import com.social.app.persistence.entity.GroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<GroupEntity, Long> {

    Optional<GroupEntity> findBySlug(String slug);

    @Query("SELECT g FROM GroupEntity g WHERE LOWER(g.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<GroupEntity> searchByName(String query);

    @Query("SELECT MAX(g.id) FROM GroupEntity g")
    Long findMaxId();
}
