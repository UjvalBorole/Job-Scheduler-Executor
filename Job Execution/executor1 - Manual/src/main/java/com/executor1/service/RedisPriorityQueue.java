//package com.executor1.service;
//
//import com.executor1.entities4.DependentJobGroup;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.SerializationFeature;
//import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.stereotype.Service;
//
//import java.time.LocalDateTime;
//import java.time.ZoneOffset;
//import java.util.*;
//
//@Slf4j
//@Service
//public class RedisPriorityQueue {
//    @Autowired
//    private RedisTemplate<String, String> redisTemplate;
//
//    public final ObjectMapper mapper = new ObjectMapper()
//            .registerModule(new JavaTimeModule()) // handle LocalDateTime
//            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
//
//    // -------- Existing methods --------
//    public void addJobToDependency(String dependency, String jobId, LocalDateTime scheduleTime,int rit) {
//        try {
//            String key = "dep_queue:" + dependency;
//            double score = scheduleTime.toEpochSecond(ZoneOffset.UTC);
//
//            DependentJobGroup jobGroup = DependentJobGroup.builder()
//                    .jobId(jobId)
//                    .scheduleTime(scheduleTime)
//                    .retries(rit)
//                    .build();
//
//            String json = mapper.writeValueAsString(jobGroup);
//
//            Set<String> existing = redisTemplate.opsForZSet().rangeByScore(key, score, score);
//            if (existing != null) {
//                for (String e : existing) {
//                    DependentJobGroup existingJob = mapper.readValue(e, DependentJobGroup.class);
//                    if (existingJob.getJobId().equals(jobId)) {
//                        log.info("⚠️ Job {} already exists for dependency {} at {}", jobId, dependency, scheduleTime);
//                        return;
//                    }
//                }
//            }
//
//            redisTemplate.opsForZSet().add(key, json, score);
//            log.info("✅ Added job {} for dependency {} at {}", jobId, dependency, scheduleTime);
//
//        } catch (Exception e) {
//            log.error("❌ Failed to add job {} for dependency {}: {}", jobId, dependency, e.getMessage());
//        }
//    }
//
//    public Set<String> getJobsByDependency(String dependency) {
//        String key = "dep_queue:" + dependency;
//        return redisTemplate.opsForZSet().range(key, 0, -1);
//    }
//
//    public List<DependentJobGroup> getJobsAsObjects(String dependency) {
//        String key = "dep_queue:" + dependency;
//        Set<String> jobs = redisTemplate.opsForZSet().range(key, 0, -1);
//        if (jobs == null) return List.of();
//
//        List<DependentJobGroup> result = new ArrayList<>();
//        for (String jobJson : jobs) {
//            try {
//                result.add(mapper.readValue(jobJson, DependentJobGroup.class));
//            } catch (Exception e) {
//                log.error("❌ Failed to deserialize job JSON: {}", e.getMessage());
//            }
//        }
//        return result;
//    }
//
//    // -------- New methods --------
//
//    /**
//     * Delete all jobs under a dependency (removes the entire key).
//     */
//    public void deleteDependency(String dependency) {
//        String key = "dep_queue:" + dependency;
//        Boolean deleted = redisTemplate.delete(key);
//        if (Boolean.TRUE.equals(deleted)) {
//            log.info("🗑️ Deleted entire dependency queue for {}", dependency);
//        } else {
//            log.warn("⚠️ No queue found to delete for dependency {}", dependency);
//        }
//    }
//
//    /**
//     * Delete a specific job from a dependency by jobId and scheduleTime.
//     */
//    public void deleteJob(String dependency, String jobId, LocalDateTime scheduleTime) {
//        try {
//            String key = "dep_queue:" + dependency;
//            double score = scheduleTime.toEpochSecond(ZoneOffset.UTC);
//
//            Set<String> existing = redisTemplate.opsForZSet().rangeByScore(key, score, score);
//            if (existing != null) {
//                for (String e : existing) {
//                    DependentJobGroup existingJob = mapper.readValue(e, DependentJobGroup.class);
//                    if (existingJob.getJobId().equals(jobId)) {
//                        redisTemplate.opsForZSet().remove(key, e);
//                        log.info("🗑️ Deleted job {} from dependency {} at {}", jobId, dependency, scheduleTime);
//                        return;
//                    }
//                }
//            }
//            log.warn("⚠️ Job {} not found in dependency {} at {}", jobId, dependency, scheduleTime);
//        } catch (Exception e) {
//            log.error("❌ Failed to delete job {} for dependency {}: {}", jobId, dependency, e.getMessage());
//        }
//    }
//
//    /**
//     * Check if a dependency queue exists in Redis.
//     */
//    public boolean isDependencyPresent(String dependency) {
//        String key = "dep_queue:" + dependency;
//        Boolean exists = redisTemplate.hasKey(key);
//        if (Boolean.TRUE.equals(exists)) {
//            log.info("✅ Dependency {} is present in Redis", dependency);
//            return true;
//        } else {
//            log.info("❌ Dependency {} is NOT present in Redis", dependency);
//            return false;
//        }
//    }
//    public void markJobDone(String jobName) {
//        redisTemplate.opsForSet().add("done_jobs", jobName);
//    }
//
//    public boolean isJobDone(String jobName) {
//        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember("done_jobs", jobName));
//    }
//
//}



