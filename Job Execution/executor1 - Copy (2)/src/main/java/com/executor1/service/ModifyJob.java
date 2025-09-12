package com.executor1.service;

import com.executor1.entities1.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class ModifyJob {

    private final WebClient webClient;

    public ModifyJob(WebClient.Builder webClientBuilder,
                     @Value("${webclient.base-url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    /**
     * Update job run details (status, error message, etc.)
     */
    public boolean updateJobAndRun(Long jobId,
                                   TaskStatus jobStatus,
                                   String errorMessage) {
        try {
            Map<String, Object> jobPatchBody = new HashMap<>();
            if (jobStatus != null) {
                jobPatchBody.put("status", jobStatus);
                log.info("JobStatus = {}", jobStatus);
            }
            if (errorMessage != null) {
                jobPatchBody.put("errorMessage", errorMessage);
            }

            if (!jobPatchBody.isEmpty()) {
                String jobResp = webClient.patch()
                        .uri("/jobs/jobsvc/{id}", jobId) // relative path
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(jobPatchBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                log.info("✅ Patched Job {}: {}", jobId, jobResp);
            }

            return true;
        } catch (Exception e) {
            log.error("❌ Error patching job/run for jobId={}", jobId, e);
            return false;
        }
    }
}
