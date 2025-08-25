package com.JobConsumerSvc.repositories;

import com.JobConsumerSvc.entities4.DepTracker;
import com.JobConsumerSvc.entities4.JobStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DepTrackerRepository extends MongoRepository<DepTracker, String> {
    Optional<DepTracker> findByJobIdAndScheduleTime(String jobId, LocalDateTime scheduleTime);
    List<DepTracker> findByJobStatus(JobStatus status);

    List<DepTracker> findByJobId(String jobId);

    List<DepTracker> findByExecutorId(String executorId);

    List<DepTracker> findByDependenciesContaining(String dependencyId);

    List<DepTracker> findByJobNameContainingIgnoreCase(String jobName);
}