package com.executor1.service;

import com.executor1.entities4.DependentJobGroup;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Slf4j
@Service
public class RedisPriorityQueue {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule()) // handle LocalDateTime
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // ================== Job Storage Methods (Type 1) ==================

    // Add a job to a dependency
    public void addJobToDependency(String dependency, String jobId, LocalDateTime scheduleTime, int rit) {
        try {
            String key = "dep_queue:" + dependency;
            double score = scheduleTime.toEpochSecond(ZoneOffset.UTC);

            DependentJobGroup jobGroup = DependentJobGroup.builder()
                    .jobId(jobId)
                    .scheduleTime(scheduleTime)
                    .retries(rit)
                    .build();

            // Wrap JSON: {"OneTime": {job details}}
            Map<String, Object> wrapper = new HashMap<>();
            wrapper.put(dependency, jobGroup);
            String json = mapper.writeValueAsString(wrapper);

            // Check duplicate at same score
            Set<String> existing = redisTemplate.opsForZSet().rangeByScore(key, score, score);
            if (existing != null) {
                for (String e : existing) {
                    Map<String, DependentJobGroup> existingWrapper =
                            mapper.readValue(e, mapper.getTypeFactory().constructMapType(Map.class, String.class, DependentJobGroup.class));
                    DependentJobGroup existingJob = existingWrapper.get(dependency);
                    if (existingJob != null && existingJob.getJobId().equals(jobId)) {
                        log.info("⚠️ Job {} already exists for dependency {} at {}", jobId, dependency, scheduleTime);
                        return;
                    }
                }
            }

            redisTemplate.opsForZSet().add(key, json, score);
            log.info("✅ Added job {} for dependency {} at {}", jobId, dependency, scheduleTime);

        } catch (Exception e) {
            log.error("❌ Failed to add job {} for dependency {}: {}", jobId, dependency, e.getMessage());
        }
    }

    // Get jobs as raw JSON
    public Set<String> getJobsByDependency(String dependency) {
        String key = "dep_queue:" + dependency;
        return redisTemplate.opsForZSet().range(key, 0, -1);
    }

    // Get jobs as DependentJobGroup objects
    public List<DependentJobGroup> getJobsAsObjects(String dependency) {
        String key = "dep_queue:" + dependency;
        Set<String> jobs = redisTemplate.opsForZSet().range(key, 0, -1);
        if (jobs == null) return List.of();

        List<DependentJobGroup> result = new ArrayList<>();
        for (String jobJson : jobs) {
            try {
                Map<String, DependentJobGroup> wrapper =
                        mapper.readValue(jobJson, mapper.getTypeFactory().constructMapType(Map.class, String.class, DependentJobGroup.class));
                DependentJobGroup job = wrapper.get(dependency);
                if (job != null) result.add(job);
            } catch (Exception e) {
                log.error("❌ Failed to deserialize job JSON: {}", e.getMessage());
            }
        }
        return result;
    }

    // Delete all jobs under a dependency
    public void deleteDependency(String dependency) {
        String key = "dep_queue:" + dependency;
        Boolean deleted = redisTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("🗑️ Deleted entire dependency queue for {}", dependency);
        } else {
            log.warn("⚠️ No queue found to delete for dependency {}", dependency);
        }
    }

    // Delete a specific job from a dependency
    public void deleteJob(String dependency, String jobId, LocalDateTime scheduleTime) {
        try {
            String key = "dep_queue:" + dependency;
            double score = scheduleTime.toEpochSecond(ZoneOffset.UTC);

            Set<String> existing = redisTemplate.opsForZSet().rangeByScore(key, score, score);
            if (existing != null) {
                for (String e : existing) {
                    Map<String, DependentJobGroup> wrapper =
                            mapper.readValue(e, mapper.getTypeFactory().constructMapType(Map.class, String.class, DependentJobGroup.class));
                    DependentJobGroup existingJob = wrapper.get(dependency);

                    if (existingJob != null && existingJob.getJobId().equals(jobId)) {
                        redisTemplate.opsForZSet().remove(key, e);
                        log.info("🗑️ Deleted job {} from dependency {} at {}", jobId, dependency, scheduleTime);
                        return;
                    }
                }
            }
            log.warn("⚠️ Job {} not found in dependency {} at {}", jobId, dependency, scheduleTime);
        } catch (Exception e) {
            log.error("❌ Failed to delete job {} for dependency {}: {}", jobId, dependency, e.getMessage());
        }
    }

    // Check if dependency queue exists
    public boolean isDependencyPresent(String dependency) {
        String key = "dep_queue:" + dependency;
        Boolean exists = redisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(exists)) {
            log.info("✅ Dependency {} is present in Redis", dependency);
            return true;
        } else {
            log.info("❌ Dependency {} is NOT present in Redis", dependency);
            return false;
        }
    }

    // ================== Job Done Methods (Type 1 auxiliary) ==================
    public void markJobDone(String jobName) {
        redisTemplate.opsForSet().add("done_jobs", jobName);
    }

    public boolean isJobDone(String jobName) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember("done_jobs", jobName));
    }

    // ================== Dependency Check Methods (Type 2) ==================

    // Mark a dependency as executed
    public void markJobDone(String dependency, LocalDateTime executedTime) {
        try {
            String key = "dep_check:" + dependency;
            double score = executedTime.toEpochSecond(ZoneOffset.UTC);

            Map<String, Boolean> flag = new HashMap<>();
            flag.put("check_dep_" + dependency, true);

            String json = mapper.writeValueAsString(flag);
            redisTemplate.opsForZSet().add(key, json, score);
            log.info("✅ Marked dependency {} as done at {}", dependency, executedTime);
        } catch (Exception e) {
            log.error("❌ Failed to mark dependency {} as done: {}", dependency, e.getMessage());
        }
    }

    // Check if a dependency is executed
    public boolean isDependencyDone(String dependency) {
        String key = "dep_check:" + dependency;
        Set<String> jobs = redisTemplate.opsForZSet().range(key, 0, -1);

        if (jobs != null) {
            for (String json : jobs) {
                try {
                    Map<String, Boolean> flag = mapper.readValue(
                            json, mapper.getTypeFactory().constructMapType(Map.class, String.class, Boolean.class));
                    if (flag.getOrDefault("check_dep_" + dependency, false)) {
                        return true;
                    }
                } catch (Exception e) {
                    log.error("❌ Failed to read dependency check JSON: {}", e.getMessage());
                }
            }
        }
        return false;
    }

    // Delete dependency check
    public void deleteDependencyCheck(String dependency) {
        String key = "dep_check:" + dependency;
        Boolean deleted = redisTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("🗑️ Deleted dependency check for {}", dependency);
        } else {
            log.warn("⚠️ No dependency check found to delete for {}", dependency);
        }
    }
}
