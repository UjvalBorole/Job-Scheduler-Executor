package com.executor1.entities3;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class JobRun {


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
