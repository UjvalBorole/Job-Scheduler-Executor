package com.watcher.services;



import com.fasterxml.jackson.databind.ObjectMapper;
import com.watcher.entities3.JobRunCacheDTO;
import com.watcher.entities3.RunStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobRunCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper mapper;
    private static final String PREFIX = "jobrun:";

    private String key(Long jobId) {
        return PREFIX + jobId;
    }

    // ✅ Create
    public JobRunCacheDTO create(JobRunCacheDTO dto) {
        try {
            String json = mapper.writeValueAsString(dto);
            redisTemplate.opsForValue().set(key(dto.getJobId()), json);
            return dto;
        } catch (Exception e) {
            throw new RuntimeException("Failed to save jobrun:" + dto.getJobId(), e);
        }
    }

    // ✅ Get by ID
    public JobRunCacheDTO get(Long jobId) {
        try {
            String json = redisTemplate.opsForValue().get(key(jobId));
            return json != null ? mapper.readValue(json, JobRunCacheDTO.class) : null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get jobrun:" + jobId, e);
        }
    }

    // ✅ Update (overwrite)
    public JobRunCacheDTO update(Long jobId, JobRunCacheDTO dto) {
        dto.setJobId(jobId);
        return create(dto);
    }

    // ✅ Delete
    public void delete(Long jobId) {
        redisTemplate.delete(key(jobId));
    }

    // ✅ Patch update
    public JobRunCacheDTO patchUpdate(Long jobId, JobRunCacheDTO patch) {
        JobRunCacheDTO existing = get(jobId);
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

    // ✅ Find by modifiedTime range
    public List<JobRunCacheDTO> getByModifiedTimeRange(LocalDateTime start, LocalDateTime end) {
        return findAll().stream()
                .filter(j -> j.getModifiedTime() != null &&
                        !j.getModifiedTime().isBefore(start) &&
                        !j.getModifiedTime().isAfter(end))
                .collect(Collectors.toList());
    }

    // ✅ Find by status
    public List<JobRunCacheDTO> getByStatus(RunStatus status) {
        return findAll().stream()
                .filter(j -> j.getStatus() == status)
                .collect(Collectors.toList());
    }

    // ✅ Get all keys (scan Redis)
    public List<JobRunCacheDTO> findAll() {
        Set<String> keys = redisTemplate.keys(PREFIX + "*");
        if (keys == null || keys.isEmpty()) return List.of();

        List<JobRunCacheDTO> all = new ArrayList<>();
        for (String k : keys) {
            String json = redisTemplate.opsForValue().get(k);
            if (json != null) {
                try {
                    all.add(mapper.readValue(json, JobRunCacheDTO.class));
                } catch (Exception e) {
                    // skip corrupt entries
                }
            }
        }
        return all;
    }
}