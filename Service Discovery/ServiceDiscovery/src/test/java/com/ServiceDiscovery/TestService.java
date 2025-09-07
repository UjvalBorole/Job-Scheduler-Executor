package com.ServiceDiscovery;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class TestService {

    private final DiscoveryClient discoveryClient;

    public TestService(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }


    @PostConstruct
    public void checkInstances() {
        System.out.println("🔍 Eureka instances of JobConsumerSvc: "
                + discoveryClient.getInstances("JOBCONSUMERSVC"));
    }
}
