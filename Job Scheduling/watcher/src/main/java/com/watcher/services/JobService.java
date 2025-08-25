package com.watcher.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.watcher.Config.RedisPriorityQueueService;
import com.watcher.Config.ScheduledJobProducer;
import com.watcher.entities1.Job;
import com.watcher.entities1.RedisJobWrapper;
import com.watcher.entities1.TaskStatus;
import com.watcher.entities3.RunStatus;
import com.watcher.utils.CronMetadataExtractor;
import com.watcher.utils.FetchLatestJob;
import com.watcher.utils.ModifyJob;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.RedisConnection;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final FetchLatestJob fetchLatestJob;
    private final ModifyJob modifyJob;
    private final CronMetadataExtractor cronMetadataExtractor;
    private final RedisPriorityQueueService redisPriorityQueueService;
    private final ScheduledJobProducer scheduledJobProducer;
//    @Qualifier("redisQueueTemplate")
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper mapper;

    @Value("${job.expiry.threshold.minutes:5}")
    private long expiryThresholdMinutes;

    @Value("${job.expiry.action:CANCEL}")
    private String expiryAction;

    @Value("${job.cron.future.count:10}")
    private int futureCount;

    @Value("${job.processor.batch.size:5}")
    private int batchSize;

    @Value("${job.kafka.retry.delay.seconds:30}")
    private int kafkaRetryDelaySeconds;

    @Value("${job.lock.timeout.seconds:300}") // NEW: Lock timeout
    private long lockTimeoutSeconds;

    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    // ===== Load next N cron executions =====
    @Scheduled(fixedDelayString = "${job.loader.interval.ms:20000}")
    @SchedulerLock(name = "loadJobsIntoRedisQueue", lockAtLeastFor = "10s", lockAtMostFor = "30s")
    public void loadJobsIntoRedisQueue() {
        try {
            Job job = fetchLatestJob.fetchLatestJob();
            if (job == null || job.getCronExpression() == null) {
                log.warn("❌ No valid job or cron expression.");
                return;
            }

            if (job.getPayloads() == null || job.getPayloads().isEmpty()) {
                cancelJob(job, "This job has no payload");
                return;
            }

            List<Map.Entry<String, LocalDateTime>> pairs =
                    cronMetadataExtractor.getFormattedAndLocalExecutionTimes(job.getCronExpression(), futureCount);

            boolean allInvalid = pairs.stream().allMatch(entry -> entry.getValue() == null);
            if (pairs.isEmpty() || allInvalid) {
                cancelJob(job, "No upcoming scheduled times — CRON expired or invalid");
                return;
            }

            for (Map.Entry<String, LocalDateTime> entry : pairs) {
                if (entry.getValue() != null) {
                    redisPriorityQueueService.addToQueue(job, entry.getValue());
                    log.info("📥 Scheduled job {} for {}", job.getJobSeqId(), entry.getKey());
                }
            }
        } catch (Exception e) {
            log.error("❌ Error loading jobs into Redis queue: {}", e.getMessage(), e);
        }
    }

    // ===== Process jobs due/expired =====
    @Scheduled(fixedRateString = "${job.processor.interval.ms:30000}")
    @SchedulerLock(name = "processScheduledJobsFromRedis", lockAtLeastFor = "10s", lockAtMostFor = "1m")
    public void processScheduledJobsFromRedis() {
        try {
            List<String> jobs = redisPriorityQueueService.pollTopJobsAtomically(batchSize);
            if (jobs.isEmpty()) return;

            for (String jobStr : jobs) {
                executor.submit(() -> {
                    try {
                        handleJob(jobStr);
                    } catch (Exception e) {
                        log.error("❌ Error processing job: {}", e.getMessage(), e);
                    }
                });
            }
        } catch (Exception e) {
            log.error("❌ Error during Redis job batch processing: {}", e.getMessage(), e);
        }
    }

    private void handleJob(String jobStr) {
        RedisJobWrapper wrapper = null;
        try {
            wrapper = mapper.readValue(jobStr, RedisJobWrapper.class);
            Job job = wrapper.getJob();
            LocalDateTime scheduledTime = wrapper.getTime();
            LocalDateTime now = getClusterTime();

            // Idempotency key with timeout to prevent deadlocks
            String lockKey = "job:lock:" + job.getJobSeqId() + ":" + scheduledTime;
            Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(
                    lockKey, "1", Duration.ofSeconds(lockTimeoutSeconds));

            if (lockAcquired == null || !lockAcquired) {
                log.warn("⚠️ Skipping duplicate job {} at {}", job.getJobSeqId(), scheduledTime);
                // Re-add to queue if still valid
                if (scheduledTime.isAfter(now)) {
                    redisPriorityQueueService.addToQueue(job, scheduledTime);
                }
                return;
            }

            LocalDateTime expiryCutoff = now.minusMinutes(expiryThresholdMinutes);

            // CASE 1: Expired job
            if (scheduledTime.isBefore(expiryCutoff)) {
                handleExpiredJob(job);
                redisTemplate.delete(lockKey); // Clean up lock
                return;
            }

            // CASE 2: Due now or in the past (within threshold)
            if (!scheduledTime.isAfter(now)) {
                handleDueJob(wrapper, now);
                redisTemplate.delete(lockKey); // Clean up lock
                return;
            }

            // CASE 3: Not yet due - re-add with proper scheduling
            redisPriorityQueueService.addToQueue(job, scheduledTime);
            redisTemplate.delete(lockKey); // Clean up lock
            log.info("⏳ Job {} not due yet (scheduled: {})", job.getJobSeqId(), scheduledTime);

        } catch (Exception e) {
            log.error("❌ Failed to handle job: {}", e.getMessage(), e);
            // Clean up lock if we have the wrapper
            if (wrapper != null) {
                String lockKey = "job:lock:" + wrapper.getJob().getJobSeqId() + ":" + wrapper.getTime();
                redisTemplate.delete(lockKey);
            }
        }
    }

    private void handleExpiredJob(Job job) {
        if ("RETRY".equalsIgnoreCase(expiryAction)) {
            LocalDateTime retryTime = getClusterTime().plusSeconds(60);
            redisPriorityQueueService.addToQueue(job, retryTime);
            log.info("🔄 Retrying expired job {} after 60s", job.getJobSeqId());
        } else {
            cancelJob(job, "Job expired — scheduled too long ago");
        }
    }

    private void handleDueJob(RedisJobWrapper wrapper, LocalDateTime now) {
        try {
            scheduledJobProducer.sendEvent(wrapper);
            modifyJob.updateJobAndRun(
                    wrapper.getJob().getId(),
                    TaskStatus.SCHEDULED,
                    RunStatus.QUEUED,
                    null,
                    wrapper.getTime()
            );
            log.info("📤 Job {} dispatched to Kafka", wrapper.getJob().getJobSeqId());
        } catch (Exception e) {
            log.error("❌ Kafka send failed for job {}, requeueing",
                    wrapper.getJob().getJobSeqId(), e);
            // Retry after delay
            LocalDateTime retryTime = now.plusSeconds(kafkaRetryDelaySeconds);
            redisPriorityQueueService.addToQueue(wrapper.getJob(), retryTime);
        }
    }

    // Safe debug method - only sample a few jobs
