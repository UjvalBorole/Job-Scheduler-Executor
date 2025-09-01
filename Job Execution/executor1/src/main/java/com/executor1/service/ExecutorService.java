package com.executor1.service;

import com.executor1.entities1.Job;
import com.executor1.entities2.Payload;
import com.executor1.entities2.PayloadEvent;
import com.executor1.entities2.PayloadEventType;
import com.executor1.entities2.RunStatus;
import com.executor1.entities4.ExecutionResult;
import com.executor1.entities4.JobStatus;
import com.executor1.utility.JenkinsfileExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j   // ✅ Lombok annotation to enable SLF4J logging
public class ExecutorService {

    private final JenkinsfileExecutor jenkinsfileExecutor;
    private final NewTopic payloadQueueTopic;
    private final KafkaTemplate<String, PayloadEvent> payloadKafkaTemplate;

    private void sentPayloadKafka(Payload payload) {
        // Wrap Payload inside PayloadEvent
        System.out.println(payload);
        PayloadEvent payloadEvent = new PayloadEvent();
        payloadEvent.setPayload(payload);
        payloadEvent.setPayloadId(String.valueOf(payload.getId())); // or another unique ID
        payloadEvent.setEventType(PayloadEventType.MOD); // 👈 choose MOD

        // Send wrapped event
        Message<PayloadEvent> message = MessageBuilder
                .withPayload(payloadEvent)
                .setHeader(KafkaHeaders.TOPIC, payloadQueueTopic.name())
                .build();
        payloadKafkaTemplate.send(message);

        log.info("✅ Sent PayloadEvent [eventType={}, payloadName={}, jobId={}] to Kafka topic '{}'",
                payloadEvent.getEventType(), payload.getName(), payload.getJobId(), payloadQueueTopic.name());
    }


    public ExecutionResult execute(Job job) {
        log.info("🚀 Starting execution for Job: {} with {} payload(s)", job.getName(), job.getPayloads().size());

        int successCount = 0;
        int failCount = 0;
        StringBuilder errorMessages = new StringBuilder();

        for (Payload pay : job.getPayloads()) {
            int attempts = pay.getAttemptNumber();
            Payload result = pay;

            // Mark start time at first execution
            pay.setStartTime(LocalDateTime.now());
            log.debug("▶️ Payload '{}' (JobId={}) execution started at {} with max attempts={}",
                    pay.getName(), pay.getJobId(), pay.getStartTime(), attempts);

            boolean success = false;
            for (int i = 1; i <= attempts; i++) {
                pay.setAttemptNumber(i);
                log.debug("🔄 Attempt {} for Payload '{}' (JobId={})", i, pay.getName(), pay.getJobId());

                result = jenkinsfileExecutor.execute(pay);

                if (result.getStatus() == RunStatus.SUCCESS) {
                    log.info("✅ Payload '{}' (JobId={}) succeeded on attempt {}", result.getName(), result.getJobId(), i);
                    success = true;
                    break;
                } else {
                    log.warn("⚠️ Payload '{}' (JobId={}) failed on attempt {}. Error={}",
                            result.getName(), result.getJobId(), i, result.getErrorMsg());
                }
            }

            // Mark end time after last attempt
            pay.setEndTime(LocalDateTime.now());
            log.debug("⏹️ Payload '{}' (JobId={}) finished at {}", pay.getName(), pay.getJobId(), pay.getEndTime());

            if (success) {
                successCount++;
            } else {
                failCount++;
                errorMessages.append("Payload '")
                        .append(result.getName())
                        .append("' (ID: ").append(result.getJobId())
                        .append(") failed after ").append(attempts).append(" attempts. ")
                        .append(result.getErrorMsg() == null ? "" : result.getErrorMsg())
                        .append("\n");
            }

            sentPayloadKafka(result);
        }

        // ==========================
        // Job ExecutionResult
        // ==========================
        JobStatus jobStatus;
        if (successCount == job.getPayloads().size()) {
            jobStatus = JobStatus.SUCCESS;
        } else if (failCount == job.getPayloads().size()) {
            jobStatus = JobStatus.FAILED;
        } else {
            jobStatus = JobStatus.PARTIAL;
        }

        log.info("🏁 Job '{}' execution finished. Status={}, Success={}, Fail={}",
                job.getName(), jobStatus, successCount, failCount);

        return ExecutionResult.builder()
                .status(jobStatus)
                .errorMessage(errorMessages.toString().trim())
                .build();
    }
}
