package com.JobConsumerSvc.mapper;

import com.JobConsumerSvc.dto.JobRunCacheDTO;
import com.JobConsumerSvc.entities3.JobRunCache;


public class JobRunCacheMapper {

    public static JobRunCacheDTO toDTO(JobRunCache entity) {
        if (entity == null) return null;

        return JobRunCacheDTO.builder()
                .jobId(entity.getJobId())
                .status(entity.getStatus())
                .startTime(entity.getStartTime())
                .executionTime(entity.getExecutionTime())
                .endTime(entity.getEndTime())
                .modifiedTime(entity.getModifiedTime())
                .executorId(entity.getExecutorId())
                .attemptNumber(entity.getAttemptNumber())
                .errorMsg(entity.getErrorMsg())
                .build();
    }

    public static JobRunCache fromDTO(JobRunCacheDTO dto) {
        if (dto == null) return null;

        return JobRunCache.builder()
                .jobId(dto.getJobId())
                .status(dto.getStatus())
                .startTime(dto.getStartTime())
                .executionTime(dto.getExecutionTime())
                .endTime(dto.getEndTime())
                .modifiedTime(dto.getModifiedTime())
                .executorId(dto.getExecutorId())
                .attemptNumber(dto.getAttemptNumber())
                .errorMsg(dto.getErrorMsg())
                .build();
    }
}
