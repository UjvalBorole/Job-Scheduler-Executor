package com.executor1.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

//KafkaListener must be FAST.
//Do NOT do business logic in it.
@Configuration
public class KafkaExecutorConfig {

    @Bean
    public Executor jobExecutor() {
        return Executors.newFixedThreadPool(10);
    }
}
