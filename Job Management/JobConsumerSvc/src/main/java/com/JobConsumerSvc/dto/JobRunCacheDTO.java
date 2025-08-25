package com.JobConsumerSvc.dto;


import com.JobConsumerSvc.entities3.RunStatus;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobRunCacheDTO {

    private Long jobId;           // Foreign key reference
    private RunStatus status;     // QUEUED, RUNNING, SUCCESS, FAILED
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime modifiedTime;
    private String executorId;
    private Integer attemptNumber;
    private LocalDateTime executionTime;
    private String errorMsg;
}
