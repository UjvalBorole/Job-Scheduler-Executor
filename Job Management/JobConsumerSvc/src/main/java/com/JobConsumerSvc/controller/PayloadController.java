package com.JobConsumerSvc.controller;

import com.JobConsumerSvc.entities2.Payload;
import com.JobConsumerSvc.service.PayloadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payloads") // base URL: /payloads
public class PayloadController {

    @Autowired
    private PayloadService payloadService;

    @GetMapping("/{id}")
    public ResponseEntity<Payload> getPayload(@PathVariable Long id) {
        Payload payload = payloadService.getPayloadById(id);
        return ResponseEntity.ok(payload);
    }
}
