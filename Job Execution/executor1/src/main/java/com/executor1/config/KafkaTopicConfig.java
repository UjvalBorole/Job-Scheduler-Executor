package com.executor1.config;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${spring.kafka.topic.run}")
    private String runQueueTopicName;

    @Value("${spring.kafka.topic.waitqueue}")
    private String waitQueueTopicName;

    @Value("${spring.kafka.topic.retryqueue}")
    private String retryQueueTopicName;

    @Value("${spring.kafka.topic.deptrackerqueue}")
    private String deptrackerTopicName;

    @Bean
    public NewTopic deptrackerQueueTopic() {
        return TopicBuilder.name(deptrackerTopicName)
                .partitions(5) // you said 5 partitions for each queue
                .replicas(1)   // adjust replicas depending on cluster setup
                .config("retention.ms", "3600000") // 1 hour retention
                .build();
    }
    @Bean
    public NewTopic runQueueTopic() {
        return TopicBuilder.name(runQueueTopicName)
                .partitions(5) // you said 5 partitions for each queue
                .replicas(1)   // adjust replicas depending on cluster setup
                .config("retention.ms", "3600000") // 1 hour retention
                .build();
    }

    @Bean
    public NewTopic waitQueueTopic() {
        return TopicBuilder.name(waitQueueTopicName)
                .partitions(5)
                .replicas(1)
                .config("retention.ms", "3600000")
                .build();
    }

    @Bean
    public NewTopic retryQueueTopic() {
        return TopicBuilder.name(retryQueueTopicName)
                .partitions(5)
                .replicas(1)
                .config("retention.ms", "3600000")
                .build();
    }
}
