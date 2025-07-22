package com.JobConsumerSvc.dto;

import com.JobConsumerSvc.entities1.Job;
import com.JobConsumerSvc.entities3.JobRun;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JobRunWithJobDTO {
    private JobRun jobRun;
    private Job job;
}
