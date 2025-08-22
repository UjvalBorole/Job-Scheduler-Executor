package com.JobConsumerSvc.dto;



import com.JobConsumerSvc.entities2.RunStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayloadRequest {
    private Long jobId;
    private String name;
    private Integer seqId;
    private RunStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime modifiedTime;
    private String executorId;
    private Integer attemptNumber;
    private String errorMsg;
    private String path;
}
