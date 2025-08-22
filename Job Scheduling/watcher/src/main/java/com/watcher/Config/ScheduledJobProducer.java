package com.watcher.Config;

import com.watcher.entities1.RedisJobWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
public class ScheduledJobProducer {

    private final KafkaTemplate<String, RedisJobWrapper> kafkaTemplate;

    @Value("${spring.kafka.topic.name}")
    private String scheduledJobsTopic;

    public ScheduledJobProducer(KafkaTemplate<String, RedisJobWrapper> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendEvent(RedisJobWrapper event) {
        Message<RedisJobWrapper> message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, scheduledJobsTopic)
                .build();

        kafkaTemplate.send(message);
    }
}
