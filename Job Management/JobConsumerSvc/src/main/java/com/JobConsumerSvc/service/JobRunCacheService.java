package com.JobConsumerSvc.service;//package com.JobConsumerSvc.service;
//
//import com.JobConsumerSvc.dto.JobRunCacheDTO;
//import com.JobConsumerSvc.entities3.JobRunCache;
//import com.JobConsumerSvc.entities3.RunStatus;
//import com.JobConsumerSvc.mapper.JobRunCacheMapper;
//import com.JobConsumerSvc.repositories.JobRunCacheRepository;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Service;
//
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.Optional;
//import java.util.stream.Collectors;
//import java.util.stream.StreamSupport;
//
//@Service
//@RequiredArgsConstructor
//public class JobRunCacheService {
//
//    private final JobRunCacheRepository repository;
//
//    // CREATE
//    public JobRunCacheDTO create(JobRunCacheDTO dto) {
//        JobRunCache entity = JobRunCacheMapper.fromDTO(dto);
//        JobRunCache saved = repository.save(entity);
//        return JobRunCacheMapper.toDTO(saved);
//    }
//
//    // READ
//    public Optional<JobRunCacheDTO> get(Long jobId) {
//        return repository.findById(jobId)
//                .map(JobRunCacheMapper::toDTO);
//    }
//
//    // UPDATE
//    public JobRunCacheDTO update(Long jobId, JobRunCacheDTO dto) {
//        JobRunCache entity = repository.findById(jobId)
//                .orElseThrow(() -> new RuntimeException("JobRun not found in Redis: " + jobId));
//
//        // update only non-null fields from dto
//        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
//        if (dto.getStartTime() != null) entity.setStartTime(dto.getStartTime());
//        if(dto.getExecutionTime() != null)entity.setExecutionTime(dto.getExecutionTime());
//        if (dto.getEndTime() != null) entity.setEndTime(dto.getEndTime());
//        if (dto.getModifiedTime() != null) entity.setModifiedTime(dto.getModifiedTime());
//        if (dto.getExecutorId() != null) entity.setExecutorId(dto.getExecutorId());
//        if (dto.getAttemptNumber() != 0) entity.setAttemptNumber(dto.getAttemptNumber());
//        if (dto.getErrorMsg() != null) entity.setErrorMsg(dto.getErrorMsg());
//
//        JobRunCache updated = repository.save(entity);
//        return JobRunCacheMapper.toDTO(updated);
//    }
//
//    // In JobRunCacheService
//    public JobRunCacheDTO patchUpdate(Long jobId, JobRunCacheDTO dto) {
//        JobRunCache entity = repository.findById(jobId)
//                .orElseThrow(() -> new RuntimeException("JobRun not found in Redis: " + jobId));
//
//        // update only non-null / non-default fields
//        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
//        if (dto.getStartTime() != null) entity.setStartTime(dto.getStartTime());
//        if (dto.getEndTime() != null) entity.setEndTime(dto.getEndTime());
//        if (dto.getModifiedTime() != null) entity.setModifiedTime(dto.getModifiedTime());
//        if (dto.getExecutorId() != null) entity.setExecutorId(dto.getExecutorId());
//        if (dto.getAttemptNumber() > 0) entity.setAttemptNumber(dto.getAttemptNumber());
//        if (dto.getErrorMsg() != null) entity.setErrorMsg(dto.getErrorMsg());
//        if(dto.getExecutionTime() != null)entity.setExecutionTime(dto.getExecutionTime());
//
//
//        JobRunCache updated = repository.save(entity);
//        return JobRunCacheMapper.toDTO(updated);
//    }
//
//
//    // DELETE
//    public void delete(Long jobId) {
//        repository.deleteById(jobId);
//    }
//
//
//    // ✅ get by jobId
//    public Optional<JobRunCacheDTO> getJobId(Long jobId) {
//        return repository.findById(jobId).map(JobRunCacheMapper::toDTO);
//    }
//
//    // ✅ get by modifiedTime range
//    public List<JobRunCacheDTO> getByModifiedTimeRange(LocalDateTime start, LocalDateTime end) {
//        return StreamSupport.stream(repository.findAll().spliterator(), false)
//                .filter(j -> j.getModifiedTime() != null &&
//                        (j.getModifiedTime().isEqual(start) || j.getModifiedTime().isAfter(start)) &&
//                        (j.getModifiedTime().isEqual(end) || j.getModifiedTime().isBefore(end)))
//                .map(JobRunCacheMapper::toDTO)
//                .collect(Collectors.toList());
//    }
//
//    // ✅ get by status
//    public List<JobRunCacheDTO> getByStatus(RunStatus status) {
//        return StreamSupport.stream(repository.findAll().spliterator(), false)
//                .filter(j -> j.getStatus() == status)
//                .map(JobRunCacheMapper::toDTO)
//                .collect(Collectors.toList());
//    }
//
//}

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import com.JobConsumerSvc.dto.JobRunCacheDTO;
import com.JobConsumerSvc.entities3.RunStatus;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
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
