package com.executor1.entities1;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobEvent {

    private JobEventType eventType; // CREATE / ADD / DELETE
    private Job job;                // Optional
    private String jobId;           // Only for DELETE
}
