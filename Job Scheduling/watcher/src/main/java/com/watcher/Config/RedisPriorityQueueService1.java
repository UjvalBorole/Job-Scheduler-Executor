package com.watcher.Config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.watcher.entities1.Job;
import com.watcher.entities1.RedisJobWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Slf4j
@Service
public class RedisPriorityQueueService1 {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String REDIS_KEY = "job_priority_queue";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public void addToQueue(Job job, LocalDateTime executionTime) {
        if (job == null || job.getId() == null) return;

        long baseMillis = executionTime.toInstant(ZoneOffset.UTC).toEpochMilli();
        double offset = job.getId() / 1_000_000_000.0; // to ensure uniqueness but maintain order
        double score = baseMillis + offset;

        // Check if job with same ID exists in Redis
        Set<String> existingJobs = redisTemplate.opsForZSet().range(REDIS_KEY, 0, -1);

        for (String json : existingJobs) {
            try {
                Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
                Long existingJobId = Long.parseLong(String.valueOf(((Map<String, Object>) map.get("job")).get("id")));
                String modifiedTimeStr = String.valueOf(((Map<String, Object>) map.get("job")).get("modifiedTime"));

                if (existingJobId.equals(job.getId())) {
                    LocalDateTime existingModifiedTime = LocalDateTime.parse(modifiedTimeStr);
                    if (!job.getModifiedTime().isAfter(existingModifiedTime)) {
                        // Skip insertion if not newer
                        return;
                    }

                    // Remove old version
                    redisTemplate.opsForZSet().remove(REDIS_KEY, json);
                    break;
                }

            } catch (Exception e) {
                log.error("Error parsing Redis JSON: {}", e.getMessage());
            }
        }

        // Create payload
        Map<String, Object> redisPayload = new HashMap<>();
        redisPayload.put("id", job.getId());
        redisPayload.put("time", executionTime.toString());
        redisPayload.put("job", job);

        try {
            String json = objectMapper.writeValueAsString(redisPayload);
            redisTemplate.opsForZSet().add(REDIS_KEY, json, score);
            log.info("✅ Added/Updated job ID {} with time {}", job.getId(), executionTime);
        } catch (JsonProcessingException e) {
            log.error("❌ Failed to serialize job to JSON: {}", e.getMessage());
        }
    }


    public Optional<String> peekTopJob() {
        Set<String> top = redisTemplate.opsForZSet().range(REDIS_KEY, 0, 0);
        return top.stream().findFirst();
    }

    public boolean pollTopJob() {
        Set<String> top = redisTemplate.opsForZSet().range(REDIS_KEY, 0, 0);

        if (top != null && !top.isEmpty()) {
            String job = top.iterator().next();
            Long removed = redisTemplate.opsForZSet().remove(REDIS_KEY, job);
            return removed != null && removed > 0;
        }

        return false;
    }

    public void inspectQueue() {
        Long size = redisTemplate.opsForZSet().size(REDIS_KEY);
        System.out.println("🔢 Redis Queue Size: " + size);

        // Get the job with the lowest score (next scheduled)
        Set<String> firstEntry = redisTemplate.opsForZSet().range(REDIS_KEY, 0, 0);

        if (firstEntry != null && !firstEntry.isEmpty()) {
            String nextJobJson = firstEntry.iterator().next();
            System.out.println("🕒 Next Scheduled Job: " + nextJobJson);
        } else {
            System.out.println("📭 Redis queue is empty.");
        }
    }
    public List<String> getAllJobs() {
        Set<String> allJobs = redisTemplate.opsForZSet().range(REDIS_KEY, 0, -1);
        return allJobs == null ? List.of() : new ArrayList<>(allJobs);
    }
    public Set<String> getTopNJobs(int n) {
        return redisTemplate.opsForZSet().range(REDIS_KEY, 0, n - 1);
    }
    public List<String> getJobsInRange(int start, int end) {
        Set<String> jobs = redisTemplate.opsForZSet().range(REDIS_KEY, start, end);
        return jobs == null ? List.of() : new ArrayList<>(jobs);
    }
    public void clearAllJobs() {
        redisTemplate.delete(REDIS_KEY);
    }


    // Remove a specific job wrapper based on exact match of job JSON
    public boolean removeJob(RedisJobWrapper targetWrapper) {
        try {
            // Serialize the job wrapper to match the stored value
            String targetJson = objectMapper.writeValueAsString(targetWrapper);

            // Remove that exact value from the Redis sorted set
            Long removedCount = redisTemplate.opsForZSet()
                    .remove(REDIS_KEY, targetJson);

            return removedCount != null && removedCount > 0;

        } catch (Exception e) {
            System.err.println("❌ Failed to remove job from Redis: " + e.getMessage());
            return false;
        }
    }

}
