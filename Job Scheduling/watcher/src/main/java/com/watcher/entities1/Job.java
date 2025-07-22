package com.watcher.entities1;

import com.watcher.entities2.Payload;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {
    private Long id;
    private String jobSeqId;
    private String name;
    private ScheduleType scheduleType;
    private TaskStatus status;
    private LocalDateTime scheduleTime;
    private String cronExpression;
    private String dependencies;
    private int retries;
    private List<Payload> payload;
    private String email;
    private String meta;
    private LocalDateTime modifiedTime;
}

