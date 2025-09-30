package com.JobConsumerSvc.config;

import com.JobConsumerSvc.dto.JobDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
public class ManualJobProducer {

    private final KafkaTemplate<String, JobDTO> kafkaTemplate;

    @Value("${spring.kafka.topic.manual}")
    private String scheduledJobsTopic;

    public ManualJobProducer(KafkaTemplate<String, JobDTO> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendEvent(JobDTO event) {
        Message<JobDTO> message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, scheduledJobsTopic)
                .build();

        kafkaTemplate.send(message);
    }
}
