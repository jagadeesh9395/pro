package com.tal.pro.repository;

import com.tal.pro.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId);

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId, Pageable pageable);

    long countByRecipientIdAndReadFalse(String recipientId);

    List<Notification> findByRecipientIdAndReadFalse(String recipientId);

    Page<Notification> findByRecipientIdAndReadFalseOrderByCreatedAtDesc(String recipientId, Pageable pageable);
}
