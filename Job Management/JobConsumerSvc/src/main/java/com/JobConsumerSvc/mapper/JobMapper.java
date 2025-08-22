package com.JobConsumerSvc.mapper;

import com.JobConsumerSvc.dto.JobDTO;
import com.JobConsumerSvc.dto.JobRunDTO;
import com.JobConsumerSvc.entities1.Job;
import com.JobConsumerSvc.entities3.JobRun;

public class JobMapper {
    public static JobDTO toDTO(Job job) {
        return JobDTO.builder()
                .id(job.getId())
                .name(job.getName())
                .scheduleType(job.getScheduleType())
                .status(job.getStatus())
                .scheduleTime(job.getScheduleTime())
                .cronExpression(job.getCronExpression())
                .payload(job.getPayloads())
                .retries(job.getRetries())
                .dependencies(job.getDependencies())
                .email(job.getEmail())
                .meta(job.getMeta())
                .build();
    }

    public static JobRunDTO toDTO(JobRun run) {
        return JobRunDTO.builder()
                .id(run.getId())
                .jobId(run.getJobId())
                .status(run.getStatus())
                .startTime(run.getStartTime())
                .endTime(run.getEndTime())
                .modifiedTime(run.getModifiedTime())
                .executorId(run.getExecutorId())
                .attemptNumber(run.getAttemptNumber())
                .errorMsg(run.getErrorMsg())
                .build();
    }
    public static Job fromDTO(JobDTO dto) {
        return Job.builder()
                .id(dto.getId())
                .name(dto.getName())
                .jobSeqId(dto.getJobSeqId())
                .scheduleType(dto.getScheduleType())
                .status(dto.getStatus())
                .scheduleTime(dto.getScheduleTime())
                .cronExpression(dto.getCronExpression())
                .payloads(dto.getPayload()) // Assuming `Job` uses `payloads`, and `JobDTO` uses `payload`
                .retries(dto.getRetries())
                .dependencies(dto.getDependencies())
                .email(dto.getEmail())
                .meta(dto.getMeta())
                .build();
    }

}
