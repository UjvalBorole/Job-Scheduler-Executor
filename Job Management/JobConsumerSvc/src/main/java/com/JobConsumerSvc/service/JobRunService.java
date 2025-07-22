package com.JobConsumerSvc.service;

import com.JobConsumerSvc.dto.JobRunDTO;
import com.JobConsumerSvc.entities1.Job;
import com.JobConsumerSvc.entities3.JobRun;
import com.JobConsumerSvc.dto.JobRunWithJobDTO;
import com.JobConsumerSvc.entities3.RunStatus;
import com.JobConsumerSvc.repositories.JobRepository;
import com.JobConsumerSvc.repositories.JobRunRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class JobRunService {

    @Autowired
    private JobRunRepository jobRunRepository;

    @Autowired
    private JobRepository jobRepository;

    private static final Logger LOGGER = LoggerFactory.getLogger(JobRunService.class); // add this at the top

    public JobRun updateJobRun(Long jobId, JobRunDTO updatedJobRun) {
        System.out.println("Error massage this is the error massage "+ updatedJobRun);
        JobRun existing = jobRunRepository.findByJobId(jobId)
                .orElseThrow(() -> new EntityNotFoundException("JobRun not found with jobId: " + jobId));

        if (updatedJobRun.getStatus() != null) {
            LOGGER.info("Updating status from '{}' to '{}'", existing.getStatus(), updatedJobRun.getStatus());
            existing.setStatus(updatedJobRun.getStatus());
        }

        if (updatedJobRun.getStartTime() != null) {
            LOGGER.info("Updating startTime from '{}' to '{}'", existing.getStartTime(), updatedJobRun.getStartTime());
            existing.setStartTime(updatedJobRun.getStartTime());
        }

        if (updatedJobRun.getEndTime() != null) {
            LOGGER.info("Updating endTime from '{}' to '{}'", existing.getEndTime(), updatedJobRun.getEndTime());
            existing.setEndTime(updatedJobRun.getEndTime());
        }

        if (updatedJobRun.getModifiedTime() != null) {
            LOGGER.info("Updating modifiedTime from '{}' to '{}'", existing.getModifiedTime(), updatedJobRun.getModifiedTime());
            existing.setModifiedTime(updatedJobRun.getModifiedTime());
        }

        if (updatedJobRun.getExecutorId() != null && !updatedJobRun.getExecutorId().isBlank()) {
            LOGGER.info("Updating executorId from '{}' to '{}'", existing.getExecutorId(), updatedJobRun.getExecutorId());
            existing.setExecutorId(updatedJobRun.getExecutorId());
        }

        if (updatedJobRun.getAttemptNumber() != 0) {
            LOGGER.info("Updating attemptNumber from '{}' to '{}'", existing.getAttemptNumber(), updatedJobRun.getAttemptNumber());
            existing.setAttemptNumber(updatedJobRun.getAttemptNumber());
        }

        if (updatedJobRun.getErrorMsg() != null && !updatedJobRun.getErrorMsg().isBlank()) {
            LOGGER.info("Updating errorMsg from '{}' to '{}'", existing.getErrorMsg(), updatedJobRun.getErrorMsg());
            existing.setErrorMsg(updatedJobRun.getErrorMsg());
        }

        return jobRunRepository.save(existing);
    }

    public JobRunWithJobDTO getById(Long id) {
        JobRun run = jobRunRepository.findJobRunById(id);
        Job job = jobRepository.findById(run.getJobId()).orElse(null);
        return new JobRunWithJobDTO(run, job);
    }

    public JobRunWithJobDTO getByJobId(Long jobId) {
        JobRun existing = jobRunRepository.findByJobId(jobId)
                .orElseThrow(() -> new EntityNotFoundException("JobRun not found with jobId: " + jobId));
        Job job = jobRepository.findById(jobId).orElse(null);
        return new JobRunWithJobDTO(existing, job);
    }

    public List<JobRunWithJobDTO> getByStatus(String status) {
        List<JobRun> runs = jobRunRepository.findByStatus(Enum.valueOf(RunStatus.class, status));
        return runs.stream()
                .map(run -> {
                    Job job = jobRepository.findById(run.getJobId()).orElse(null);
                    return new JobRunWithJobDTO(run, job);
                })
                .collect(Collectors.toList());
    }

    public List<JobRunWithJobDTO> getByModifiedTimeRange(LocalDateTime from, LocalDateTime to) {
        List<JobRun> runs = jobRunRepository.findByModifiedTimeBetween(from, to);
        return runs.stream()
                .map(run -> {
                    Job job = jobRepository.findById(run.getJobId()).orElse(null);
                    return new JobRunWithJobDTO(run, job);
                })
                .collect(Collectors.toList());
    }
}
