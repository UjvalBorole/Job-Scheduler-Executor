package com.JobConsumerSvc.repositories;

import com.JobConsumerSvc.entities4.DepTracker;
import com.JobConsumerSvc.entities4.JobStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DepTrackerRepository extends MongoRepository<DepTracker, String> {

    List<DepTracker> findByJobStatus(JobStatus status);

    List<DepTracker> findByJobId(String jobId);

    List<DepTracker> findByExecutorId(String executorId);

    List<DepTracker> findByDependenciesContaining(String dependencyId);

    List<DepTracker> findByJobNameContainingIgnoreCase(String jobName);
}