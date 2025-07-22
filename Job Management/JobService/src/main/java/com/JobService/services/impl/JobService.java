package com.JobService.services.impl;

import com.JobService.entities1.Job;
import com.JobService.entities1.JobEvent;
import com.JobService.entities1.JobEventType;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
public class JobService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobService.class);

    private final KafkaTemplate<String, JobEvent> jobKafkaTemplate;
    private final NewTopic jobTopic;

    public JobService(KafkaTemplate<String, JobEvent> jobKafkaTemplate, NewTopic jobTopic) {
        this.jobKafkaTemplate = jobKafkaTemplate;
        this.jobTopic = jobTopic;
    }

    public void sendEvent(JobEvent event) {
        Message<JobEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, jobTopic.name())
                .build();
        jobKafkaTemplate.send(message);
    }


    public boolean create(Job job) {
        JobEvent event = JobEvent.builder()
                .eventType(JobEventType.CREATE)
                .job(job)
                .build();

        LOGGER.info("Sending CREATE event to Kafka: {}", event);
        sendEvent(event);
        return true;
    }



    public boolean modify(Job job,String Id) {
        System.out.println(job);
        JobEvent event = JobEvent.builder()
                .eventType(JobEventType.MOD)
                .job(job)
                .jobId(Id)
                .build();

        LOGGER.info("Sending Modify event to Kafka: {}", event);
        sendEvent(event);
        return true;
    }


    public boolean delete(String jobId) {
        JobEvent event = JobEvent.builder()
                .eventType(JobEventType.DELETE)
                .jobId(jobId)
                .build();

        LOGGER.info("Sending DELETE event to Kafka: {}", event);
        sendEvent(event);
        return true;
    }



}
