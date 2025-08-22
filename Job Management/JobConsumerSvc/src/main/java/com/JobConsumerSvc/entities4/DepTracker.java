package com.JobConsumerSvc.entities4;
//that table stores the cycles of the jobs to track the cycle for this table made when want's to track in real time then use jobruntable


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Data                       // generates getters, setters, equals, hashCode, toString
@Builder                    // builder pattern for easy object creation
@NoArgsConstructor          // no-args constructor
@AllArgsConstructor         // all-args constructor
@Document(collection = "dependency_tracker")  // MongoDB collection name
public class DepTracker {

    @Id
    private String id;              // MongoDB document ID

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

