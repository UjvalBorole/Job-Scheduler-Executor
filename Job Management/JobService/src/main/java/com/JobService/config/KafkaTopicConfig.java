package com.JobService.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${spring.kafka.topic.job}")
    private String jobTopicName;

    @Value("${spring.kafka.topic.payload}")
    private String payloadTopicName;


    @Bean
    public NewTopic payloadTopic() {
        return TopicBuilder.name(payloadTopicName)
                .config("retention.ms", "3600000") // 1 hour in milliseconds
                .build();
    }


    @Bean
    public NewTopic jobTopic() {
        return TopicBuilder.name(jobTopicName)
                .config("retention.ms", "3600000")
                .build();
    }

}
