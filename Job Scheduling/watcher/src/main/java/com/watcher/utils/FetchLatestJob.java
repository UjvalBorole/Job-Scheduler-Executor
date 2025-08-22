package com.watcher.utils;

import com.watcher.entities1.Job;
import com.watcher.entities1.TaskStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class FetchLatestJob {

    private final WebClient webClient;

    private Long lastFetchedJobId = 0L;
    private LocalDateTime lastModifiedTime = null;

    @Autowired
    public FetchLatestJob(WebClient webClient) {
        this.webClient = webClient;
    }

    public Job fetchLatestJob() {
        Job job = null;

        try {
            Job latestJob = webClient.get()
                    .uri("/jobs/latest")
                    .retrieve()
                    .bodyToMono(Job.class)
                    .block();  // blocking call
            System.out.println(latestJob +" this is the latest job");
            if (latestJob != null && TaskStatus.READY.equals(latestJob.getStatus())) {

                Long currentJobId = latestJob.getId();
                LocalDateTime currentModified = latestJob.getModifiedTime();

                LocalDateTime roundedCurrentModified = currentModified != null
                        ? currentModified.truncatedTo(ChronoUnit.MINUTES)
                        : null;

                LocalDateTime roundedLastModified = lastModifiedTime != null
                        ? lastModifiedTime.truncatedTo(ChronoUnit.MINUTES)
                        : null;

                boolean isNewJob = !currentJobId.equals(lastFetchedJobId);
                boolean isModified = (roundedCurrentModified != null && !roundedCurrentModified.equals(roundedLastModified));

                if (!isNewJob && !isModified) {
                    System.out.println("⏳ No new or updated job (same ID and modifiedTime).");
                    return null;
                }

                System.out.println("✅ New or updated READY job: " + latestJob.getName());

                // update stored values
                lastFetchedJobId = currentJobId;
                lastModifiedTime = currentModified;

                job = latestJob;

            } else {
                System.out.println("⏳ No READY job found.");
            }

        } catch (Exception e) {
            System.err.println("❌ Error fetching job via WebClient: " + e.getMessage());
        }

        return job;
    }
}
