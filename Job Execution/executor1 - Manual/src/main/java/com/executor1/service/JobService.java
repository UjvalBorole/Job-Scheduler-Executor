package com.executor1.service;

import java.time.LocalDateTime;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import com.executor1.entities1.Job;
import com.executor1.entities1.RedisJobWrapper;
import com.executor1.entities3.JobRun;
import com.executor1.entities4.DepTracker;
import com.executor1.entities4.ExecutionResult;
import com.executor1.entities4.JobStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {


    private final ModifyJob modifyJob;

    private final RedisPriorityQueue redisPriorityQueue;
    private final DepTrackerClient depTrackerClient;
    private final JobRunService jobRunService;
    private final ExecutorService executorService;
    private final CancelReqRepository cancelReqRepository;


    private final NewTopic deptrackerQueueTopic;

    private final KafkaTemplate<String, RedisJobWrapper> redisJobWrapperKafkaTemplate;
    private final KafkaTemplate<String, DepTracker> depTrackerKafkaTemplate;

    LocalDateTime time = LocalDateTime.now();



    /* =========================================================
     * Kafka send helpers
     * ========================================================= */


    private void sendDepTracker(DepTracker tracker) {
        Message<DepTracker> message = MessageBuilder
                .withPayload(tracker)
                .setHeader(KafkaHeaders.TOPIC, deptrackerQueueTopic.name())
                .build();
        depTrackerKafkaTemplate.send(message);
    }

    /* =========================================================
     * Save job run info into DepTracker
     * ========================================================= */
    private void saveJobRun(Job job, JobStatus status, String errorMsg) {
        try {
            JobRun jr = jobRunService.get(job.getId());

            DepTracker tracker = DepTracker.builder()
                    .jobId(String.valueOf(jr.getJobId()))
                    .jobName(job.getName())
                    .jobStatus(status)
                    .startTime(time)
                    .endTime(LocalDateTime.now())
                    .errorMessage(errorMsg)
                    .retryCount(jr.getAttemptNumber())
                    .maxRetries(job.getRetries())
                    .dependencies(job.getDependencies())
                    .retryCount(jr.getAttemptNumber())
                    .scheduleTime(jr.getExecutionTime())
                    .executorId(Integer.toString(2))
                    .build();

            sendDepTracker(tracker);
            log.info("📌 Saved job {} in DepTracker with status {}", job.getId(), status);

            if (status == JobStatus.SUCCESS) {
                redisPriorityQueue.markJobDone(job.getName()); // ✅ mark success in Redis
            }
        } catch (Exception e) {
            log.error("❌ Failed to save DepTracker for job {}: {}", job.getId(), e.getMessage(), e);
        }
    }

    /* =========================================================
     * Execution logic (stub for actual work)
     * ========================================================= */
    private ExecutionResult execute(Job job) {
        log.info("Executing job: {}", job);
        return executorService.execute(job);
    }


    /* =========================================================
     * Kafka Consumers
     * ========================================================= */

    @KafkaListener(
            topics = "${spring.kafka.topic.manual}",
            groupId = "${spring.kafka.topic.manual.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(Job job) {
        /* =========================================================
         * Unified Job Runner
         * ========================================================= */
//        System.out.println(job);
        ExecutionResult result = execute(job);
        saveJobRun(job, result.getStatus(), result.getErrorMessage());
    }
}

