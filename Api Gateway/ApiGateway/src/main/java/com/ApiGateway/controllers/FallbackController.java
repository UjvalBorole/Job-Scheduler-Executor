package com.ApiGateway.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FallbackController {

    @GetMapping("/fallback/jobconsumer")
    public ResponseEntity<String> jobConsumerFallback() {
        return ResponseEntity.ok("⚠️ JobConsumerSvc is unavailable. Please try again later.");
    }

    @GetMapping("/fallback/jobservice")
    public ResponseEntity<String> jobServiceFallback() {
        return ResponseEntity.ok("⚠️ JobService is unavailable. Please try again later.");
    }
}
