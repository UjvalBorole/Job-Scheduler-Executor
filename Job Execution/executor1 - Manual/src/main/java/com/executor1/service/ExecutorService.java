package com.executor1.service;

import java.time.LocalDateTime;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import com.executor1.entities1.Job;
import com.executor1.entities2.Payload;
import com.executor1.entities2.PayloadEvent;
import com.executor1.entities2.PayloadEventType;
import com.executor1.entities2.RunStatus;
import com.executor1.entities4.CancelReq;
import com.executor1.entities4.ExecutionResult;
import com.executor1.entities4.JobStatus;
import com.executor1.entities4.Type;
import com.executor1.utility.JenkinsfileExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutorService {

    private final JenkinsfileExecutor jenkinsfileExecutor;
    private final NewTopic payloadQueueTopic;
    private final KafkaTemplate<String, PayloadEvent> payloadKafkaTemplate;
    private final CancelReqRepository cancelReqRepository;

    // Send PayloadEvent to Kafka
    private void sendPayloadKafka(Payload payload, PayloadEventType eventType) {
        PayloadEvent payloadEvent = new PayloadEvent();
        payloadEvent.setPayload(payload);
        payloadEvent.setPayloadId(String.valueOf(payload.getId()));
        payloadEvent.setEventType(eventType);

        Message<PayloadEvent> message = MessageBuilder
                .withPayload(payloadEvent)
                .setHeader(KafkaHeaders.TOPIC, payloadQueueTopic.name())
                .build();

        payloadKafkaTemplate.send(message);

        log.info("📤 Sent PayloadEvent [eventType={}, payloadName={}, jobId={}] to Kafka topic '{}'",
                eventType, payload.getName(), payload.getJobId(), payloadQueueTopic.name());
    }

    public ExecutionResult execute(Job job) {
        log.info("🚀 Starting execution for Job: {} with {} payload(s)", job.getName(), job.getPayloads().size());
//        log.info("Payload1 {}",job.getPayloads().size());
        // ==============================
        // Cancel Job? (check Redis)
        // ==============================
        CancelReq cancelJob = cancelReqRepository.findById(String.valueOf(job.getId()));
        if (cancelJob != null && cancelJob.getType() == Type.Job) {
            log.warn("❌ Job '{}' (ID={}) is canceled via Redis", job.getName(), job.getId());

            // delete cancel request from Redis
//            cancelReqRepository.deleteById(String.valueOf(job.getId()));

            return ExecutionResult.builder()
                    .status(JobStatus.FAILED)
                    .errorMessage("Job was canceled via Redis")
                    .build();
        }

        int successCount = 0;
        int failCount = 0;
        StringBuilder errorMessages = new StringBuilder();

        // ==============================
        // Iterate payloads
        // ==============================
        for (Payload pay : job.getPayloads()) {
            // Check Cancel for this payload
            CancelReq cancelPayload = cancelReqRepository.findById(String.valueOf(pay.getId()));
            if (cancelPayload != null && cancelPayload.getType() == Type.Payload) {
                log.warn("⏹️ Payload '{}' (ID={}) is canceled via Redis. Skipping execution.",
                        pay.getName(), pay.getId());

                // delete cancel req from Redis
//                cancelReqRepository.deleteById(String.valueOf(pay.getId()));

                // mark as skipped → treat as success
                pay.setExecutorId(Integer.toString(2));
                pay.setStartTime(LocalDateTime.now());
                pay.setEndTime(LocalDateTime.now());

                successCount++;
                sendPayloadKafka(pay, PayloadEventType.DELETE);
                continue;
            }

            // if(pay.getAttemptNumber() == null){
            //     log.warn("AttemptNumber not be NULL ");
            //     pay.setAttemptNumber(-1);
            // }
             int attempts = (pay.getAttemptNumber() == null)?0: pay.getAttemptNumber();
            if(pay.getPath() != null && pay.getAttemptNumber() == null) {
                attempts = 1;
                log.warn("▶️ Payload '{}' (JobId={})  attempnumber set to {} from {} ",
                        pay.getName(), pay.getJobId(), attempts,pay.getAttemptNumber());
            }
            else if(pay.getPath() != null && pay.getAttemptNumber() == 0)attempts = 1;
            if(attempts == 0)log.warn("▶️ Payload '{}' (JobId={}) execution Skipped because of attempnumber is {}  for execution attempts greater than 1 needed",
                    pay.getName(), pay.getJobId(), attempts);
            Payload result = pay;

            // Mark start time
            pay.setStartTime(LocalDateTime.now());
            log.debug("▶️ Payload '{}' (JobId={}) execution started at {} with max attempts={}",
                    pay.getName(), pay.getJobId(), pay.getStartTime(), attempts);

            //Retries code with halt for 1 min
            boolean success = false;
            for (int i = 1; i <= attempts; i++) {
                pay.setAttemptNumber(i);
                log.debug("🔄 Attempt {} for Payload '{}' (JobId={})", i, pay.getName(), pay.getJobId());
                pay.setJobId(job.getId());
                pay.setExecutorId(Integer.toString(2));
                result = jenkinsfileExecutor.execute(pay);

                if (result.getStatus() == RunStatus.SUCCESS) {
                    log.info("✅ Payload '{}' (JobId={}) succeeded on attempt {}",
                            result.getName(), result.getJobId(), i);
                    success = true;
                    break;
                } else {
                    log.warn("⚠️ Payload '{}' (JobId={}) failed on attempt {}. Error={}",
                            result.getName(), result.getJobId(), i, result.getErrorMsg());

                    // wait 1 minute before next retry (if more retries left)
                    if (i < attempts) {
                        try {
                            log.info("⏸ Waiting 1 minute before next retry...");
                            Thread.sleep(60_000); // 60,000 ms = 1 minute
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt(); // restore interrupt flag
                            break; // exit retry loop if thread is interrupted
                        }
                    }
                }
            }

            // Mark end time
            pay.setEndTime(LocalDateTime.now());
            log.debug("⏹️ Payload '{}' (JobId={}) finished at {}", pay.getName(), pay.getJobId(), pay.getEndTime());

            if (success) {
                successCount++;
                sendPayloadKafka(result, PayloadEventType.MOD);
            } else {
                failCount++;
                errorMessages.append("Payload '")
                        .append(result.getName())
                        .append("' (ID: ").append(result.getId())
                        .append(") failed after ").append(attempts).append(" attempts. ")
                        .append(result.getErrorMsg() == null ? "" : result.getErrorMsg())
                        .append("\n");

                sendPayloadKafka(result, PayloadEventType.MOD);
            }
        }

        // ==============================
        // Job Result
        // ==============================
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
