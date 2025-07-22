package com.JobConsumerSvc.dto;

import com.JobConsumerSvc.entities3.RunStatus;
import lombok.*;

import java.time.LocalDateTime;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobRunDTO {
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
