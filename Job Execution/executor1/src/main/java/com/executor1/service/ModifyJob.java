package com.executor1.service;
import com.executor1.entities1.TaskStatus;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class ModifyJob {

    private final WebClient webClient;


    @Autowired
    public ModifyJob(WebClient webClient) {
        this.webClient = webClient;
    }


    /**
     * Update job run details (status, timestamps, error message, etc.)
     */
    public boolean updateJobAndRun(Long jobId,
                                   TaskStatus jobStatus,
                                   String errorMessage

    ) {
        try {
            // 🛠 Dynamically build Job fields
            Map<String, Object> jobPatchBody = new HashMap<>();
            if (jobStatus != null) jobPatchBody.put("status", jobStatus);
            System.out.println("JobStatus "+ jobStatus);

            // 🔄 Patch Job
            if (!jobPatchBody.isEmpty()) {
                String jobResp = webClient.patch()
                        .uri("/jobs/{id}", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(jobPatchBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                System.out.println("✅ Patched Job: " + jobResp);
            }

            return true;
        } catch (Exception e) {
            System.err.println("❌ Error patching job/run: " + e.getMessage());
            return false;
        }
    }

}
