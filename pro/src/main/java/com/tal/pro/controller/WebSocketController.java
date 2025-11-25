package com.tal.pro.controller;

import com.tal.pro.event.ApplicationStatusEvent;
import com.tal.pro.model.JobApplication;
import com.tal.pro.service.JobApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private JobApplicationService jobApplicationService;

    @MessageMapping("/application/status/update")
    public void updateApplicationStatus(@Payload ApplicationStatusEvent event) {
        // Update the application status
        JobApplication application = jobApplicationService.updateApplicationStatus(
            event.getApplicationId(),
            event.getNewStatus(),
            event.getNotes(),
            event.getUpdatedBy()
        );

        // Notify the candidate
        messagingTemplate.convertAndSendToUser(
            event.getCandidateId(),
            "/queue/status-updates",
            event
        );

        // Notify the recruiter
        messagingTemplate.convertAndSend(
            "/topic/recruiter/" + application.getJob().getPostedBy() + "/applications/status-updates",
            event
        );
    }

    @SubscribeMapping("/topic/application/{applicationId}/status")
    public void subscribeToApplicationStatus(String applicationId) {
        // Subscription handling is automatic
    }
}
