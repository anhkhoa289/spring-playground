package com.khoa.spring.playground.repository;

import com.khoa.spring.playground.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findByUserId(Long userId);

    List<Post> findByTitleContainingIgnoreCase(String title);
}
