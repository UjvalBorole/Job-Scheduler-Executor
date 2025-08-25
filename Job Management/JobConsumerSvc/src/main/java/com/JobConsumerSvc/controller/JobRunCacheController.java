package com.JobConsumerSvc.controller;

import com.JobConsumerSvc.dto.JobRunCacheDTO;
import com.JobConsumerSvc.entities3.RunStatus;
import com.JobConsumerSvc.service.JobRunCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/jobruns")
@RequiredArgsConstructor
public class JobRunCacheController {

    private final JobRunCacheService service;

    @PostMapping
    public ResponseEntity<JobRunCacheDTO> create(@RequestBody JobRunCacheDTO dto) {
        return ResponseEntity.ok(service.create(dto));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<?> getByJobId(@PathVariable Long jobId) {
        JobRunCacheDTO dto = service.get(jobId);
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.notFound().build();
    }

    @PutMapping("/{jobId}")
    public ResponseEntity<JobRunCacheDTO> update(@PathVariable Long jobId,
                                                 @RequestBody JobRunCacheDTO dto) {
        return ResponseEntity.ok(service.update(jobId, dto));
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> delete(@PathVariable Long jobId) {
        service.delete(jobId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{jobId}")
    public ResponseEntity<JobRunCacheDTO> patchUpdate(@PathVariable Long jobId,
                                                      @RequestBody JobRunCacheDTO dto) {
        return ResponseEntity.ok(service.patchUpdate(jobId, dto));
    }

    // Since Redis is just key-value, these two will scan all keys and filter in-memory:
    @GetMapping("/range")
    public ResponseEntity<List<JobRunCacheDTO>> getByModifiedTimeRange(@RequestParam("start") String start,
                                                                       @RequestParam("end") String end) {
        LocalDateTime startTime = LocalDateTime.parse(start);
        LocalDateTime endTime = LocalDateTime.parse(end);
        return ResponseEntity.ok(service.getByModifiedTimeRange(startTime, endTime));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<JobRunCacheDTO>> getByStatus(@PathVariable RunStatus status) {
        return ResponseEntity.ok(service.getByStatus(status));
    }
}
