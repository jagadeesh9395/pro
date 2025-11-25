package com.tal.pro.service;

import com.tal.pro.event.ApplicationStatusEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducerService {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.application-status:application-status}")
    private String applicationStatusTopic;

    public void sendStatusUpdate(ApplicationStatusEvent event) {
        kafkaTemplate.send(applicationStatusTopic, event);
    }
}
