package com.JobConsumerSvc.config;

import java.util.HashMap;
import java.util.Map;

import com.JobConsumerSvc.dto.DepTrackerDTO;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import com.JobConsumerSvc.entities1.JobEvent;
import com.JobConsumerSvc.entities2.PayloadEvent;

@Configuration
public class KafkaConsumerConfig {
    // Load topic name from application.properties
    @Value("${spring.kafka.topic.deptrackerqueue}")
    private String DepTrackerDTOTopic;

    // ---- JobEvent Config ----
    @Bean
    public ConsumerFactory<String, JobEvent> jobEventConsumerFactory() {
        JsonDeserializer<JobEvent> deserializer = new JsonDeserializer<>(JobEvent.class);
        deserializer.setRemoveTypeHeaders(false);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeMapperForKey(true);

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "executor-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean(name = "jobEventKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, JobEvent> jobEventKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, JobEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(jobEventConsumerFactory());
        return factory;
    }

    // ---- Payload Config ----
    @Bean
    public ConsumerFactory<String, PayloadEvent> payloadEventConsumerFactory() {
        JsonDeserializer<PayloadEvent> deserializer = new JsonDeserializer<>(PayloadEvent.class);
        deserializer.setRemoveTypeHeaders(false);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeMapperForKey(true);

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "payload-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean(name = "payloadKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, PayloadEvent> payloadKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PayloadEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(payloadEventConsumerFactory());
        return factory;
    }

    // ---- DepTrackerDTO Config ----
    @Bean
    public ConsumerFactory<String, DepTrackerDTO> DepTrackerDTOConsumerFactory() {
        JsonDeserializer<DepTrackerDTO> deserializer = new JsonDeserializer<>(DepTrackerDTO.class);
        deserializer.setRemoveTypeHeaders(false);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeMapperForKey(true);

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "DepTrackerDTO-group"); // ✅ new group for DepTrackerDTO
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean(name = "DepTrackerDTOKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, DepTrackerDTO> DepTrackerDTOKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, DepTrackerDTO> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(DepTrackerDTOConsumerFactory());
        return factory;
    }
}
