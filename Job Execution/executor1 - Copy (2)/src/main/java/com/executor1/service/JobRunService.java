package com.executor1.service;

import com.executor1.entities3.JobRun;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobRunService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper mapper;
    private static final String PREFIX = "jobrun:";

    private String key(Long jobId) {
        return PREFIX + jobId;
    }

    // ✅ Create
    public JobRun create(JobRun dto) {
        try {
            String json = mapper.writeValueAsString(dto);
            redisTemplate.opsForValue().set(key(dto.getJobId()), json);
            return dto;
        } catch (Exception e) {
            throw new RuntimeException("Failed to save jobrun:" + dto.getJobId(), e);
        }
    }
    
    // ✅ Delete
    public void delete(Long jobId) {
        redisTemplate.delete(key(jobId));
    }
    // ✅ Get by ID
    public JobRun get(Long jobId) {
        try {
            String json = redisTemplate.opsForValue().get(key(jobId));
            return json != null ? mapper.readValue(json, JobRun.class) : null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get jobrun:" + jobId, e);
        }
    }
    // ✅ Patch update
    public JobRun patchUpdate(Long jobId, JobRun patch) {
        JobRun existing = get(jobId);
        if (existing == null) throw new RuntimeException("JobRun not found: " + jobId);

        if (patch.getStatus() != null) existing.setStatus(patch.getStatus());
        if (patch.getModifiedTime() != null) existing.setModifiedTime(patch.getModifiedTime());
        if (patch.getExecutorId() != null) existing.setExecutorId(patch.getExecutorId());
        if (patch.getErrorMsg() != null) existing.setErrorMsg(patch.getErrorMsg());
        if (patch.getStartTime() != null) existing.setStartTime(patch.getStartTime());
        if (patch.getEndTime() != null) existing.setEndTime(patch.getEndTime());
        if (patch.getExecutionTime() != null) existing.setExecutionTime(patch.getExecutionTime());
        if (patch.getAttemptNumber() != null) existing.setAttemptNumber(patch.getAttemptNumber());

        return create(existing);
    }
}
