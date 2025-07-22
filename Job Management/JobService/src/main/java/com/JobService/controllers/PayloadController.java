package com.JobService.controllers;

import com.JobService.entities2.Payload;
import com.JobService.services.impl.PayloadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payloads")
public class PayloadController {

    @Autowired
    private PayloadService payloadService;

    // 🔹 Create Payload
    @PostMapping("/{jobId}")
    public ResponseEntity<String> createPayload(@RequestBody Payload payload,@PathVariable String jobId) {
        boolean result = payloadService.create(payload,jobId);
        if (result) {
            return ResponseEntity.status(HttpStatus.CREATED).body("Payload  sent to Kafka of Job "+jobId);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create payload.");
        }
    }

    // 🔹 Modify Payload
    @PostMapping("/modify/{payloadId}")
    public ResponseEntity<String> modifyPayload(@RequestBody Payload payload, @PathVariable String payloadId) {
        boolean result = payloadService.update(payload, payloadId);
        if (result) {
            return ResponseEntity.ok("Payload update sent to Kafka.");
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to update payload.");
        }
    }

    // 🔹 Delete Payload
    @DeleteMapping("/delete/{payloadId}")
    public ResponseEntity<String> deletePayload(@PathVariable String payloadId) {
        boolean result = payloadService.delete(payloadId);
        if (result) {
            return ResponseEntity.ok("Payload deleted via Kafka.");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Payload not found.");
        }
    }
}
