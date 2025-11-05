package com.executor1.entities4;

import lombok.*;

@Getter
@Setter @AllArgsConstructor
@Builder
public class ExecutionResult {
    private JobStatus status;
    private String errorMessage;
}
