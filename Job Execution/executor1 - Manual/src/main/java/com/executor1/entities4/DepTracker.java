package com.executor1.entities4;
//that table stores the cycles of the jobs to track the cycle for this table made when want's to track in real time then use jobruntable

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.time.LocalDateTime;
import java.util.List;

@Data                       // generates getters, setters, equals, hashCode, toString
@Builder                    // builder pattern for easy object creation
@NoArgsConstructor          // no-args constructor
@AllArgsConstructor         // all-args constructor
public class DepTracker {
    private String Id;
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

