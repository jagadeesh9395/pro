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

    /**
     * Converts plain text to HTML format for email content
     * @param text Plain text to convert
     * @return HTML formatted text
     */
    private String convertTextToHtml(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        // Convert line breaks to <br> and escape HTML special characters
        return "<html><body>" + 
               text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\n", "<br>") +
               "</body></html>";
    }

    private void sendEmail(String to, String subject, String text) {
        try {
            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);

            // Convert plain text to HTML with basic formatting
            String htmlContent = convertTextToHtml(text);
            helper.setText(htmlContent, true);

            log.info("Sending HTML email to: {}, Subject: {}", to, subject);
            emailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send email", e);
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
    public void sendJobApplicationConfirmation(String candidateEmail, String candidateName,
                                               String jobTitle, String companyName) {
        if (candidateEmail == null || candidateEmail.trim().isEmpty()) {
            log.warn("Cannot send job application confirmation: candidate email is empty");
            return;
        }

        String subject = "Application Received - " + jobTitle + " at " + companyName;
        
        String message = "Dear " + candidateName + ",\n\n" +
                "Thank you for applying for the " + jobTitle + " position at " + companyName + ". " +
                "We have received your application and our team will review it carefully.\n\n" +
                "Application Details:\n" +
                "- Position: " + jobTitle + "\n" +
                "- Company: " + companyName + "\n\n" +
                "We will contact you if your qualifications match our requirements. " +
                "This process may take up to 2 weeks.\n\n" +
                "Best regards,\n" +
                "The " + companyName + " Team";
                
        // Convert plain text to HTML
        String html = "<div style=\"font-family: Arial, sans-serif; line-height: 1.6;\">" +
                "<p>Dear " + candidateName + ",</p>" +
                "<p>Thank you for applying for the <strong>" + jobTitle + "</strong> position at <strong>" + companyName + "</strong>. " +
                "We have received your application and our team will review it carefully.</p>" +
                "<h3>Application Details:</h3>" +
                "<ul>" +
                "<li><strong>Position:</strong> " + jobTitle + "</li>" +
                "<li><strong>Company:</strong> " + companyName + "</li>" +
                "</ul>" +
                "<p>We will contact you if your qualifications match our requirements. " +
                "This process may take up to 2 weeks.</p>" +
                "<p>Best regards,<br>" +
                "The " + companyName + " Team</p>" +
                "</div>" +
                "<div style=\"color: #666; font-size: 0.9em; border-top: 1px solid #eee; padding-top: 10px; margin-top: 20px;\">" +
                "<p>This is an automated message, please do not reply to this email.</p>" +
                "<p>© 2023 Talent Trove. All rights reserved.</p>" +
                "</div>";

        try {
            sendEmail(candidateEmail, subject, html);
            log.info("Job application confirmation sent to: {}", candidateEmail);
        } catch (Exception e) {
            log.error("Failed to send job application confirmation to: " + candidateEmail, e);
        }
    }

    public void notifyRecruiterNewApplication(String recruiterEmail, String recruiterName, 
                                            String candidateName, String candidateEmail,
                                            String jobTitle, String jobId, String applicationId) {
        String subject = String.format("New Application for %s", jobTitle);
        String message = String.format(""
                        + "Hello %s,\n\n"
                        + "You have received a new application for the position: %s.\n\n"
                        + "Candidate: %s\n"
                        + "Email: %s\n"
                        + "Position: %s\n"
                        + "Job ID: %s\n"
                        + "Application ID: %s\n\n"
                        + "Please log in to your Talent Trove recruiter dashboard to review this application.\n\n"
                        + "Best regards,\n"
                        + "Talent Trove Team",
                recruiterName, jobTitle, candidateName, candidateEmail, jobTitle, jobId, applicationId);

        sendEmail(recruiterEmail, subject, message);
    }

    public void markAsRead(String notificationId) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            notification.setRead(true);
            notificationRepository.save(notification);
        });
    }
}
