package com.JobConsumerSvc.config;

import com.JobConsumerSvc.entities1.JobEvent;
import com.JobConsumerSvc.entities2.Payload;
import com.JobConsumerSvc.entities2.PayloadEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

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
}
