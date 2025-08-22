package com.executor1.service;

import com.executor1.entities1.RedisJobWrapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class JobService {
    @KafkaListener(
            topics = "${spring.kafka.topic.run}",
            containerFactory = "runQueueKafkaListenerContainerFactory"
    )
    public void consumeRunQueue(RedisJobWrapper event) {
        System.out.println("RunQueue Job: " + event);
    }

    @KafkaListener(
            topics = "${spring.kafka.topic.waitqueue}",
            containerFactory = "waitQueueKafkaListenerContainerFactory"
    )
    public void consumeWaitQueue(RedisJobWrapper event) {
        System.out.println("WaitQueue Job: " + event);
    }

    @KafkaListener(
            topics = "${spring.kafka.topic.retryqueue}",
            containerFactory = "retryQueueKafkaListenerContainerFactory"
    )
    public void consumeRetryQueue(RedisJobWrapper event) {

        System.out.println("RetryQueue Job: " + event);
    }
}
