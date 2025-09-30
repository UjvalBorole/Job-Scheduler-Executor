package com.watcher.services;

import com.watcher.entities1.Job;
import com.watcher.entities1.ScheduleType;
import com.watcher.entities1.TaskStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class FetchLatestJob {


    private Long lastFetchedJobId = 0L;
    private LocalDateTime lastModifiedTime = null;

    @Value("${webclient.base-url}")
    private String baseUrl;
    private final WebClient webClientBuilder;

    public FetchLatestJob(WebClient.Builder webClientBuilder,
                          @Value("${webclient.base-url}") String baseUrl) {
        this.webClientBuilder = webClientBuilder.baseUrl(baseUrl).build();
    }


    public Job fetchLatestJob() {
        Job job = null;

        try {

            Job latestJob = webClientBuilder.get()
                    .uri("/jobs/jobsvc/latest")   // relative path only
                    .retrieve()
                    .bodyToMono(Job.class)
                    .block();

//            System.out.println(latestJob +" this is the latest job");
            if (latestJob != null && TaskStatus.READY.equals(latestJob.getStatus()) && ScheduleType.CRON.equals(latestJob.getScheduleType())) {

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
