package com.khoa.spring.playground.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for tracking async user deletion jobs
 * Provides progress tracking and status monitoring
 */
@Entity
@Table(name = "delete_jobs")
@EqualsAndHashCode(callSuper = false)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteJob extends BaseEntity {

    @Id
    private String id;  // UUID

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeleteJobStatus status;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_records")
    private Long totalRecords;

    @Column(name = "processed_records")
    private Long processedRecords;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Calculate progress percentage (0-100)
     */
    public int getProgress() {
        if (totalRecords == null || totalRecords == 0) {
            return 0;
        }
        if (processedRecords == null) {
            return 0;
        }
        return (int) ((processedRecords * 100) / totalRecords);
    }

    @PrePersist
    protected void onCreate() {
        if (processedRecords == null) {
            processedRecords = 0L;
        }
    }
}
