package com.social.app.persistence.repository;

import com.social.app.persistence.entity.GraphEntityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GraphEntityRepository extends JpaRepository<GraphEntityEntity, Long> {

    @Query("SELECT DISTINCT e.entityType FROM GraphEntityEntity e")
    List<String> findDistinctEntityTypes();
}
