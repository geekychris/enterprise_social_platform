package com.social.app.persistence.repository;

import com.social.app.persistence.entity.OrgUnitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrgUnitRepository extends JpaRepository<OrgUnitEntity, Long> {

    List<OrgUnitEntity> findByParentId(Long parentId);

    List<OrgUnitEntity> findByParentIdIsNull();

    List<OrgUnitEntity> findByHeadUserId(Long headUserId);

    List<OrgUnitEntity> findByType(String type);

    @Query("SELECT o FROM OrgUnitEntity o WHERE LOWER(o.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<OrgUnitEntity> searchByName(String query);

    @Query("SELECT MAX(o.id) FROM OrgUnitEntity o")
    Long findMaxId();
}
