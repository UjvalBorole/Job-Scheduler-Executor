package com.watcher.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${webclient.base-url}")
    private String baseUrl;

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        System.out.println("Creating WebClient with base URL: " + baseUrl);
        return builder
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Host", "localhost:8082")  // Add this line to fix the Host header issue
                .filter((request, next) -> {
                    System.out.println("WebClient Request:");
                    System.out.println("URL: " + request.url());
                    System.out.println("Method: " + request.method());
                    System.out.println("Headers: " + request.headers());
                    return next.exchange(request);
                })
                .build();
    }
}