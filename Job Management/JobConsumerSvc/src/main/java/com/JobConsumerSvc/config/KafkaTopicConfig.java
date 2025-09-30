package com.JobConsumerSvc.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${spring.kafka.topic.manual}")
    private String scheduledJobsTopicName;

    @Bean
    public NewTopic scheduledJobsTopic() {
        return TopicBuilder.name(scheduledJobsTopicName)
                .config("retention.ms", "3600000") // 1 hour retention
                .build();
    }
}