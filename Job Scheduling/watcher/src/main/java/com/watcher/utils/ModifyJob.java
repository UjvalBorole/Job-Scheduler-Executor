package com.watcher.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.watcher.entities1.Job;
import com.watcher.entities1.TaskStatus;
import com.watcher.entities3.JobRun;
import com.watcher.entities3.RunStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Service
public class ModifyJob {
    private final WebClient webClient;

    @Autowired
    public ModifyJob(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Patch only the specified fields of Job and JobRun.
     */
    public boolean updateJobAndRun(Long jobId, TaskStatus jobStatus, RunStatus runStatus, String errorMessage) {
        try {
            // 🛠 Dynamically build Job fields
            Map<String, Object> jobPatchBody = new HashMap<>();
            if (jobStatus != null) jobPatchBody.put("status", jobStatus);

            // 🛠 Dynamically build JobRun fields
            Map<String, Object> runPatchBody = new HashMap<>();
            if (runStatus != null) runPatchBody.put("status", runStatus);
            if (errorMessage != null)runPatchBody.put("errorMsg", errorMessage);

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

            // 🔄 Patch JobRun
            if (!runPatchBody.isEmpty()) {
                String runResp = webClient.patch()
                        .uri("/api/job-runs/{id}", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(runPatchBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                System.out.println("✅ Patched JobRun: " + runResp);
            }

            return true;
        } catch (Exception e) {
            System.err.println("❌ Error patching job/run: " + e.getMessage());
            return false;
        }
    }
}
