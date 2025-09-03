package com.executor1.service;

import com.executor1.entities1.Job;
import com.executor1.entities1.RedisJobWrapper;
import com.executor1.entities1.TaskStatus;
import com.executor1.entities3.JobRun;
import com.executor1.entities4.DepTracker;
import com.executor1.entities4.DependentJobGroup;
import com.executor1.entities4.ExecutionResult;
import com.executor1.entities4.JobStatus;
import com.executor1.utility.CronMetadataExtractor;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final CronMetadataExtractor cronMetadataExtractor;
    private final ModifyJob modifyJob;

    private final RedisPriorityQueue redisPriorityQueue;
    private final DepTrackerClient depTrackerClient;
    private final JobRunService jobRunService;
    private final ExecutorService executorService;
    private final CancelReqRepository cancelReqRepository;

    private final NewTopic runQueueTopic;
    private final NewTopic waitQueueTopic;
    private final NewTopic retryQueueTopic;
    private final NewTopic deptrackerQueueTopic;

    private final KafkaTemplate<String, RedisJobWrapper> redisJobWrapperKafkaTemplate;
    private final KafkaTemplate<String, DepTracker> depTrackerKafkaTemplate;

    LocalDateTime time = LocalDateTime.now();

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
                    .startTime(time)
                    .endTime(LocalDateTime.now())
                    .errorMessage(errorMsg)
                    .retryCount(jr.getAttemptNumber())
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
        log.info("Executing job: {}", job);
        return executorService.execute(job);
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
            JobRun jobRun = new JobRun();
            jobRun.setAttemptNumber(retriesLeft);
            jobRunService.patchUpdate(job.getId(),jobRun);
            saveJobRun(job, JobStatus.RETRYING, errorMessage);
            sendEvent(event, retryQueueTopic);

            log.warn("🔄 Job {} failed, requeued with {} retries left", job.getId(), retriesLeft);
        } else {
            cancelReqRepository.deleteById(String.valueOf(job.getId()));
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
            setNextCronOrSuccess(job,TaskStatus.SUCCESS);
        } else if(result.getStatus() == JobStatus.PARTIAL){
            saveJobRun(job, JobStatus.PARTIAL, result.getErrorMessage());
            setNextCronOrSuccess(job,TaskStatus.PAUSED);
        }else{
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
            cancelReqRepository.deleteById(String.valueOf(job.getId()));
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
//         time = LocalDateTime.now();
        // 3️⃣ All deps satisfied
        runJob(job, event);
    }

    /* =========================================================
     * Release dependent jobs once a job finishes
     * ========================================================= */
    private void releaseWaitingDependents(Job finishedJob) {
        String depName = finishedJob.getName();
        List<DependentJobGroup> waitingJobs = redisPriorityQueue.getJobsAsObjects(depName);

        // cleanup
        redisPriorityQueue.deleteDependency(depName);
        log.info("Waiting job Released for Dependency of {} ",depName);

        // jobs depending on this dep
        List<Job> allJobs = depTrackerClient.getJobsByDependency(depName);

        // map jobId -> DependentJobGroup for quick lookup
        Map<Long, DependentJobGroup> waitingJobMap = waitingJobs.stream()
                .collect(Collectors.toMap(
                        djg -> Long.valueOf(djg.getJobId()), // key = jobId as Long
                        djg -> djg                           // value = whole object
                ));
            System.out.println("Waiting Jobs"+waitingJobMap);
        for (Job job : allJobs) {
            DependentJobGroup djg = waitingJobMap.get(job.getId());
            if (djg != null) {
                RedisJobWrapper wrapper = new RedisJobWrapper();
                wrapper.setId(job.getId());

                // ✅ set schedule time from waiting job group
                job.setScheduleTime(djg.getScheduleTime());

                wrapper.setJob(job);
                System.out.println("RedisJobWrapper = "+wrapper);
                sendEvent(wrapper, waitQueueTopic);
                log.info("📤 Released dependent job {} back to WAIT queue", job.getId());
            }
        }
    }


    /* =========================================================
     * Cron Handling
     * ========================================================= */
    private void setNextCronOrSuccess(Job job,TaskStatus status) {
        try {
            List<Map.Entry<String, LocalDateTime>> nextRuns =
                    cronMetadataExtractor.getFormattedAndLocalExecutionTimes(job.getCronExpression(), 5);

            if (nextRuns != null && !nextRuns.isEmpty()) {
                modifyJob.updateJobAndRun(job.getId(), status,
                        status+ " for next run at " + nextRuns.get(0).getValue());
            } else {
                modifyJob.updateJobAndRun(job.getId(), status, "No future runs → marked "+status);
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

