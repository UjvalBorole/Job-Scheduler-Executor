package com.JobConsumerSvc.entities1;

import com.JobConsumerSvc.entities2.Payload;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(
        name = "jobs",
        indexes = {
                @Index(name = "idx_modified_time", columnList = "modifiedTime")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String jobSeqId;
    private String name;

    @Enumerated(EnumType.STRING)
    private ScheduleType scheduleType;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    /**
     * Dependencies stored in a separate table (job_dependencies)
     * Each entry will have job_id + dependency string
     */
    @ElementCollection
    @CollectionTable(
            name = "job_dependencies",
            joinColumns = @JoinColumn(name = "job_id")
    )
    @Column(name = "dependency")
    private List<String> dependencies;

    private LocalDateTime scheduleTime;
    private String cronExpression;
    private int retries;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Payload> payloads;

    private String email;

    @Column(columnDefinition = "TEXT")
    private String meta;

    private LocalDateTime modifiedTime;

    @PreUpdate
    public void onUpdate() {
        this.modifiedTime = LocalDateTime.now();
    }

    @PrePersist
    public void onCreate() {
        this.modifiedTime = LocalDateTime.now();
    }
}
