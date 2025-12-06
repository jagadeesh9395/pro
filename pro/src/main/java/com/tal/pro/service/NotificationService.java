package com.tal.pro.service;

import com.tal.pro.model.Notification;
import com.tal.pro.repository.NotificationRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
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

    @Autowired
    private org.thymeleaf.TemplateEngine templateEngine;

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
            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);

            // Prepare the evaluation context
            org.thymeleaf.context.Context context = new org.thymeleaf.context.Context();
            context.setVariable("title", subject);
            // Convert newlines to <br> for the message body if it's plain text
            String formattedMessage = text.replace("\n", "<br>");
            context.setVariable("message", formattedMessage);
            // context.setVariable("actionUrl", "http://localhost:8080/dashboard"); //
            // Optional: Add action URL

            // Create the HTML body using Thymeleaf
            String htmlContent = templateEngine.process("email/notification-email", context);

            helper.setText(htmlContent, true);

            log.info("Sending HTML email to: {}, Subject: {}", to, subject);
            emailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            // Don't throw exception to avoid rolling back transaction if email fails
            // throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * Sends a job application confirmation email to the candidate
     *
     * @param candidateEmail Candidate's email address
     * @param candidateName  Candidate's full name
     * @param jobTitle       Job title applied for
     * @param companyName    Company name
     */
    public void sendJobApplicationConfirmation(String candidateId, String candidateEmail, String candidateName,
            String jobTitle, String companyName) {
        if (candidateEmail == null || candidateEmail.trim().isEmpty()) {
            log.warn("Cannot send job application confirmation: candidate email is empty");
            return;
        }

        String subject = "Application Received - " + jobTitle + " at " + companyName;

        String message = "Dear " + candidateName + ",<br><br>" +
                "Thank you for applying for the <strong>" + jobTitle + "</strong> position at <strong>" + companyName
                + "</strong>. " +
                "We have received your application and our team will review it carefully.<br><br>" +
                "<h3>Application Details:</h3>" +
                "<ul>" +
                "<li><strong>Position:</strong> " + jobTitle + "</li>" +
                "<li><strong>Company:</strong> " + companyName + "</li>" +
                "</ul>" +
                "<p>We will contact you if your qualifications match our requirements. " +
                "This process may take up to 2 weeks.</p>" +
                "<p>Best regards,<br>" +
                "The " + companyName + " Team</p>";

        // Send persistent notification
        sendNotification(candidateId, candidateEmail, subject,
                "Application received for " + jobTitle + " at " + companyName,
                Notification.NotificationType.INFO, null, "APPLICATION");
    }

    public void notifyRecruiterNewApplication(String recruiterId, String recruiterEmail, String recruiterName,
            String candidateName, String candidateEmail,
            String jobTitle, String jobId, String applicationId) {
        String subject = String.format("New Application for %s", jobTitle);
        String message = String.format(""
                + "Hello %s,<br><br>"
                + "You have received a new application for the position: <strong>%s</strong>.<br><br>"
                + "<strong>Candidate:</strong> %s<br>"
                + "<strong>Email:</strong> %s<br>"
                + "<strong>Position:</strong> %s<br>"
                + "<strong>Job ID:</strong> %s<br>"
                + "<strong>Application ID:</strong> %s<br><br>"
                + "Please log in to your Talent Trove recruiter dashboard to review this application.<br><br>"
                + "Best regards,<br>"
                + "Talent Trove Team",
                recruiterName, jobTitle, candidateName, candidateEmail, jobTitle, jobId, applicationId);

        // Send persistent notification
        sendNotification(recruiterId, recruiterEmail, subject,
                "New application received for " + jobTitle + " from " + candidateName,
                Notification.NotificationType.INFO, applicationId, "APPLICATION");
    }

    public void markAsRead(String notificationId) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            notification.setRead(true);
            notificationRepository.save(notification);
        });
    }

    public void markAllAsRead(String recipientId) {
        List<Notification> unreadNotifications = notificationRepository.findByRecipientIdAndReadFalse(recipientId);
        if (!unreadNotifications.isEmpty()) {
            unreadNotifications.forEach(n -> n.setRead(true));
            notificationRepository.saveAll(unreadNotifications);
        }
    }
}
