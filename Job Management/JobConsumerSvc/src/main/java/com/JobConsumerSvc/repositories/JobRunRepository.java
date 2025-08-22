package com.JobConsumerSvc.repositories;

import com.JobConsumerSvc.entities3.JobRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JobRunRepository extends JpaRepository<JobRun, Long> {
    Optional<JobRun> findByJobId(Long jobId);
    // ✅ Correct delete logic for primitive field
    @Modifying
    @Transactional
    @Query("DELETE FROM JobRun jr WHERE jr.jobId = :jobId")
    void deleteByJobId(Long jobId);


    @Query("SELECT jr FROM JobRun jr WHERE jr.id = :id")
    JobRun findJobRunById(Long id);

//    @Query("SELECT jr FROM JobRun jr WHERE jr.jobId = :jobId")
//    List<JobRun> findByJobId(Long jobId);

    @Query("SELECT jr FROM JobRun jr WHERE jr.status = :status")
    List<JobRun> findByStatus(Enum status); // use correct Enum type

    @Query("SELECT jr FROM JobRun jr WHERE jr.modifiedTime >= :from AND jr.modifiedTime <= :to")
    List<JobRun> findByModifiedTimeBetween(LocalDateTime from, LocalDateTime to);
}
