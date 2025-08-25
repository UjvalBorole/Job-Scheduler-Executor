package com.executor1.config;

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
}
