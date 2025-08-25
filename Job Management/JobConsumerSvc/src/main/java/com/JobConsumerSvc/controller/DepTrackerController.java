package com.JobConsumerSvc.controller;

import com.JobConsumerSvc.dto.DepTrackerDTO;
import com.JobConsumerSvc.entities4.DepTracker;
import com.JobConsumerSvc.entities4.JobStatus;
import com.JobConsumerSvc.service.DepTrackerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deptracker")
@RequiredArgsConstructor
public class DepTrackerController {

    private final DepTrackerService depTrackerService;

    @PostMapping
    public ResponseEntity<DepTracker> createDepTracker(@RequestBody DepTrackerDTO dto) {
        DepTracker depTracker =  depTrackerService.createDepTracker(dto);
        return ResponseEntity.ok(depTracker);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<DepTracker> patchDepTracker(@PathVariable String id,
                                      @RequestBody DepTrackerDTO dto) {
        DepTracker depTracker = depTrackerService.patchDepTracker(id, dto);
        return ResponseEntity.ok(depTracker);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteDepTracker(@PathVariable String id) {
        depTrackerService.deleteDepTracker(id);
        return ResponseEntity.ok("DepTracker with id=" + id + " deleted successfully.");
    }


    // 1. Get all jobs
    @GetMapping
    public List<DepTracker> getAll() {
        return depTrackerService.getAll();
    }

    // 2. Get job by MongoDB ID
    @GetMapping("/{id}")
    public DepTracker getById(@PathVariable String id) {
        return depTrackerService.getById(id);
    }

    // 3. Get jobs by jobId (business ID)
    @GetMapping("/job/{jobId}")
    public List<DepTracker> getByJobId(@PathVariable String jobId) {
        return depTrackerService.getByJobId(jobId);
    }

    // 4. Get jobs by status
    @GetMapping("/status/{status}")
    public List<DepTracker> getByJobStatus(@PathVariable JobStatus status) {
        return depTrackerService.getByJobStatus(status);
    }

    // 5. Get jobs by executor
    @GetMapping("/executor/{executorId}")
    public List<DepTracker> getByExecutorId(@PathVariable String executorId) {
        return depTrackerService.getByExecutorId(executorId);
    }

    // 6. Get jobs that depend on a specific jobId
    @GetMapping("/dependency/{dependencyId}")
    public List<DepTracker> getByDependency(@PathVariable String dependencyId) {
        return depTrackerService.getByDependency(dependencyId);
    }

    // 7. Search jobs by jobName (case-insensitive contains)  GET /dep-tracker/search?name=etl
    @GetMapping("/search")
    public List<DepTracker> searchByJobName(@RequestParam String name) {
        return depTrackerService.searchByJobName(name);
    }

}
