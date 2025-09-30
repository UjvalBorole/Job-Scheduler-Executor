package com.JobConsumerSvc.dto;


import com.JobConsumerSvc.entities1.ScheduleType;
import com.JobConsumerSvc.entities1.TaskStatus;
import com.JobConsumerSvc.entities2.Payload;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobDTO {
    private Long id;
    private String jobSeqId;
    private String name;
    private ScheduleType scheduleType;
    private TaskStatus status;
    private LocalDateTime scheduleTime;
    private String cronExpression;
    private List<String> dependencies;
    private int retries;
    private List<Payload> payloads;
    private String email;
    private String meta;
    private LocalDateTime modifiedTime;
}

