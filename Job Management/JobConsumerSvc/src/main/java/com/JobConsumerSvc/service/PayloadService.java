package com.JobConsumerSvc.service;

import com.JobConsumerSvc.dto.PayloadRequest;
import com.JobConsumerSvc.entities1.Job;
import com.JobConsumerSvc.entities2.Payload;
import com.JobConsumerSvc.entities2.PayloadEvent;
import com.JobConsumerSvc.repositories.JobRepository;
import com.JobConsumerSvc.repositories.PayloadRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PayloadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayloadService.class);

    private final PayloadRepository payloadRepository;
    private final JobRepository jobRepository;

    @KafkaListener(
            topics = "${spring.kafka.topic.payload}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "payloadKafkaListenerContainerFactory"
    )
    public void handlePayloadEvent(PayloadEvent event) {
        LOGGER.info("📥 Received PayloadEvent => {}", event);

        switch (event.getEventType()) {
            case CREATE -> handleCreate(event.getPayload());
            case MOD    -> handleModify(event);
            case DELETE -> handleDelete(event.getPayloadId());
        }
    }

    @Transactional
    private void handleCreate(PayloadRequest payloadRequest) {
        try {
            LOGGER.info("✅ Handling CREATE for payload: {}", payloadRequest.getName());

            // Step 1: Validate Job
            Long jobId = payloadRequest.getJobId();
            if (jobId == null) {
                LOGGER.error("❌ Payload must be linked to a valid Job ID.");
                return;
            }

            Job job = jobRepository.findById(jobId).orElse(null);
            if (job == null) {
                LOGGER.error("❌ Job with ID {} not found. Cannot create Payload.", jobId);
                return;
            }

            // Step 2: Map DTO to Entity
            Payload payload = Payload.builder()
                    .name(payloadRequest.getName())
                    .seqId(payloadRequest.getSeqId())
                    .status(payloadRequest.getStatus())
                    .startTime(payloadRequest.getStartTime())
                    .endTime(payloadRequest.getEndTime())
                    .modifiedTime(payloadRequest.getModifiedTime())
                    .executorId(payloadRequest.getExecutorId())
                    .attemptNumber(payloadRequest.getAttemptNumber())
                    .errorMsg(payloadRequest.getErrorMsg())
                    .path(payloadRequest.getPath())
                    .job(job)
                    .build();

            // Step 3: Save payload first
           Payload payload1 =  payloadRepository.save(payload);

            // Step 4: Add to job's payload list and save job
//            if (job.getPayloads() == null) {
//                job.setPayloads(new ArrayList<>());
//            }
//            job.getPayloads().add(payload);
//            jobRepository.save(job); // persist the relationship

            LOGGER.info("✅ Payload created and linked to Job ID {}: Payload ID = {}", jobId, payload1.getId());

        } catch (Exception e) {
            LOGGER.error("❌ Error while creating payload: {}", payloadRequest, e);
        }
    }


    @Transactional
    private void handleModify(PayloadEvent event) {
        System.out.println(event);
        LOGGER.info("🔄 Handling MODIFY for payload ID: {}", event.getPayloadId());

        try {
            Long payloadId = Long.parseLong(event.getPayloadId());

            Optional<Payload> existingOpt = payloadRepository.findById(payloadId);
            if (existingOpt.isEmpty()) {
                LOGGER.warn("❌ Modify failed: Payload ID {} not found", payloadId);
                return;
            }

            Payload existing = existingOpt.get();
            PayloadRequest updated = event.getPayload();

            // Update other fields
            existing.setName(updated.getName());
            existing.setSeqId(updated.getSeqId());
            existing.setStatus(updated.getStatus());
            existing.setStartTime(updated.getStartTime());
            existing.setEndTime(updated.getEndTime());
            existing.setModifiedTime(updated.getModifiedTime());
            existing.setExecutorId(updated.getExecutorId());
            existing.setAttemptNumber(updated.getAttemptNumber());
            existing.setErrorMsg(updated.getErrorMsg());
            existing.setPath(updated.getPath());

            payloadRepository.save(existing);
            LOGGER.info("✅ Payload updated successfully with ID: {}", existing.getId());

        } catch (NumberFormatException e) {
            LOGGER.error("❌ Invalid Payload ID format: {}", event.getPayloadId(), e);
        } catch (Exception e) {
            LOGGER.error("❌ Unexpected error while modifying Payload ID {}: {}", event.getPayloadId(), e.getMessage(), e);
        }
    }


    @Transactional
    private void handleDelete(String payloadIdStr) {
        LOGGER.info("❌ Handling DELETE for payloadId: {}", payloadIdStr);
        try {
            Long payloadId = Long.parseLong(payloadIdStr);
            if (payloadRepository.existsById(payloadId)) {
                payloadRepository.deleteById(payloadId);
                LOGGER.info("✅ Payload {} deleted", payloadId);
            } else {
                LOGGER.warn("⚠️ Payload ID {} not found for deletion", payloadId);
            }
        } catch (NumberFormatException e) {
            LOGGER.error("🚫 Invalid payloadId format: {}", payloadIdStr);
        }
    }

        public Payload getPayloadById(Long id) {
            return payloadRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Payload not found with ID: " + id));
        }


}

