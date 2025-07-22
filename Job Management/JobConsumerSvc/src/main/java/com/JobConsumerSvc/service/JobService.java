package com.JobConsumerSvc.service;

import com.JobConsumerSvc.dto.JobDTO;
import com.JobConsumerSvc.dto.JobRunDTO;
import com.JobConsumerSvc.entities1.*;
import com.JobConsumerSvc.entities2.Payload;
import com.JobConsumerSvc.entities3.RunStatus;
import com.JobConsumerSvc.entities3.JobRun;
import com.JobConsumerSvc.repositories.JobRepository;
import com.JobConsumerSvc.repositories.JobRunRepository;
import com.JobConsumerSvc.repositories.PayloadRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.JobConsumerSvc.mapper.JobMapper.fromDTO;

@Service
@RequiredArgsConstructor
public class JobService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobService.class);
    private final JobRepository jobRepository;
    private final JobRunRepository jobRunRepository;
    private final PayloadRepository payloadRepository;

    private final JobRunService jobRunService;

    @KafkaListener(
            topics = "${spring.kafka.topic.name}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "jobEventKafkaListenerContainerFactory"
    )
    public void handleJobEvent(JobEvent event) {
        LOGGER.info("📥 Received JobEvent => {}", event);

        switch (event.getEventType()) {
            case CREATE -> handleCreate(event.getJob());
            case MOD     -> handleModify(event);
            case DELETE  -> handleDelete(event.getJobId());
        }
    }

    @Transactional
    private void handleCreate(JobDTO job) {
        try {
            LOGGER.info("📥 Handling CREATE for job: {}", job.getName());

            if (job.getId() != null && jobRepository.existsById(job.getId())) {
                LOGGER.warn("⚠️ Job with ID {} already exists. Skipping creation.", job.getId());
                return;
            }

            boolean valid = true;
            StringBuilder errorLog = new StringBuilder();
            ScheduleType scheduleType = job.getScheduleType();

            if (job.getRetries() == 0) {
                valid = false;
                errorLog.append("❌ Retries must not be zero. ");
            }

            switch (scheduleType) {
                case CRON -> {
                    if (job.getCronExpression() == null) {
                        valid = false;
                        errorLog.append("❌ CRON expression is required for CRON schedule. ");
                    }
                    job.setScheduleTime(null);
                }
                case MANUAL -> {
                    job.setCronExpression(null);
                    job.setScheduleTime(null);
                }
            }

            if (!valid) {
                LOGGER.warn("❌ Job creation aborted due to invalid configuration for job '{}': {}", job.getName(), errorLog);
                return;
            }

            Job entity = fromDTO(job);
            entity.setModifiedTime(LocalDateTime.now()); // ✅ set modified time on creation
            Job createdJob = jobRepository.save(entity);

            jobRunRepository.save(JobRun.builder()
                    .jobId(createdJob.getId())
                    .status(RunStatus.PENDING)
                    .attemptNumber(1)
                    .build());

            LOGGER.info("✅ Job {} created successfully", createdJob.getId());

        } catch (Exception e) {
            LOGGER.error("❌ Error while creating job: {}", job, e);
        }
    }

    public void handleModify(JobEvent event) {
        String jobId = event.getJobId();

        Job job = jobRepository.findById(Long.valueOf(jobId))
                .orElseThrow(() -> new EntityNotFoundException("Job not found with id: " + jobId));

        JobDTO dto = event.getJob();

        if (dto.getName() != null && !dto.getName().isBlank()) {
            LOGGER.info("Updating name from '{}' to '{}'", job.getName(), dto.getName());
            job.setName(dto.getName());
        }

        if (dto.getScheduleType() != null) {
            LOGGER.info("Updating scheduleType from '{}' to '{}'", job.getScheduleType(), dto.getScheduleType());
            job.setScheduleType(dto.getScheduleType());
        }

        if (dto.getStatus() != null) {
            LOGGER.info("Updating status from '{}' to '{}'", job.getStatus(), dto.getStatus());
            job.setStatus(dto.getStatus());
        }

        if (dto.getDependencies() != null && !dto.getDependencies().isBlank()) {
            LOGGER.info("Updating dependencies from '{}' to '{}'", job.getDependencies(), dto.getDependencies());
            job.setDependencies(dto.getDependencies());
        }

        if (dto.getScheduleTime() != null) {
            LOGGER.info("Updating scheduleTime from '{}' to '{}'", job.getScheduleTime(), dto.getScheduleTime());
            job.setScheduleTime(dto.getScheduleTime());
        }

        if (dto.getCronExpression() != null && !dto.getCronExpression().isBlank()) {
            LOGGER.info("Updating cronExpression from '{}' to '{}'", job.getCronExpression(), dto.getCronExpression());
            job.setCronExpression(dto.getCronExpression());
        }

        if (dto.getRetries() != 0) {
            LOGGER.info("Updating retries from '{}' to '{}'", job.getRetries(), dto.getRetries());
            job.setRetries(dto.getRetries());
        }

        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            LOGGER.info("Updating email from '{}' to '{}'", job.getEmail(), dto.getEmail());
            job.setEmail(dto.getEmail());
        }

        if (dto.getMeta() != null && !dto.getMeta().isBlank()) {
            LOGGER.info("Updating meta from '{}' to '{}'", job.getMeta(), dto.getMeta());
            job.setMeta(dto.getMeta());
        }

        // ✅ set modified time on update
        job.setModifiedTime(LocalDateTime.now());
        JobRunDTO jobRunDTO = JobRunDTO
                .builder()
                .errorMsg("no error")
                .attemptNumber(0)
                .executorId("no execution")
                .modifiedTime(LocalDateTime.now())
                .status(RunStatus.PENDING)
                .build();

        jobRepository.save(job);
        jobRunService.updateJobRun(job.getId(),jobRunDTO);
    }

    private void handleDelete(String jobIdStr) {
        LOGGER.info("❌ Handling DELETE for jobId: {}", jobIdStr);
        try {
            Long jobId = Long.parseLong(jobIdStr);
            if (jobRepository.existsById(jobId)) {

                JobRun run = jobRunRepository.findByJobId(jobId)
                        .orElseThrow(() -> new EntityNotFoundException("JobRun not found with jobId: " + jobId));

                if (run != null) LOGGER.info("Found JobRun entries for jobId {}", jobId);
                jobRunRepository.deleteByJobId(jobId);

                jobRepository.deleteById(jobId);
                LOGGER.info("✅ Job {} and its runs deleted", jobId);
            } else {
                LOGGER.warn("⚠️ Job ID {} not found for deletion", jobId);
            }
        } catch (NumberFormatException e) {
            LOGGER.error("🚫 Invalid jobId format: {}", jobIdStr);
        }
    }

    public Job getJobWithPayloads(Long id) {
        Optional<Job> jobOpt = jobRepository.findByIdWithPayloads(id);
        return jobOpt.orElseThrow(() -> new RuntimeException("Job not found with ID: " + id));
    }

    public Job getLatestJob() {
        return jobRepository.findFirstByOrderByModifiedTimeDesc();
    }
}
