package com.executor1.config;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {



    @Value("${spring.kafka.topic.deptrackerqueue}")
    private String deptrackerTopicName;

    @Value("${spring.kafka.topic.payloadqueue}")
    private String payloadTopicName;

    @Bean
    public NewTopic deptrackerQueueTopic() {
        return TopicBuilder.name(deptrackerTopicName)
                .partitions(5) // you said 5 partitions for each queue
                .replicas(1)   // adjust replicas depending on cluster setup
                .config("retention.ms", "3600000") // 1 hour retention
                .build();
    }

    @Bean
    public NewTopic payloadQueueTopic() {
        return TopicBuilder.name(payloadTopicName)
                .partitions(5) // you said 5 partitions for each queue
                .replicas(1)   // adjust replicas depending on cluster setup
                .config("retention.ms", "3600000") // 1 hour retention
                .build();
    }



}
