package com.khoa.spring.playground.repository;

import com.khoa.spring.playground.entity.DeleteJob;
import com.khoa.spring.playground.entity.DeleteJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DeleteJobRepository extends JpaRepository<DeleteJob, String> {

    /**
     * Find all jobs by user ID
     */
    List<DeleteJob> findByUserId(Long userId);

    /**
     * Find all jobs by status
     */
    List<DeleteJob> findByStatus(DeleteJobStatus status);

    /**
     * Find jobs older than specified date
     * Useful for cleanup of old completed/failed jobs
     */
    List<DeleteJob> findByCreatedAtBefore(LocalDateTime dateTime);

    /**
     * Count pending/in-progress jobs for a user
     * Useful to prevent multiple concurrent delete operations
     */
    long countByUserIdAndStatusIn(Long userId, List<DeleteJobStatus> statuses);
}
