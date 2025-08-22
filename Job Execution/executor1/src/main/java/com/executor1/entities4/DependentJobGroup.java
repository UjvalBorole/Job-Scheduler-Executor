package com.executor1.entities4;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DependentJobGroup {
    private String jobId;             // Single job
    private LocalDateTime scheduleTime; // Its execution time
}
