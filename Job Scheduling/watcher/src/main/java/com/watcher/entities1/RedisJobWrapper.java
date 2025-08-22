package com.watcher.entities1;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisJobWrapper {
    private Long id;
    private LocalDateTime time;
    private Job job;
}
