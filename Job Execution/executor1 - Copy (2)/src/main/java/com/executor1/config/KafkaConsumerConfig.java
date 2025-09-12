package com.executor1.config;

import java.util.HashMap;
import java.util.Map;

import com.executor1.entities1.RedisJobWrapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.groups.run}")
    private String runGroupId;

    @Value("${kafka.groups.wait}")
    private String waitGroupId;

    @Value("${kafka.groups.retry}")
    private String retryGroupId;

    // ==== Common props ====
    private Map<String, Object> consumerProps(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return props;
    }

    private <T> ConsumerFactory<String, T> createConsumerFactory(Class<T> clazz, String groupId) {
        JsonDeserializer<T> deserializer = new JsonDeserializer<>(clazz, false);
        deserializer.setRemoveTypeHeaders(true);   // strip __TypeId__ from producer
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeMapperForKey(false);

        return new DefaultKafkaConsumerFactory<>(
                consumerProps(groupId),
                new StringDeserializer(),
                deserializer
        );
    }


    // ==== Run Queue ====
    @Bean(name = "runQueueKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, RedisJobWrapper> runQueueKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, RedisJobWrapper> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(createConsumerFactory(RedisJobWrapper.class, runGroupId));
        return factory;
    }

    // ==== Wait Queue ====
    @Bean(name = "waitQueueKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, RedisJobWrapper> waitQueueKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, RedisJobWrapper> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(createConsumerFactory(RedisJobWrapper.class, waitGroupId));
        return factory;
    }

    // ==== Retry Queue ====
    @Bean(name = "retryQueueKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, RedisJobWrapper> retryQueueKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, RedisJobWrapper> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(createConsumerFactory(RedisJobWrapper.class, retryGroupId));
        return factory;
    }
}