//    @Scheduled(fixedRateString = "${job.debug.interval.ms:60000}")
//    public void debugPrintRedisQueue() {
//        try {
//            List<String> sampleJobs = redisPriorityQueueService.getSampleJobs(5); // Limit to 5
//            sampleJobs.forEach(jobStr -> {
//                try {
//                    RedisJobWrapper wrapper = mapper.readValue(jobStr, RedisJobWrapper.class);
//                    log.info("🔎 Redis sample - Job={} → Scheduled={}",
//                            wrapper.getJob().getJobSeqId(), wrapper.getTime());
//                } catch (Exception e) {
//                    log.error("❌ Failed to parse Redis job: {}", jobStr);
//                }
//            });
//        } catch (Exception e) {
//            log.error("❌ Error sampling Redis queue: {}", e.getMessage());
//        }
//    }

    private void cancelJob(Job job, String reason) {
        boolean cancelled = modifyJob.updateJobAndRun(
                job.getId(), TaskStatus.CANCELLED, RunStatus.FAILED, reason, null);
        if (cancelled) {
            log.info("🛑 Job '{}' cancelled → {}", job.getJobSeqId(), reason);
        } else {
            log.warn("⚠️ Failed to cancel job '{}'", job.getJobSeqId());
        }
    }

    private LocalDateTime getClusterTime() {
        return redisTemplate.execute((RedisConnection connection) -> {
            Long millis = connection.time();
            return Instant.ofEpochMilli(millis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        });
    }

    // Graceful shutdown
    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}