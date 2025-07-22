package com.JobConsumerSvc.repositories;


import com.JobConsumerSvc.entities1.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {
    @Query("SELECT j FROM Job j LEFT JOIN FETCH j.payloads WHERE j.id = :id")
    Optional<Job> findByIdWithPayloads(Long id);

//    @Query("SELECT j FROM Job j LEFT JOIN FETCH j.payloads ORDER BY j.modifiedTime DESC LIMIT 1")
//    Job findTopByOrderByModifiedTimeDesc();
        Job findFirstByOrderByModifiedTimeDesc();
}
