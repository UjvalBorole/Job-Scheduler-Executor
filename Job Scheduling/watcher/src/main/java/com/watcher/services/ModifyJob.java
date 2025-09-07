package com.watcher.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.watcher.entities3.JobRunCacheDTO;
import com.watcher.entities1.TaskStatus;
import com.watcher.entities3.RunStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class ModifyJob {

    private final WebClient webClientBuilder;
    private final JobRunCacheService jobRunCacheService;

    @Value("${webclient.base-url}")
    private String baseUrl;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper mapper;

    @Autowired
    public ModifyJob(WebClient.Builder webClientBuilder,
                     JobRunCacheService jobRunCacheService,
                     RedisTemplate<String, Object> redisTemplate,
                     ObjectMapper mapper,
                     @Value("${webclient.base-url}") String baseUrl) {
        this.webClientBuilder = webClientBuilder.baseUrl(baseUrl).build();
        this.jobRunCacheService = jobRunCacheService;
        this.redisTemplate = redisTemplate;
        this.mapper = mapper;
    }



    /**
     * Update job run details (status, timestamps, error message, etc.)
     */
    public boolean updateJobAndRun(Long jobId,
                                   TaskStatus jobStatus,
                                   RunStatus newStatus,   // 🔄 use RunStatus here, not TaskStatus
                                   String errorMessage,
                                   LocalDateTime executionTime
    ) {
        try {
            // 🛠 Dynamically build Job fields
            Map<String, Object> jobPatchBody = new HashMap<>();
            if (jobStatus != null) jobPatchBody.put("status", jobStatus);



            // 🔄 Patch Job
            if (!jobPatchBody.isEmpty()) {
                String jobResp = webClientBuilder.patch()
                        .uri("/jobs/jobsvc/{id}", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(jobPatchBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                log.info("✅ Patched Job {} response={}", jobId, jobResp);


            }

            JobRunCacheDTO existing = jobRunCacheService.get(jobId);
            if (existing == null) {
                throw new IllegalArgumentException("JobRun not found in Redis: " + jobId);
            }

            // 🔧 Update status
            if (newStatus != null) {
                existing.setStatus(newStatus);
                existing.setModifiedTime(LocalDateTime.now());

                if (newStatus == RunStatus.RUNNING) {
                    existing.setStartTime(LocalDateTime.now());
                }
                if (newStatus == RunStatus.SUCCESS || newStatus == RunStatus.FAILED) {
                    existing.setEndTime(LocalDateTime.now());
                }
            }

            // 🔧 Update error message if present
            if (errorMessage != null) {
                existing.setErrorMsg(errorMessage);
            }

            // 🔧 Update scheduled/execution time if provided
            if (executionTime != null) {
                existing.setExecutionTime(executionTime);
            }

            // ✅ Save back into Redis (JSON overwrite)
            JobRunCacheDTO updated = jobRunCacheService.update(jobId, existing);
            log.info("✅ Update JobRun {} to status={}, executionTime={}", jobId, newStatus, executionTime);

            return true;
        } catch (Exception e) {
            System.err.println("❌ Error patching job/run: " + e.getMessage());
            return false;
        }
    }

}
