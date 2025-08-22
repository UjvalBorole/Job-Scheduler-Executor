package com.JobConsumerSvc.entities2;

import com.JobConsumerSvc.entities1.Job;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payloads")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Integer seqId;

    @Enumerated(EnumType.STRING)
    private RunStatus status = RunStatus.PENDING;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime modifiedTime;

    private String executorId;
    private Integer attemptNumber;

    @Column(columnDefinition = "TEXT")
    private String errorMsg;
    private String path;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    @JsonBackReference
    private Job job;
}
