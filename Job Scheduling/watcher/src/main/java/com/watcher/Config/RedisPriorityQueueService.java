package com.watcher.Config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.watcher.entities1.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisPriorityQueueService {

//    @Qualifier("redisQueueTemplate")
    private final RedisTemplate<String, String> redisTemplate;
//    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String REDIS_KEY = "job:priorityQueue";
    private final ObjectMapper objectMapper;

    /**
     * Add a job to Redis sorted set with deduplication based on job ID + scheduled time
     */
    public void addToQueue(Job job, LocalDateTime executionTime) {
        if (job == null || job.getId() == null || executionTime == null) return;

        try {
            // Create unique idempotency key for this job occurrence
            String jobUniqueKey = job.getId() + ":" + executionTime.toString();

            // Check if job with this key already exists in Redis
            Set<String> existingJobs = redisTemplate.opsForZSet().range(REDIS_KEY, 0, -1);
            if (existingJobs != null) {
                for (String json : existingJobs) {
                    Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
                    Map<String, Object> jobMap = (Map<String, Object>) map.get("job");
                    Long existingJobId = Long.parseLong(String.valueOf(jobMap.get("id")));
                    String existingTime = String.valueOf(map.get("time"));

                    if (existingJobId.equals(job.getId()) && existingTime.equals(executionTime.toString())) {
                        log.info("⚠️ Job {} at {} already exists in Redis, skipping", job.getJobSeqId(), executionTime);
                        return;
                    }
                }
            }

            // Score = epoch milliseconds + tiny offset to preserve ordering
            double score = executionTime.toInstant(ZoneOffset.UTC).toEpochMilli() + job.getId() / 1_000_000_000.0;

            Map<String, Object> redisPayload = new HashMap<>();
            redisPayload.put("job", job);
            redisPayload.put("time", executionTime.toString());

            String json = objectMapper.writeValueAsString(redisPayload);
            redisTemplate.opsForZSet().add(REDIS_KEY, json, score);

            log.info("✅ Added job {} to Redis for execution at {}", job.getJobSeqId(), executionTime);

        } catch (JsonProcessingException e) {
            log.error("❌ Failed to serialize job {}: {}", job.getJobSeqId(), e.getMessage());
        } catch (Exception e) {
            log.error("❌ Unexpected error adding job {} to Redis: {}", job.getJobSeqId(), e.getMessage());
        }
    }

    /**
     * Atomically poll the top N jobs from Redis
     */
    public List<String> pollTopJobsAtomically(int batchSize) {
        try {
            Set<String> topJobs = redisTemplate.opsForZSet().range(REDIS_KEY, 0, batchSize - 1);
            if (topJobs == null || topJobs.isEmpty()) return Collections.emptyList();

            // Remove all polled jobs atomically
            redisTemplate.opsForZSet().remove(REDIS_KEY, topJobs.toArray());

            return new ArrayList<>(topJobs);
        } catch (Exception e) {
            log.error("❌ Failed to poll top jobs from Redis: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get all jobs in Redis (for debugging)
     */
    public List<String> getAllJobs() {
        try {
            Set<String> all = redisTemplate.opsForZSet().range(REDIS_KEY, 0, -1);
            return all == null ? Collections.emptyList() : new ArrayList<>(all);
        } catch (Exception e) {
            log.error("❌ Failed to fetch all jobs from Redis: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Clear all jobs (for startup or testing)
     */
    public void clearAllJobs() {
        try {
            redisTemplate.delete(REDIS_KEY);
            log.info("🧹 Cleared all jobs from Redis priority queue");
        } catch (Exception e) {
            log.error("❌ Failed to clear Redis queue: {}", e.getMessage());
        }
    }
}
