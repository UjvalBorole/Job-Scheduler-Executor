package com.JobService.controllers;

import com.JobService.entities1.Job;

import com.JobService.services.impl.JobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/jobs")
public class JobController {

    @Autowired
    private JobService jobService;

    // 🔹 Create Job (CRUD — goes to DB and Kafka CRUD topic)
    @PostMapping
    public ResponseEntity<String> createJob(@RequestBody Job job) {
        boolean result = jobService.create(job);
        if (result) {
            return ResponseEntity.status(HttpStatus.CREATED).body("Job create Request  sent to JobService.");
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create job Request.");
        }
    }

    // 🔹 Add Immediate Job (no DB — goes to Kafka executor topic)
    @PostMapping("/modify/{jobId}")
    public ResponseEntity<String> modify(@RequestBody Job job,@PathVariable String jobId) {
        boolean result = jobService.modify(job,jobId);
        if (result) {
            return ResponseEntity.status(HttpStatus.CREATED).body("Modify Request sent to Job service.");
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to Modify job Request.");
        }
    }

    // 🔹 Cancel Job by ID
    @DeleteMapping("/delete/{jobId}")
    public ResponseEntity<String> deleteJob(@PathVariable String jobId) {
        boolean result = jobService.delete(jobId);
        if (result) {
            return ResponseEntity.ok("Job " + jobId + " cancel Request sent successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found or already completed.");
        }
    }

    // Uncomment if needed
//    @GetMapping("/{jobId}")
//    public ResponseEntity<Job> getJob(@PathVariable String jobId) {
//        return ResponseEntity.status(HttpStatus.OK).body(jobService.get(jobId));
//    }
//
//    @GetMapping
//    public ResponseEntity<List<Job>> getAllJobs() {
//        return ResponseEntity.ok(jobService.getAll());
//    }
}
