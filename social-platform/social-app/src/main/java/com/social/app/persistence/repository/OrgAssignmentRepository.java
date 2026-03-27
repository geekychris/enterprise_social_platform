package com.social.app.persistence.repository;

import com.social.app.persistence.entity.OrgAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrgAssignmentRepository extends JpaRepository<OrgAssignmentEntity, Long> {

    List<OrgAssignmentEntity> findByUserId(Long userId);

    List<OrgAssignmentEntity> findByOrgUnitId(Long orgUnitId);

    List<OrgAssignmentEntity> findByReportsToUserId(Long reportsToUserId);

    List<OrgAssignmentEntity> findByUserIdAndRelationshipType(Long userId, String relationshipType);

    List<OrgAssignmentEntity> findByOrgUnitIdAndRelationshipType(Long orgUnitId, String relationshipType);

    @Query("SELECT MAX(a.id) FROM OrgAssignmentEntity a")
    Long findMaxId();

    void deleteByUserIdAndOrgUnitId(Long userId, Long orgUnitId);
}
