package com.aisupport.persistence.repository;

import com.aisupport.persistence.entity.MetricEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MetricRepository extends JpaRepository<MetricEntity, Long> {

    List<MetricEntity> findByKnowledgeSetIdAndMetricTypeOrderByRecordedAtDesc(Long knowledgeSetId, String metricType, Pageable pageable);
}
