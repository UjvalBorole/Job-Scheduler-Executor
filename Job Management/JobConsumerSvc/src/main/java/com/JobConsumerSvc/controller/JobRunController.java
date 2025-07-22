package com.JobConsumerSvc.controller;

import com.JobConsumerSvc.dto.JobRunDTO;
import com.JobConsumerSvc.entities3.JobRun;
import com.JobConsumerSvc.dto.JobRunWithJobDTO;
import com.JobConsumerSvc.service.JobRunService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/job-runs")
public class JobRunController {

    @Autowired
    private JobRunService jobRunService;

    // ✅ Update JobRun (excluding id and jobId)
    @PatchMapping("/{id}")
    public JobRun updateJobRun(@PathVariable Long id, @RequestBody JobRunDTO jobRunDTO) {
        System.out.println(jobRunDTO);
        return jobRunService.updateJobRun(id, jobRunDTO);
    }

    // ✅ Get by JobRun ID
    @GetMapping("/{id}")
    public JobRunWithJobDTO getById(@PathVariable Long id) {
        return jobRunService.getById(id);
    }

    // ✅ Get by Job ID
    @GetMapping("/by-job/{jobId}")
    public JobRunWithJobDTO getByJobId(@PathVariable Long jobId) {
        return jobRunService.getByJobId(jobId);
    }

    // ✅ Get by Run Status
    @GetMapping("/status/{status}")
    public List<JobRunWithJobDTO> getByStatus(@PathVariable String status) {
        return jobRunService.getByStatus(status);
    }

    // ✅ Get by Modified Time Range
    @GetMapping("/modified")
    public List<JobRunWithJobDTO> getByModifiedTime(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return jobRunService.getByModifiedTimeRange(from, to);
    }
}
