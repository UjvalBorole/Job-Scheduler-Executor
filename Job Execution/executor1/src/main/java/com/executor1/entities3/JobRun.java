package com.executor1.entities3;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobRun {
    private Long id;
    private Long jobId;
    private RunStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime modifiedTime;
    private String executorId;
    private int attemptNumber;
    private String errorMsg;
}

