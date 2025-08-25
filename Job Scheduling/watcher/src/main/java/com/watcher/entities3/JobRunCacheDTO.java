package com.watcher.entities3;



import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class JobRunCacheDTO {


    private Long jobId;

    private RunStatus status; // QUEUED, RUNNING, SUCCESS, FAILED
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime modifiedTime;
    private String executorId;
    private Integer attemptNumber;
    private LocalDateTime executionTime;
    private String errorMsg; // stored as TEXT


}
