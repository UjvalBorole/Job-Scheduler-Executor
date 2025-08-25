package com.JobConsumerSvc.entities3;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
//@RedisHash("jobrun")  // Redis "table" name (namespace/prefix)
public class JobRunCache  {

    @Id
    private Long jobId;   // Foreign key reference

    private RunStatus status; // QUEUED, RUNNING, SUCCESS, FAILED
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime modifiedTime;
    private String executorId;
    private Integer attemptNumber;
    private LocalDateTime executionTime;
    private String errorMsg; // stored as TEXT
}
