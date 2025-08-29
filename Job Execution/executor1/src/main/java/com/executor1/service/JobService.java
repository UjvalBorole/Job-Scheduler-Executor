//package com.executor1.service;
//
//import com.executor1.config.DepTrackerClient;
//import com.executor1.config.RedisPriorityQueue;
//import com.executor1.entities1.Job;
//import com.executor1.entities1.RedisJobWrapper;
//import com.executor1.entities1.TaskStatus;
//import com.executor1.entities3.JobRun;
//import com.executor1.entities4.DepTracker;
//import com.executor1.entities4.DependentJobGroup;
//import com.executor1.entities4.ExecutionResult;
//import com.executor1.entities4.JobStatus;
//import com.executor1.utility.CronMetadataExtractor;
//import com.executor1.utility.ModifyJob;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.kafka.clients.admin.NewTopic;
//import org.springframework.beans.BeanUtils;
//import org.springframework.kafka.annotation.KafkaListener;
//import org.springframework.kafka.core.KafkaTemplate;
//import org.springframework.kafka.support.KafkaHeaders;
//import org.springframework.messaging.Message;
//import org.springframework.messaging.support.MessageBuilder;
//import org.springframework.stereotype.Service;
//
//import java.time.LocalDateTime;
//import java.util.*;
//import java.util.stream.Collectors;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class JobService {
//
//    private final CronMetadataExtractor cronMetadataExtractor;
//    private final ModifyJob modifyJob;
//
//    private final RedisPriorityQueue redisPriorityQueue;
//    private final DepTrackerClient depTrackerClient;
//    private final JobRunService jobRunService;
//
//    private final NewTopic runQueueTopic;
//    private final NewTopic waitQueueTopic;
//    private final NewTopic retryQueueTopic;
//    private final NewTopic deptrackerQueueTopic;
//
//    private final KafkaTemplate<String, RedisJobWrapper> redisJobWrapperKafkaTemplate;
//    private final KafkaTemplate<String, DepTracker> depTrackerKafkaTemplate;
//
//    /* =========================================================
//     * Kafka produce helpers
//     * ========================================================= */
//    private void sendEvent(RedisJobWrapper event, NewTopic topic) {
//        Message<RedisJobWrapper> message = MessageBuilder
//                .withPayload(event)
//                .setHeader(KafkaHeaders.TOPIC, topic.name())
//                .build();
//        redisJobWrapperKafkaTemplate.send(message);
//    }
//
//    private void sendDepTracker(DepTracker event) {
//        Message<DepTracker> message = MessageBuilder
//                .withPayload(event)
//                .setHeader(KafkaHeaders.TOPIC, deptrackerQueueTopic.name())
//                .build();
//        depTrackerKafkaTemplate.send(message);
//    }
//
//    /* =========================================================
//     * DepTracker persistence
//     * ========================================================= */
//    private void saveJobRunToDepTracker(Job job, JobStatus status) {
//        try {
//            JobRun jr = jobRunService.get(job.getId());
//            log.info("📝 Building DepTracker for JobId={} name={} → status={}",
//                    job.getId(), job.getName(), status);
//
//
//            DepTracker  depTracker = DepTracker.builder()
//                        .jobId(String.valueOf(jr.getJobId()))
//                        .jobName(job.getName())
//                        .jobStatus(status)
//                        .endTime(LocalDateTime.now())
//                        .startTime(jr.getStartTime())
//                        .errorMessage(status == JobStatus.SUCCESS?jr.getErrorMsg():job.getMeta())
//                        .maxRetries(job.getRetries())
//                        .dependencies(job.getDependencies())
//                        .retryCount(jr.getAttemptNumber())
//                        .scheduleTime(jr.getExecutionTime())
//                        .executorId("1")
//                        .build();
//
//
//            if(depTracker != null)sendDepTracker(depTracker);
//            log.info("✅ DepTracker sent for jobId={}", depTracker.getJobId());
//
//            // Mark successful jobs into Redis (dependency satisfied memory)
//            if (status == JobStatus.SUCCESS) {
//                redisPriorityQueue.markJobDone(job.getName());
//            }
//
//        } catch (Exception e) {
//            log.error("❌ Failed to persist DepTracker for jobId={} name={}: {}",
//                    job.getId(), job.getName(), e.getMessage(), e);
//        }
//    }
//
//    /* =========================================================
//     * Retry helpers
//     * ========================================================= */
//    private void handleRetry(RedisJobWrapper event, Job job) {
//        int retriesLeft = job.getRetries() - 1;
//        if (retriesLeft > 0) {
//            Job retryJob = new Job();
//            BeanUtils.copyProperties(job, retryJob);
//            retryJob.setRetries(retriesLeft);
//
//            event.setJob(retryJob);
//            sendEvent(event, retryQueueTopic);
//
//            log.info("🔄 Job {} failed → retry queued (remaining retries={})",
//                    job.getId(), retriesLeft);
//        } else {
//            log.error("⛔ Job {} failed and retries exhausted. Mark FAILED.", job.getId());
//            saveJobRunToDepTracker(job, JobStatus.FAILED);
//        }
//    }
//
//    /* =========================================================
//     * Dependency gating
//     * ========================================================= */
//    private void queueIntoRedisWaiting(String depName, RedisJobWrapper event) {
//        redisPriorityQueue.addJobToDependency(
//                depName,
//                String.valueOf(event.getJob().getId()),
//                event.getTime(),
//                event.getJob().getRetries()
//        );
//        log.info("📌 Queued job {} under dependency '{}'",
//                event.getJob().getId(), depName);
//    }
//
//    /* =========================================================
//     * Execution stub
//     * ========================================================= */
//    private ExecutionResult execute(Job job) {
//        // Replace with actual execution
//        log.info("▶️ Executing job {} name={}", job.getId(), job.getName());
//        ExecutionResult res = ExecutionResult.builder().status(JobStatus.RETRYING).errorMessage("xyz").build();
//        return res;
//    }
//
//    /* =========================================================
//     * Kafka Consumers
//     * ========================================================= */
//    @KafkaListener(
//            topics = "${spring.kafka.topic.run}",
//            containerFactory = "runQueueKafkaListenerContainerFactory"
//    )
//    public void consumeRunQueue(RedisJobWrapper event) {
//        log.info("▶️ Consuming from RunQueue: job {}", event.getJob() != null ? event.getJob().getId() : null);
//        handleJobEvent(event);
//    }
//
//    @KafkaListener(
//            topics = "${spring.kafka.topic.waitqueue}",
//            containerFactory = "waitQueueKafkaListenerContainerFactory"
//    )
//    public void consumeWaitQueue(RedisJobWrapper event) {
//        log.info("↪️ Consuming from WaitQueue: job {}", event.getJob() != null ? event.getJob().getId() : null);
//        handleJobEvent(event);
//    }
//
//    @KafkaListener(
//            topics = "${spring.kafka.topic.retryqueue}",
//            containerFactory = "retryQueueKafkaListenerContainerFactory"
//    )
////    public void consumeRetryQueue(RedisJobWrapper event) {
////        Job job = event.getJob();
////        if (job == null) return;
////
////        if (job.getRetries() <= 0) {
////            saveJobRunToDepTracker(job, JobStatus.FAILED);
////            return;
////        }
////        log.info("♻️ RetryQueue → RunQueue for job {} (retries={})", job.getId(), job.getRetries());
////        sendEvent(event, runQueueTopic);
////    }
//
//    @KafkaListener(
//            topics = "${spring.kafka.topic.retryqueue}",
//            containerFactory = "retryQueueKafkaListenerContainerFactory"
//    )
//    public void consumeRetryQueue(RedisJobWrapper event) {
//        Job job = event.getJob();
//        if (job == null) return;
//
//        if (job.getRetries() <= 0) {
//            log.error("⛔ Job {} retries exhausted, marking FAILED.", job.getId());
//            saveJobRunToDepTracker(job, JobStatus.FAILED);
//            modifyJob.updateJobAndRun(job.getId(), TaskStatus.CANCELLED, "Retries exhausted");
//            return;
//        }
//
//        log.info("♻️ Retrying job {} (remaining retries={})", job.getId(), job.getRetries());
//
//        // Execute job again
//        ExecutionResult result = execute(job);
//
//        if (result.getStatus() == JobStatus.SUCCESS) {
//            // ✅ Success → save + release dependents
//            saveJobRunToDepTracker(job, JobStatus.SUCCESS);
//            releaseWaitingDependents(job);
//            setStatusAfterChk(job);
//        } else {
//            // ❌ Failure
//            int retriesLeft = job.getRetries() - 1;
//
//
//
//            if (retriesLeft > 0) {
//                // requeue with decremented retries
//                Job retryJob = new Job();
//                BeanUtils.copyProperties(job, retryJob);
//                retryJob.setRetries(retriesLeft);
//                retryJob.setMeta(result.getErrorMessage());
//                event.setJob(retryJob);
//                // save intermediate retry status in Mongo
//                saveJobRunToDepTracker(job, JobStatus.RETRYING);
//                sendEvent(event, retryQueueTopic);
//
//                log.warn("🔄 Job {} failed (reason={}), requeued (remaining={})",
//                        job.getId(), result.getErrorMessage(), retriesLeft);
//            } else {
//                // retries exhausted → mark FAILED
//                saveJobRunToDepTracker(job, JobStatus.FAILED);
//                modifyJob.updateJobAndRun(job.getId(), TaskStatus.CANCELLED,
//                        "Final failure: " + result.getErrorMessage());
//
//                log.error("⛔ Job {} permanently FAILED after retries exhausted", job.getId());
//            }
//        }
//    }
//
//
//    /* =========================================================
//     * Unified Job Event Handler (used by RunQueue + WaitQueue)
//     * ========================================================= */
//    private void handleJobEvent(RedisJobWrapper event) {
//        Job job = event.getJob();
//        if (job == null) {
//            log.warn("⚠️ Received null job in event {}", event);
//            return;
//        }
//
//        // Retries exhausted
//        if (job.getRetries() <= 0) {
//            log.error("🚫 Job {} has no retries left, marking FAILED.", job.getId());
//            saveJobRunToDepTracker(job, JobStatus.FAILED);
//            return;
//        }
//
//        List<String> deps = job.getDependencies();
//
//        // Case 1: No dependencies → run immediately
//        if (deps == null || deps.isEmpty()) {
//            runJob(job, event);
//            return;
//        }
//
//        // Case 2: Check Redis memory cache first
//        boolean waiting = false;
//        for (String dep : deps) {
//            if (!redisPriorityQueue.isJobDone(dep)) {
//                queueIntoRedisWaiting(dep, event);
//                waiting = true;
//            }
//        }
//        if (waiting) {
//            log.info("⏸️ Job {} moved to wait queue (deps not done in Redis)", job.getId());
//            return;
//        }
//
//        // Case 3: Fallback DB check (to warm Redis)
//        for (String dep : deps) {
//            if (!redisPriorityQueue.isJobDone(dep)) {
//                DepTracker dt = depTrackerClient.findFirstByJobName(dep);
//                if (dt == null || dt.getJobStatus() != JobStatus.SUCCESS) {
//                    queueIntoRedisWaiting(dep, event);
//                    log.info("⏸️ Job {} moved to wait queue (dep={} not SUCCESS in DB)", job.getId(), dep);
//                    return;
//                } else {
//                    // warm cache in Redis so next time DB won’t be hit
//                    redisPriorityQueue.markJobDone(dep);
//                }
//            }
//        }
//
//        // Case 4: All dependencies satisfied → run
//        runJob(job, event);
//    }
//
//
//    /* =========================================================
//     * Job execution + dependent release
//     * ========================================================= */
//    private void runJob(Job job, RedisJobWrapper event) {
//        ExecutionResult status = execute(job);
//        if (status.getStatus() == JobStatus.SUCCESS) {
//            saveJobRunToDepTracker(job, JobStatus.SUCCESS);
//            releaseWaitingDependents(job);
//            setStatusAfterChk(job);
//        } else {
//            job.setMeta(status.getErrorMessage());
//            handleRetry(event, job);
//        }
//    }
//
//    private void releaseWaitingDependents(Job justFinishedJob) {
//        String finishedName = justFinishedJob.getName();
//
//        List<DependentJobGroup> waiting = redisPriorityQueue.getJobsAsObjects(finishedName);
//        redisPriorityQueue.deleteDependency(finishedName);
//
//        if (waiting.isEmpty()) return;
//
//        for (DependentJobGroup djg : waiting) {
//            RedisJobWrapper wrapper = new RedisJobWrapper();
//            wrapper.setId(Long.valueOf(djg.getJobId()));
//            wrapper.setTime(djg.getScheduleTime());
//
//            Job depJob = new Job();
//            depJob.setId(Long.valueOf(djg.getJobId()));
//            depJob.setName(djg.getJobId()); // fallback, you can enrich if needed
//            depJob.setRetries(djg.getRetries());
//            depJob.setScheduleTime(djg.getScheduleTime());
//
//            wrapper.setJob(depJob);
//
//            sendEvent(wrapper, waitQueueTopic);
//            log.info("📤 Released dependent job {} → WAIT queue (dep={})", djg.getJobId(), finishedName);
//        }
//    }
//
//    /* =========================================================
//     * Cron next run check
//     * ========================================================= */
//    private void setStatusAfterChk(Job job) {
//        try {
//            List<Map.Entry<String, LocalDateTime>> nextRuns =
//                    cronMetadataExtractor.getFormattedAndLocalExecutionTimes(job.getCronExpression(), 5);
//
//            if (nextRuns != null && !nextRuns.isEmpty()) {
//                modifyJob.updateJobAndRun(
//                        job.getId(),
//                        TaskStatus.READY,
//                        "Ready for next scheduled run at " + nextRuns.get(0).getValue()
//                );
//            } else {
//                modifyJob.updateJobAndRun(
//                        job.getId(),
//                        TaskStatus.SUCCESS,
//                        "No future cron → marking SUCCESS"
//                );
//            }
//        } catch (Exception e) {
//            log.error("❌ Cron check failed for job {}: {}", job.getId(), e.getMessage());
//            modifyJob.updateJobAndRun(job.getId(), TaskStatus.SUCCESS, "Cron check failed → SUCCESS");
//        }
//    }
//}


































