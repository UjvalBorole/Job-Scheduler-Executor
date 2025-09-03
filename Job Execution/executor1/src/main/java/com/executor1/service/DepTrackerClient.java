package com.executor1.service;

import com.executor1.entities1.Job;
import com.executor1.entities4.DepTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepTrackerClient {

    private final WebClient webClient;

    /**
     * Call GET /api/deptracker/search?name=xxx and return the first matching job (or null).
     */
    public DepTracker findFirstByJobName(String name) {
        List<DepTracker> results = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/deptracker/search")
                        .queryParam("name", name)
                        .build())
                .retrieve()
                .bodyToFlux(DepTracker.class)
                .collectList()
                .block();

        return (results != null && !results.isEmpty()) ? results.get(0) : null;
    }

    /**
     * Call POST /api/deptracker and create a new DepTracker entry.
     */
    public DepTracker createDepTracker(DepTracker depTracker) {
        return webClient.post()
                .uri("/api/deptracker")
                .bodyValue(depTracker)   // send full object as JSON
                .retrieve()
                .bodyToMono(DepTracker.class)  // return created DepTracker
                .block();
    }

    /**
     * Call GET /api/deptracker/dependency/{dependencyId}
     * and return a list of DepTracker objects.
     */
    public List<DepTracker> getByDependency(String dependencyId) {
        return webClient.get()
                .uri("/api/deptracker/dependency/{dependencyId}", dependencyId)
                .retrieve()
                .bodyToFlux(DepTracker.class)
                .collectList()
                .block(); // block() since you want sync return
    }
    /**
     * Call GET /jobs/dependency/{dependency} to fetch jobs.
     */
    public List<Job> getJobsByDependency(String dependency) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/jobs/dependency/{dependency}")
                        .build(dependency))
                .retrieve()
                .bodyToFlux(Job.class)
                .collectList()
                .block(); // blocking for simplicity, can use reactive if needed
    }

}
