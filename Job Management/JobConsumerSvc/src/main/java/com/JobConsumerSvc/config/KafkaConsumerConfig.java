package com.JobConsumerSvc.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import com.JobConsumerSvc.dto.DepTrackerDTO;
import com.JobConsumerSvc.dto.PayloadRequest;
import com.JobConsumerSvc.entities1.JobEvent;
import com.JobConsumerSvc.entities2.PayloadEvent;

@Configuration
public class KafkaConsumerConfig {

    // @Value("${spring.kafka.topic.deptrackerqueue}")
    // private String DepTrackerDTOTopic;

    // @Value("${spring.kafka.topic.payloadqueue}")
    // private String payloadDTOTopic;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ---- JobEvent Config ----
    @Bean
    public ConsumerFactory<String, JobEvent> jobEventConsumerFactory() {
        JsonDeserializer<JobEvent> deserializer = new JsonDeserializer<>(JobEvent.class);
        deserializer.setRemoveTypeHeaders(false);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeMapperForKey(true);

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "executor-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean(name = "jobEventKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, JobEvent> jobEventKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, JobEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(jobEventConsumerFactory());
        return factory;
    }

    // ---- PayloadEvent Config ----
    @Bean
    public ConsumerFactory<String, PayloadEvent> payloadEventConsumerFactory() {
        JsonDeserializer<PayloadEvent> deserializer = new JsonDeserializer<>(PayloadEvent.class);
        deserializer.setRemoveTypeHeaders(false);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeMapperForKey(true);

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "payload-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

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
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "DepTrackerDTO-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean(name = "DepTrackerDTOKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, DepTrackerDTO> DepTrackerDTOKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, DepTrackerDTO> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(DepTrackerDTOConsumerFactory());
        return factory;
    }

    // ---- Payload (from ExecutorService) Config ----
    @Bean
    public ConsumerFactory<String, PayloadRequest> payloadConsumerFactory() {
        JsonDeserializer<PayloadRequest> deserializer = new JsonDeserializer<>(PayloadRequest.class);
        deserializer.setRemoveTypeHeaders(false);
        deserializer.addTrustedPackages("*"); // trust all packages for deserialization
        deserializer.setUseTypeMapperForKey(true);

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "payload-dto-group"); // ✅ unique group
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean(name = "payloadDtoKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, PayloadRequest> payloadDtoKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PayloadRequest> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(payloadConsumerFactory());
        return factory;
    }
}
