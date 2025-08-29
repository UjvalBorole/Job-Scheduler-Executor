package com.JobConsumerSvc.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.JobConsumerSvc.dto.JobDTO;
import com.JobConsumerSvc.entities1.Job;
import com.JobConsumerSvc.entities1.JobEvent;
import com.JobConsumerSvc.mapper.JobMapper;
import com.JobConsumerSvc.repositories.JobRepository;
import com.JobConsumerSvc.service.JobService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobRepository jobRepository;

    @Autowired
    private JobService jobService;


    @GetMapping
    public ResponseEntity<List<JobDTO>> getAllJobs() {
        List<JobDTO> jobDTOs = jobRepository.findAll()
                .stream()
                .map(JobMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(jobDTOs);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobDTO> getJobById(@PathVariable Long jobId) {
        return jobRepository.findById(jobId)
                .map(JobMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }



    @GetMapping("/payload/{id}")
    public ResponseEntity<Job> getJobWithPayloads(@PathVariable Long id) {
        Job job = jobService.getJobWithPayloads(id);
        return ResponseEntity.ok(job);
    }

    @GetMapping("/latest")
    public ResponseEntity<Job> getLatestJob() {
        System.out.println("Fetching latest job...hyyyyy");
        Job latestJob = jobService.getLatestJob();
        return ResponseEntity.ok(latestJob);
    }
    /**
     * Endpoint to modify an existing job using JobEvent DTO.
     * Simulates an event coming from client for testing or REST update.
     */
    @PatchMapping("/{jobId}")
    public ResponseEntity<String> modifyJob(
            @PathVariable String jobId,
            @RequestBody JobDTO jobDTO) {

        JobEvent jobEvent = new JobEvent();
        jobEvent.setJobId(jobId);
        jobEvent.setJob(jobDTO);

        try {
            jobService.handleModify(jobEvent);
            return ResponseEntity.ok("✅ Job modified successfully.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("❌ Failed to modify job: " + e.getMessage());
        }
    }

    // ✅ GET /api/jobs/dependency/{dependency}
    @GetMapping("/dependency/{dependency}")
    public List<Job> getJobsByDependency(@PathVariable String dependency) {
        return jobService.getJobsByDependency(dependency);
    }
}
