package com.social.app.persistence.repository;

import com.social.app.persistence.entity.GraphEdgeEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

@Repository
public interface GraphEdgeRepository extends JpaRepository<GraphEdgeEntity, Long> {

    @Query("SELECT DISTINCT e.edgeType FROM GraphEdgeEntity e")
    List<String> findDistinctEdgeTypes();

    List<GraphEdgeEntity> findBySrcIdAndEdgeType(long srcId, String edgeType);

    List<GraphEdgeEntity> findBySrcIdAndEdgeType(long srcId, String edgeType, Pageable pageable);

    Optional<GraphEdgeEntity> findBySrcIdAndEdgeTypeAndDstId(long srcId, String edgeType, long dstId);

    boolean existsBySrcIdAndEdgeTypeAndDstId(long srcId, String edgeType, long dstId);

    long countBySrcIdAndEdgeType(long srcId, String edgeType);

    @Transactional
    void deleteBySrcIdAndEdgeTypeAndDstId(long srcId, String edgeType, long dstId);
}
