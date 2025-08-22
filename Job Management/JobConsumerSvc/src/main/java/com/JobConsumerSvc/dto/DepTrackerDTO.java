package com.JobConsumerSvc.dto;

import com.JobConsumerSvc.entities4.JobStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data                       // generates getters, setters, equals, hashCode, toString
public class DepTrackerDTO {

    private String jobId;           // Unique Job ID
    private String jobName;         // Human-readable name
    private JobStatus jobStatus;    // Enum: READY, RUNNING, SUCCESS, FAILED, etc.

    private List<String> dependencies;  // parent job IDs

    private Integer retryCount;         // current retry attempts
    private Integer maxRetries;         // max allowed retries

    private LocalDateTime scheduleTime; // when job was scheduled
    private LocalDateTime startTime;    // execution start
    private LocalDateTime endTime;      // execution end
    private String nextCron;            // cron for recurring jobs

    private String executorId;      // which executor processed it
    private String errorMessage;    // error details if failed

}

