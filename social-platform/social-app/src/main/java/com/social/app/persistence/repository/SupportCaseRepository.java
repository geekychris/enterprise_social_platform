package com.social.app.persistence.repository;

import com.social.app.persistence.entity.SupportCaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportCaseRepository extends JpaRepository<SupportCaseEntity, Long> {
    List<SupportCaseEntity> findByStatusInOrderByCreatedAtDesc(List<String> statuses);
    List<SupportCaseEntity> findByAssigneeIdAndStatusIn(Long assigneeId, List<String> statuses);
    Optional<SupportCaseEntity> findByCaseNumber(String caseNumber);
    List<SupportCaseEntity> findByAppIdAndStatusIn(Long appId, List<String> statuses);
    long countByStatus(String status);
}
