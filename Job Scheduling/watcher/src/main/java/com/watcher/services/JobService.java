package com.watcher.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.watcher.Config.RedisPriorityQueueService;
import com.watcher.Config.ScheduledJobProducer;
import com.watcher.entities1.Job;
import com.watcher.entities1.RedisJobWrapper;
import com.watcher.entities1.TaskStatus;
import com.watcher.entities3.RunStatus;
import com.watcher.utils.CronMetadataExtractor;
import com.watcher.utils.FetchLatestJob;
import com.watcher.utils.ModifyJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class JobService {

    @Autowired
    private FetchLatestJob fetchLatestJob;

    @Autowired
    private ModifyJob modifyJob;

    @Autowired
    private CronMetadataExtractor cronMetadataExtractor;

    @Autowired
    private RedisPriorityQueueService redisPriorityQueueService;

    @Autowired
    private ScheduledJobProducer scheduledJobProducer;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

//    @PostConstruct
//    public void clearRedisQueueAtStartup() {
//        redisPriorityQueueService.clearAllJobs();
//        log.info("🧹 Cleared all jobs from Redis priority queue at startup.");
//    }


    /**
     * Load next 10 cron execution times into Redis
     */
    @Scheduled(fixedDelay = 20000)
    public void loadJobsIntoRedisQueue() {
        Job job = fetchLatestJob.fetchLatestJob();
        if (job == null || job.getCronExpression() == null) {
            log.warn("❌ No valid job or cron expression.");
            return;
        }

        log.info("📦 Loaded job from DB: {}", job);

        if(job.getPayloads().size() == 0){
            log.warn("⛔ Cancelling job, This job has no Payload please add'{}'", job.getJobSeqId());
            System.out.println("new jobs "+job);
            boolean cancelled = modifyJob.updateJobAndRun(
                    job.getId(),
                    TaskStatus.CANCELLED,
                    RunStatus.FAILED,
                    "This job has no Payload "
            );

            if (cancelled) {
                log.info("🛑 Job '{}' cancelled successfully due to job has no Payload .", job.getJobSeqId());
            } else {
                log.warn("⚠️ Failed to cancel job '{}'", job.getJobSeqId());
            }
            return;
        }

        List<Map.Entry<String, LocalDateTime>> pairs =
                cronMetadataExtractor.getFormattedAndLocalExecutionTimes(job.getCronExpression(), 10);

        // 🔍 Check if all entries are invalid or empty
        boolean allInvalid = pairs.stream().allMatch(entry -> entry.getValue() == null);

        if (pairs.isEmpty() || allInvalid) {
            log.warn("⛔ CRON expression produced no valid future times. Cancelling job '{}'", job.getJobSeqId());

            boolean cancelled = modifyJob.updateJobAndRun(
                    job.getId(),
                    TaskStatus.CANCELLED,
                    RunStatus.FAILED,
                    "No upcoming scheduled times — CRON expired or invalid"
            );

            if (cancelled) {
                log.info("🛑 Job '{}' cancelled successfully due to expired CRON.", job.getJobSeqId());
            } else {
                log.warn("⚠️ Failed to cancel job '{}'", job.getJobSeqId());
            }
            return;
        }



        // ✅ Push future executions to Redis
        for (Map.Entry<String, LocalDateTime> entry : pairs) {
            LocalDateTime scheduledTime = entry.getValue();
            LocalDateTime now = LocalDateTime.now();

            if (scheduledTime == null) {
                log.warn("⚠️ Skipping null scheduledTime for job '{}' for timing of '{}' ", job.getJobSeqId(),entry.getKey());
                continue;
            }

            redisPriorityQueueService.addToQueue(job, scheduledTime);
            log.info("📥 Pushed job '{}' to Redis for execution at {}", job.getJobSeqId(), entry.getKey());
        }
    }

    /**
     * Process jobs from Redis that are due or expired
     */
    @Scheduled(fixedRate = 30000)
    public void processScheduledJobsFromRedis() {
        try {
            Optional<String> jobStrOpt = redisPriorityQueueService.peekTopJob();
            if (jobStrOpt.isEmpty()) return;

            String jobStr = jobStrOpt.get();
            RedisJobWrapper wrapper = mapper.readValue(jobStr, RedisJobWrapper.class);

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime fiveMinutesAgo = now.minusMinutes(5);
            LocalDateTime scheduledTime = wrapper.getTime();
            Job job = wrapper.getJob();

            log.info("🔍 Checking top job {} → Scheduled: {}, Now: {}", job.getJobSeqId(), scheduledTime, now);

            // CASE 1: Expired
            if (scheduledTime.isBefore(fiveMinutesAgo)) {
                boolean cancelled = modifyJob.updateJobAndRun(
                        job.getId(),
                        TaskStatus.CANCELLED,
                        RunStatus.FAILED,
                        "time over/out to current time"
                );
                if (cancelled) {
                    redisPriorityQueueService.pollTopJob();
                    log.info("🗑 Cancelled expired job {}", job.getJobSeqId());
                    redisPriorityQueueService.inspectQueue();
                }
                return;
            }

            // CASE 2: Due now
            if (!scheduledTime.isAfter(now)) {
                scheduledJobProducer.sendEvent(wrapper);

                boolean updated = modifyJob.updateJobAndRun(
                        job.getId(),
                        TaskStatus.SCHEDULED,
                        RunStatus.QUEUED,
                        null
                );
                if (updated) {
                    boolean removed = redisPriorityQueueService.pollTopJob();
                    if (removed) {
                        log.info("🧹 Removed job {} from Redis", job.getJobSeqId());
                        redisPriorityQueueService.inspectQueue();
                    }
                    log.info("📤 Sent job {} to Kafka", job.getJobSeqId());
                }
            }

            // CASE 3: Not yet due → do nothing
        } catch (Exception e) {
            log.error("❌ Error during Redis job processing: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedRate = 60000)
    public void debugPrintRedisQueue() {
        List<String> jobs = redisPriorityQueueService.getAllJobs(); // assume this exists
        for (String jobStr : jobs) {
            try {
                RedisJobWrapper wrapper = mapper.readValue(jobStr, RedisJobWrapper.class);
                log.info("🔎 Job in Redis: ID={}, Time={}", wrapper.getJob().getJobSeqId(), wrapper.getTime());
            } catch (Exception e) {
                log.error("❌ Failed to parse Redis job: {}", jobStr);
            }
        }
    }
}
