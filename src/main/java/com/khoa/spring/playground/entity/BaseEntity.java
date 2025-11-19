package com.khoa.spring.playground.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.Instant;

/**
 * Base entity class that provides common fields for all entities:
 * - Serializable implementation for Hazelcast cache support
 * - Version field for optimistic locking
 * - Creation and update timestamps (UTC-based using Instant)
 *
 * Using Instant instead of LocalDateTime ensures:
 * - Timezone-independent timestamps (always UTC)
 * - Consistent behavior across different server timezones
 * - Proper handling for multi-timezone applications
 * - Best practice for audit fields
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity implements Serializable {

    @Version
    @Column(name = "version")
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
