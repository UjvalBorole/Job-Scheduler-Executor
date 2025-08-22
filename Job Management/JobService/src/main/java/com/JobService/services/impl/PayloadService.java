package com.JobService.services.impl;

import com.JobService.entities2.Payload;
import com.JobService.entities2.PayloadEvent;
import com.JobService.entities2.PayloadEventType;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
public class PayloadService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PayloadService.class);

//    #########################################################kafka##################################
    private final NewTopic payloadTopic;
    private final KafkaTemplate<String, PayloadEvent> kafkaTemplate;

    public PayloadService(NewTopic payloadTopic, KafkaTemplate<String, PayloadEvent> kafkaTemplate) {
        this.payloadTopic = payloadTopic;
        this.kafkaTemplate = kafkaTemplate;
    }

    private void sendEvent(PayloadEvent event) {
        Message<PayloadEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, payloadTopic.name())
                .build();
        kafkaTemplate.send(message);
    }

//    #########################################################################################

    public boolean create(Payload payload, String jobId) {
        if (jobId == null || jobId.trim().isEmpty()) {
            LOGGER.error("❌ Cannot create payload: jobId is null or empty.");
            return false;
        }

        try {
            payload.setJobId(Long.valueOf(jobId));
        } catch (NumberFormatException e) {
            LOGGER.error("❌ Invalid jobId format: '{}'. Must be a numeric value.", jobId);
            return false;
        }

        PayloadEvent event = PayloadEvent.builder()
                .eventType(PayloadEventType.CREATE)
                .payload(payload)
                .build();
        sendEvent(event);
        LOGGER.info("📤 Sending CREATE Payload event to Kafka: {}", event);
        return true;
    }

    public boolean update(Payload payload, String id) {
        PayloadEvent event = PayloadEvent.builder()
                .eventType(PayloadEventType.MOD)
                .payload(payload)
                .payloadId(id)
                .build();
        LOGGER.info("Sending UPDATE Payload event to Kafka: {}", event);
        sendEvent(event);
        return true;
    }

    public boolean delete(String payloadId) {
        PayloadEvent event = PayloadEvent.builder()
                .eventType(PayloadEventType.DELETE)
                .payloadId(payloadId)
                .build();
        LOGGER.info("Sending DELETE Payload event to Kafka: {}", event);
        sendEvent(event);
        return true;
    }
}
