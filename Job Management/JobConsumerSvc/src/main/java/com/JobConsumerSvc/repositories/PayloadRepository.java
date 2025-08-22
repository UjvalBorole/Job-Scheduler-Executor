package com.JobConsumerSvc.repositories;


import com.JobConsumerSvc.entities2.Payload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayloadRepository extends JpaRepository<Payload, Long> {
    List<Payload> findByJobId(Long jobId);
    Optional<Payload> findById(Long id);
}
