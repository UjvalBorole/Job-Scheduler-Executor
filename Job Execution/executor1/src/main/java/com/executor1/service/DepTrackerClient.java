package com.executor1.service;

import com.executor1.entities1.Job;
import com.executor1.entities4.DepTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

//@Service
//public class DepTrackerClient {
//
//    private final WebClient webClientBuilder;
//    @Value("${webclient.base-url}")
//    private String baseUrl;
//
//    public DepTrackerClient(WebClient.Builder webClientBuilder) {
//        this.webClientBuilder = webClientBuilder.baseUrl("http://JOBCONSUMERSVC").build();
//    }
//
//    /**
//     * Call GET /api/deptracker/search?name=xxx and return the first matching job (or null).
//     */
//    public DepTracker findFirstByJobName(String name) {
//        List<DepTracker> results = webClientBuilder.get()
//                .uri(uriBuilder -> uriBuilder
//                        .path(baseUrl + "/api/deptracker/search")
//                        .queryParam("name", name)
//                        .build())
//                .retrieve()
//                .bodyToFlux(DepTracker.class)
//                .collectList()
//                .block();
//
//        return (results != null && !results.isEmpty()) ? results.get(0) : null;
//    }
//
//    /**
//     * Call POST /api/deptracker and create a new DepTracker entry.
//     */
//    public DepTracker createDepTracker(DepTracker depTracker) {
//        return webClientBuilder.post()
//                .uri(baseUrl + "/api/deptracker")
//                .bodyValue(depTracker)   // send full object as JSON
//                .retrieve()
//                .bodyToMono(DepTracker.class)  // return created DepTracker
//                .block();
//    }
//
//    /**
//     * Call GET /api/deptracker/dependency/{dependencyId}
//     * and return a list of DepTracker objects.
//     */
//    public List<DepTracker> getByDependency(String dependencyId) {
//        return webClientBuilder.get()
//                .uri(baseUrl +"/api/deptracker/dependency/{dependencyId}", dependencyId)
//                .retrieve()
//                .bodyToFlux(DepTracker.class)
//                .collectList()
//                .block(); // block() since you want sync return
//    }
//    /**
//     * Call GET /jobs/dependency/{dependency} to fetch jobs.
//     */
//    public List<Job> getJobsByDependency(String dependency) {
//        return webClientBuilder.get()
//                .uri(uriBuilder -> uriBuilder
//                        .path( baseUrl + "/jobs/jobsvc/dependency/{dependency}")
//                        .build(dependency))
//                .retrieve()
//                .bodyToFlux(Job.class)
//                .collectList()
//                .block(); // blocking for simplicity, can use reactive if needed
//    }

//}

@Service
public class DepTrackerClient {

    private final WebClient webClient;
////    @Value("${webclient.base-url}")
//    private String baseUrl;
    public DepTrackerClient(WebClient.Builder webClientBuilder, @Value("${webclient.base-url}") String baseUrl) {
        // If using Eureka/LoadBalancer, service name works
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    /**
     * GET /api/deptracker/search?name=xxx
     */
    public DepTracker findFirstByJobName(String name) {
        List<DepTracker> results = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/deptracker/search")   // ✅ only relative path
                        .queryParam("name", name)
                        .build())
                .retrieve()
                .bodyToFlux(DepTracker.class)
                .collectList()
                .block();

        return (results != null && !results.isEmpty()) ? results.get(0) : null;
    }

    /**
     * POST /api/deptracker
     */
    public DepTracker createDepTracker(DepTracker depTracker) {
        return webClient.post()
                .uri("/api/deptracker")   // ✅ relative path only
                .bodyValue(depTracker)
                .retrieve()
                .bodyToMono(DepTracker.class)
                .block();
    }

    /**
     * GET /api/deptracker/dependency/{dependencyId}
     */
    public List<DepTracker> getByDependency(String dependencyId) {
        return webClient.get()
                .uri("/api/deptracker/dependency/{dependencyId}", dependencyId)
                .retrieve()
                .bodyToFlux(DepTracker.class)
                .collectList()
                .block();
    }

    /**
     * GET /jobs/jobsvc/dependency/{dependency}
     */
//    public List<Job> getJobsByDependency(String dependency) {
//        return webClient.get()
//                .uri("/jobs/jobsvc/dependency/{dependency}", dependency)
//                .retrieve()
//                .bodyToFlux(Job.class)
//                .collectList()
//                .block();
//    }

    public List<Job> getJobsByDependency(String dependency) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/jobs/jobsvc/dependency/{dependency}")
                        .build(dependency))
                .retrieve()
                .bodyToFlux(Job.class)
                .collectList()
                .block();
    }
}
