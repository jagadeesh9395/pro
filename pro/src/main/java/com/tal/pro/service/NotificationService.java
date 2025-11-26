package com.tal.pro.service;

import com.tal.pro.model.Notification;
import com.tal.pro.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JavaMailSender emailSender;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Value("${spring.mail.username:noreply@talpro.com}")
    private String fromEmail;

    public void sendNotification(String recipientId, String recipientEmail, String title, String message,
            Notification.NotificationType type, String relatedEntityId, String relatedEntityType) {

        // 1. Save persistent notification
        Notification notification = new Notification();
        notification.setRecipientId(recipientId);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setRelatedEntityId(relatedEntityId);
        notification.setRelatedEntityType(relatedEntityType);

        notificationRepository.save(notification);

        // 2. Send Real-time WebSocket notification
        try {
            messagingTemplate.convertAndSendToUser(
                    recipientId,
                    "/queue/notifications",
                    notification);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification to user {}", recipientId, e);
        }

        // 3. Send Email
        if (recipientEmail != null && !recipientEmail.isEmpty()) {
            sendEmail(recipientEmail, title, message);
        }
    }

    private void sendEmail(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            emailSender.send(message);
            log.info("Email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            // Don't throw exception to avoid breaking the main flow
        }
    }

    public void markAsRead(String notificationId) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            notification.setRead(true);
            notificationRepository.save(notification);
        });
    }
}
