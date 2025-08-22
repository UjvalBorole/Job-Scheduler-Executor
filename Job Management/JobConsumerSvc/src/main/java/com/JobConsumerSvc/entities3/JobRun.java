package com.JobConsumerSvc.entities3;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_runs", indexes = {
        @Index(name = "idx_job_id", columnList = "jobId"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_modified_time", columnList = "modifiedTime")
})

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long jobId;  // Foreign key reference

    @Enumerated(EnumType.STRING)
    private RunStatus status;  // QUEUED, RUNNING, SUCCESS, FAILED

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime modifiedTime;

    private String executorId;

    private int attemptNumber;

    @Column(columnDefinition = "TEXT")
    private String errorMsg;
}
