package com.JobConsumerSvc.service;


import com.JobConsumerSvc.dto.DepTrackerDTO;
import com.JobConsumerSvc.entities4.DepTracker;
import com.JobConsumerSvc.entities4.JobStatus;
import com.JobConsumerSvc.repositories.DepTrackerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class DepTrackerService {

    private final DepTrackerRepository depTrackerRepository;

    public DepTracker createDepTracker(DepTrackerDTO dto) {
        log.info("Creating DepTracker entry for jobId={} jobName={}", dto.getJobId(), dto.getJobName());

        DepTracker depTracker = DepTracker.builder()
                .jobId(dto.getJobId())
                .jobName(dto.getJobName())
                .jobStatus(dto.getJobStatus()) // convert string to enum
                .dependencies(dto.getDependencies())
                .retryCount(dto.getRetryCount())
                .maxRetries(dto.getMaxRetries())
                .scheduleTime(dto.getScheduleTime())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .nextCron(dto.getNextCron())
                .executorId(dto.getExecutorId())
                .errorMessage(dto.getErrorMessage())
                .build();

        DepTracker saved = depTrackerRepository.save(depTracker);

        log.debug("DepTracker saved in MongoDB: {}", saved);
        return saved;
    }

    public DepTracker patchDepTracker(String id, DepTrackerDTO dto) {
        DepTracker existing = depTrackerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("DepTracker not found with id=" + id));

        log.info("Patching DepTracker with id={} fields={}", id, dto);

        if (dto.getJobName() != null) existing.setJobName(dto.getJobName());
        if (dto.getJobStatus() != null)
            existing.setJobStatus(dto.getJobStatus());
        if (dto.getDependencies() != null) existing.setDependencies(dto.getDependencies());
        if (dto.getMaxRetries() != null) existing.setRetryCount(dto.getRetryCount());
        if (dto.getMaxRetries() != null) existing.setMaxRetries(dto.getMaxRetries());
        if (dto.getScheduleTime() != null) existing.setScheduleTime(dto.getScheduleTime());
        if (dto.getStartTime() != null) existing.setStartTime(dto.getStartTime());
        if (dto.getEndTime() != null) existing.setEndTime(dto.getEndTime());
        if (dto.getNextCron() != null) existing.setNextCron(dto.getNextCron());
        if (dto.getExecutorId() != null) existing.setExecutorId(dto.getExecutorId());
        if (dto.getErrorMessage() != null) existing.setErrorMessage(dto.getErrorMessage());

        return depTrackerRepository.save(existing);
    }

    public void deleteDepTracker(String id) {
        if (!depTrackerRepository.existsById(id)) {
            log.warn("DepTracker with id={} not found, nothing deleted.", id);
            throw new RuntimeException("DepTracker not found with id=" + id);
        }

        depTrackerRepository.deleteById(id);
        log.info("Deleted DepTracker with id={}", id);
    }

    public List<DepTracker> getAll() {
        log.info("Fetching all dependency tracker jobs");
        return depTrackerRepository.findAll();
    }

    public DepTracker getById(String id) {
        log.info("Fetching DepTracker with id={}", id);
        return depTrackerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("DepTracker not found with id=" + id));
    }

    public List<DepTracker> getByJobStatus(JobStatus status) {
        log.info("Fetching jobs with status={}", status);
        return depTrackerRepository.findByJobStatus(status);
    }

    public List<DepTracker> getByJobId(String jobId) {
        log.info("Fetching jobs with jobId={}", jobId);
        return depTrackerRepository.findByJobId(jobId);
    }

    public List<DepTracker> getByExecutorId(String executorId) {
        log.info("Fetching jobs executed by executorId={}", executorId);
        return depTrackerRepository.findByExecutorId(executorId);
    }

    public List<DepTracker> getByDependency(String dependencyId) {
        log.info("Fetching jobs depending on jobId={}", dependencyId);
        return depTrackerRepository.findByDependenciesContaining(dependencyId);
    }

    public List<DepTracker> searchByJobName(String jobName) {
        log.info("Searching jobs by jobName containing '{}'", jobName);
        return depTrackerRepository.findByJobNameContainingIgnoreCase(jobName);
    }
}
