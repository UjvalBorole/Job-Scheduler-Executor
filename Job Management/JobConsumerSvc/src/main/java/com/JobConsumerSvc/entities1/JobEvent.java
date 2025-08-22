package com.JobConsumerSvc.entities1;


import com.JobConsumerSvc.dto.JobDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobEvent {

    private JobEventType eventType; // CREATE / ADD / DELETE
    private JobDTO job;                // Optional
    private String jobId;           // Only for DELETE
}
