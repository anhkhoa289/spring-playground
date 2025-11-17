package com.khoa.spring.playground.repository;

import com.khoa.spring.playground.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    /**
     * Find all favorites by user ID
     */
    List<Favorite> findByUserId(Long userId);

    /**
     * Count favorites by user ID
     */
    long countByUserId(Long userId);
}
