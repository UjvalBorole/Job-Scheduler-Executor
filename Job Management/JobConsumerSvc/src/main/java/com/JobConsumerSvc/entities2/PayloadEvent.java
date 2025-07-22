package com.JobConsumerSvc.entities2;


import com.JobConsumerSvc.dto.PayloadRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayloadEvent {
    private PayloadRequest payload;
    private String payloadId; // For DELETE or UPDATE by ID
    private PayloadEventType eventType;
}
