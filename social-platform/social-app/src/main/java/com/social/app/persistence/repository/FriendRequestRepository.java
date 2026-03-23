package com.social.app.persistence.repository;

import com.social.app.persistence.entity.FriendRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendRequestRepository extends JpaRepository<FriendRequestEntity, Long> {

    List<FriendRequestEntity> findByReceiverIdAndStatus(Long receiverId, String status);

    List<FriendRequestEntity> findBySenderIdAndStatus(Long senderId, String status);

    Optional<FriendRequestEntity> findBySenderIdAndReceiverId(Long senderId, Long receiverId);

    boolean existsBySenderIdAndReceiverIdAndStatus(Long senderId, Long receiverId, String status);

    boolean existsBySenderIdAndReceiverId(Long senderId, Long receiverId);
}
