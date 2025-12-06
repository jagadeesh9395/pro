package com.tal.pro.controller;

import com.tal.pro.model.Notification;
import com.tal.pro.repository.CandidateRepository;
import com.tal.pro.repository.NotificationRepository;
import com.tal.pro.repository.RecruiterRepository;
import com.tal.pro.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private RecruiterRepository recruiterRepository;

    @GetMapping
    @ResponseBody
    public ResponseEntity<?> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        String username = principal.getName();
        String userId = null;

        // Try to find candidate
        var candidate = candidateRepository.findByUsername(username);
        if (candidate.isPresent()) {
            userId = candidate.get().getId();
        } else {
            // Try to find recruiter
            var recruiter = recruiterRepository.findByUsername(username);
            if (recruiter.isPresent()) {
                userId = recruiter.get().getId();
            }
        }

        if (userId == null) {
            return ResponseEntity.ok(Map.of("content", List.of(), "totalPages", 0));
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> notifications = notificationRepository.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(
                userId,
                pageable);

        return ResponseEntity.ok(notifications);
    }

    // Let's create a better version that uses the repositories

    @PostMapping("/{id}/read")
    @ResponseBody
    public ResponseEntity<?> markAsRead(@PathVariable String id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unread-count")
    @ResponseBody
    public ResponseEntity<?> getUnreadCount(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        String username = principal.getName();
        String userId = null;

        var candidate = candidateRepository.findByUsername(username);
        if (candidate.isPresent()) {
            userId = candidate.get().getId();
        } else {
            var recruiter = recruiterRepository.findByUsername(username);
            if (recruiter.isPresent()) {
                userId = recruiter.get().getId();
            }
        }

        if (userId == null) {
            return ResponseEntity.ok(Map.of("count", 0));
        }

        long count = notificationRepository.countByRecipientIdAndReadFalse(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping("/mark-all-read")
    @ResponseBody
    public ResponseEntity<?> markAllAsRead(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        String username = principal.getName();
        String userId = null;

        var candidate = candidateRepository.findByUsername(username);
        if (candidate.isPresent()) {
            userId = candidate.get().getId();
        } else {
            var recruiter = recruiterRepository.findByUsername(username);
            if (recruiter.isPresent()) {
                userId = recruiter.get().getId();
            }
        }

        if (userId != null) {
            notificationService.markAllAsRead(userId);
        }

        return ResponseEntity.ok(Map.of("message", "All marked as read"));
    }
}
