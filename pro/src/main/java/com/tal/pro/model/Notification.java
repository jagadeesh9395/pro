package com.tal.pro.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notifications")
public class Notification {
    @Id
    private String id;
    private String recipientId;
    private String title;
    private String message;
    private NotificationType type;
    private boolean read;
    private LocalDateTime createdAt;
    private String relatedEntityId; // e.g., applicationId or jobId
    private String relatedEntityType; // e.g., "APPLICATION", "JOB"

    public enum NotificationType {
        INFO,
        SUCCESS,
        WARNING,
        ERROR
    }
}
