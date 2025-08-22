package com.executor1.entities2;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayloadEvent {
    private Payload payload;
    private String payloadId; // For DELETE or UPDATE by ID
    private PayloadEventType eventType;
}
