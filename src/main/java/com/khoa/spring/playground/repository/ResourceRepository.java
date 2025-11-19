package com.khoa.spring.playground.repository;

import com.khoa.spring.playground.entity.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, Long> {

    /**
     * Find all resources by user ID
     */
    List<Resource> findByUserId(Long userId);

    /**
     * Count resources by user ID
     */
    long countByUserId(Long userId);

    /**
     * Count total resource details for a user
     * Useful for tracking total records to be deleted
     */
    @Query("SELECT COUNT(rd) FROM Resource r JOIN r.resourceDetails rd WHERE r.user.id = :userId")
    long countResourceDetailsByUserId(@Param("userId") Long userId);
}
