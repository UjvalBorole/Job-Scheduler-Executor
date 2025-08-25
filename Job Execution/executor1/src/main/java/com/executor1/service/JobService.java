package com.executor1.service;

import com.executor1.config.DepTrackerClient;
import com.executor1.config.RedisPriorityQueue;
import com.executor1.entities1.Job;
import com.executor1.entities1.RedisJobWrapper;
import com.executor1.entities4.DepTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final RedisPriorityQueue redisPriorityQueue;

    @Autowired
    private DepTrackerClient depTrackerClient;

    @KafkaListener(
            topics = "${spring.kafka.topic.run}",
            containerFactory = "runQueueKafkaListenerContainerFactory"
    )
    public void consumeRunQueue(RedisJobWrapper event) {
        log.info("📥 Consumed RunQueue Job: {}", event);

        Job job = event.getJob();
        if (job == null) {
            log.warn("⚠️ Received null job in event {}", event);
            return;
        }

        // ✅ Case 1: No dependencies → proceed directly
        if (job.getDependencies() == null || job.getDependencies().isEmpty()) {
            log.info("✅ Job {} has no dependencies → proceeding directly", job.getId());
            // TODO: call your job execution logic here
            return;
        }

        // ✅ Case 2: Job has dependencies
        for (String dependency : job.getDependencies()) {
            // 1. Check in Redis
            boolean presentInRedis = redisPriorityQueue.isDependencyPresent(dependency);

            if (presentInRedis) {
                redisPriorityQueue.addJobToDependency(
                        dependency,
                        String.valueOf(job.getId()),
                        event.getTime()
                );
                log.info("📌 Job {} added to Redis under dependency {}", job.getId(), dependency);
            } else {
                // 2. Not in Redis → check Mongo by jobName

                DepTracker  depTracker = depTrackerClient.findFirstByJobName(dependency);

                if (depTracker != null) {
                    log.info("✅ Dependency {} found in MongoDB (jobName)", dependency);
                    // TODO: proceed with execution logic since dependency exists in DB
                } else {
                    // 3. Not in Redis or Mongo → push into Redis waiting queue
                    log.warn("⚠️ Dependency {} not found → pushing {} into Redis waiting queue", dependency, job.getId());

                    redisPriorityQueue.addJobToDependency(
                            dependency,
                            String.valueOf(job.getId()),
                            event.getTime()
                    );
                }
            }
        }
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
