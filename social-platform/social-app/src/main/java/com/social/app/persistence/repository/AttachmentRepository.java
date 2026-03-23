package com.social.app.persistence.repository;

import com.social.app.persistence.entity.AttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttachmentRepository extends JpaRepository<AttachmentEntity, Long> {

    Optional<AttachmentEntity> findByContentHash(String contentHash);

    List<AttachmentEntity> findByOwnerId(Long ownerId);

    @Query("SELECT a FROM AttachmentEntity a WHERE a.id IN :ids ORDER BY a.id")
    List<AttachmentEntity> findByIdIn(List<Long> ids);

    @Query("SELECT MAX(a.id) FROM AttachmentEntity a")
    Long findMaxId();
}
