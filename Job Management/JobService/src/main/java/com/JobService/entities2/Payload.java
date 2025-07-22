package com.JobService.entities2;

import com.JobService.entities1.Job;
import lombok.*;
import java.time.LocalDateTime;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payload {
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
