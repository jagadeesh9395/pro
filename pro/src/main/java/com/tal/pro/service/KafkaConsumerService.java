package com.tal.pro.service;

import com.tal.pro.event.ApplicationStatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = "${kafka.topic.application-status:application-status}", 
                  groupId = "application-status-group")
    public void consumeStatusUpdate(ApplicationStatusEvent event) {
        logger.info("Received status update event: {}", event);
        
        // Forward the event to WebSocket subscribers
        messagingTemplate.convertAndSendToUser(
            event.getCandidateId(),
            "/queue/status-updates",
            event
        );

        messagingTemplate.convertAndSend(
            "/topic/application/" + event.getApplicationId() + "/status-updates",
            event
        );
    }
}