package com.executor1.service;

import com.executor1.config.DepTrackerClient;
import com.executor1.config.RedisPriorityQueue;
import com.executor1.entities1.Job;
import com.executor1.entities1.RedisJobWrapper;
import com.executor1.entities1.TaskStatus;
import com.executor1.entities3.JobRun;
import com.executor1.entities4.DepTracker;
import com.executor1.entities4.DependentJobGroup;
import com.executor1.entities4.ExecutionResult;
import com.executor1.entities4.JobStatus;
import com.executor1.utility.CronMetadataExtractor;
import com.executor1.utility.ModifyJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.BeanUtils;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final CronMetadataExtractor cronMetadataExtractor;
    private final ModifyJob modifyJob;

    private final RedisPriorityQueue redisPriorityQueue;
    private final DepTrackerClient depTrackerClient;
    private final JobRunService jobRunService;

    private final NewTopic runQueueTopic;
    private final NewTopic waitQueueTopic;
    private final NewTopic retryQueueTopic;
    private final NewTopic deptrackerQueueTopic;

    private final KafkaTemplate<String, RedisJobWrapper> redisJobWrapperKafkaTemplate;
    private final KafkaTemplate<String, DepTracker> depTrackerKafkaTemplate;

    /* =========================================================
     * Kafka send helpers
     * ========================================================= */
    private void sendEvent(RedisJobWrapper event, NewTopic topic) {
        Message<RedisJobWrapper> message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, topic.name())
                .build();
        redisJobWrapperKafkaTemplate.send(message);
    }

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
                    .startTime(jr.getStartTime())
                    .endTime(LocalDateTime.now())
                    .errorMessage(errorMsg)
                    .maxRetries(job.getRetries())
                    .dependencies(job.getDependencies())
                    .retryCount(jr.getAttemptNumber())
                    .scheduleTime(jr.getExecutionTime())
                    .executorId("1")
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
        log.info("▶️ Executing job {} name={}", job.getId(), job.getName());
        // TODO: Replace this with real execution
        return ExecutionResult.builder()
                .status(JobStatus.FAILED)  // or JobStatus.RETRYING / FAILED
                .errorMessage(null)
                .build();
    }

    /* =========================================================
     * Retry Handling
     * ========================================================= */
    private void handleRetry(RedisJobWrapper event, Job job, String errorMessage) {
        int retriesLeft = job.getRetries() - 1;
        if (retriesLeft > 0) {
            Job retryJob = new Job();
            BeanUtils.copyProperties(job, retryJob);
            retryJob.setRetries(retriesLeft);
            retryJob.setMeta(errorMessage);
            event.setJob(retryJob);

            saveJobRun(job, JobStatus.RETRYING, errorMessage);
            sendEvent(event, retryQueueTopic);

            log.warn("🔄 Job {} failed, requeued with {} retries left", job.getId(), retriesLeft);
        } else {
            saveJobRun(job, JobStatus.FAILED, errorMessage);
            modifyJob.updateJobAndRun(job.getId(), TaskStatus.CANCELLED, "Retries exhausted: " + errorMessage);
            log.error("⛔ Job {} failed permanently after retries exhausted", job.getId());
        }
    }

    /* =========================================================
     * Unified Job Runner
     * ========================================================= */
    private void runJob(Job job, RedisJobWrapper event) {
        ExecutionResult result = execute(job);

        if (result.getStatus() == JobStatus.SUCCESS) {
            saveJobRun(job, JobStatus.SUCCESS, null);
            releaseWaitingDependents(job);
            setNextCronOrSuccess(job);
        } else {
            handleRetry(event, job, result.getErrorMessage());
        }
    }

    /* =========================================================
     * Handle dependencies
     * ========================================================= */
    private void handleJobEvent(RedisJobWrapper event) {
        Job job = event.getJob();
        if (job == null) return;

        if (job.getRetries() <= 0) {
            saveJobRun(job, JobStatus.FAILED, "No retries left");
            return;
        }

        List<String> deps = job.getDependencies();
        if (deps == null || deps.isEmpty()) {
            runJob(job, event);
            return;
        }

        // 1️⃣ Redis check first
        for (String dep : deps) {
            if (!redisPriorityQueue.isJobDone(dep)) {
                redisPriorityQueue.addJobToDependency(dep,
                        String.valueOf(job.getId()),
                        event.getTime(),
                        job.getRetries()
                        );
                log.info("⏸️ Job {} waiting for dependency {}", job.getId(), dep);
                return;
            }
        }

        // 2️⃣ Fallback DB check
        for (String dep : deps) {
            if (!redisPriorityQueue.isJobDone(dep)) {
                DepTracker dt = depTrackerClient.findFirstByJobName(dep);
                if (dt == null || dt.getJobStatus() != JobStatus.SUCCESS) {
                    log.info("⏸️ Job {} waiting (dep {} not SUCCESS in DB)", job.getId(), dep);
                    return;
                } else {
                    redisPriorityQueue.markJobDone(dep); // warm Redis
                }
            }
        }

        // 3️⃣ All deps satisfied
        runJob(job, event);
    }

    /* =========================================================
     * Release dependent jobs once a job finishes
     * ========================================================= */
    private void releaseWaitingDependents(Job finishedJob) {
        String depName = finishedJob.getName();
        List<DependentJobGroup> waitingJobs = redisPriorityQueue.getJobsAsObjects(depName);

        redisPriorityQueue.deleteDependency(depName); // cleanup

        for (DependentJobGroup djg : waitingJobs) {
            RedisJobWrapper wrapper = new RedisJobWrapper();
            wrapper.setId(Long.valueOf(djg.getJobId()));
            wrapper.setTime(djg.getScheduleTime());

            Job depJob = new Job();
            depJob.setId(Long.valueOf(djg.getJobId()));
            depJob.setName(djg.getJobName()); // ✅ use stored name
            depJob.setRetries(djg.getRetries());
            depJob.setScheduleTime(djg.getScheduleTime());

            wrapper.setJob(depJob);

            sendEvent(wrapper, waitQueueTopic);
            log.info("📤 Released dependent job {} back to WAIT queue", depJob.getId());
        }
    }

    /* =========================================================
     * Cron Handling
     * ========================================================= */
    private void setNextCronOrSuccess(Job job) {
        try {
            List<Map.Entry<String, LocalDateTime>> nextRuns =
                    cronMetadataExtractor.getFormattedAndLocalExecutionTimes(job.getCronExpression(), 5);

            if (nextRuns != null && !nextRuns.isEmpty()) {
                modifyJob.updateJobAndRun(job.getId(), TaskStatus.READY,
                        "Ready for next run at " + nextRuns.get(0).getValue());
            } else {
                modifyJob.updateJobAndRun(job.getId(), TaskStatus.SUCCESS, "No future runs → marked SUCCESS");
            }
        } catch (Exception e) {
            log.error("❌ Cron evaluation failed for job {}: {}", job.getId(), e.getMessage());
            modifyJob.updateJobAndRun(job.getId(), TaskStatus.SUCCESS, "Cron evaluation failed");
        }
    }

    /* =========================================================
     * Kafka Consumers
     * ========================================================= */
    @KafkaListener(
            topics = "${spring.kafka.topic.run}",
            containerFactory = "runQueueKafkaListenerContainerFactory"
//           ,concurrency = "3"  // <-- 3 threads for RunQueue
    )
    public void consumeRunQueue(RedisJobWrapper event) {
        log.info("▶️ Consumed from RunQueue job={}", event.getJob() != null ? event.getJob().getId() : null);
        handleJobEvent(event);
    }

    @KafkaListener(topics = "${spring.kafka.topic.waitqueue}", containerFactory = "waitQueueKafkaListenerContainerFactory")
    public void consumeWaitQueue(RedisJobWrapper event) {
        log.info("↪️ Consumed from WaitQueue job={}", event.getJob() != null ? event.getJob().getId() : null);
        handleJobEvent(event);
    }

    @KafkaListener(topics = "${spring.kafka.topic.retryqueue}", containerFactory = "retryQueueKafkaListenerContainerFactory")
    public void consumeRetryQueue(RedisJobWrapper event) {
        log.info("♻️ Consumed from RetryQueue job={}", event.getJob() != null ? event.getJob().getId() : null);
        handleJobEvent(event);
    }
}

