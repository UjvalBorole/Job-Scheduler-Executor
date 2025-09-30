package com.JobService.controllers;

import com.JobService.entities2.Payload;
import com.JobService.services.impl.PayloadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@RestController
@RequestMapping("/payloads")
public class PayloadController {

    @Autowired
    private PayloadService payloadService;

    // 🔹 Create Payload with file upload
    @PostMapping("/{jobId}")
    public ResponseEntity<String> createPayload(
            @PathVariable String jobId,
            @ModelAttribute Payload payload,
            @RequestParam("file") MultipartFile file) {

        try {
            // 1️⃣ Save the uploaded file to a directory accessible by Docker container
            String UPLOAD_DIR = "/data/uploads"; // you can change this to any path
            File dir = new File(UPLOAD_DIR);
            if (!dir.exists()) dir.mkdirs();

            String originalFileName = file.getOriginalFilename();
            String uniqueFileName = jobId + "_" + System.currentTimeMillis() + "_" + originalFileName;
            Path filePath = Path.of(UPLOAD_DIR, uniqueFileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            payload.setPath(filePath.toString());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("File upload failed: " + e.getMessage());
        }

        // 3️⃣ Save the payload (e.g., send to Kafka, database, etc.)
        boolean result = payloadService.create(payload, jobId);
        if (result) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body("Payload sent to Kafka for Job " + jobId);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to create payload.");
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
