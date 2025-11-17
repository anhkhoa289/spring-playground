package com.khoa.spring.playground.controller;

import com.khoa.spring.playground.entity.Post;
import com.khoa.spring.playground.entity.User;
import com.khoa.spring.playground.repository.PostRepository;
import com.khoa.spring.playground.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<Post>> getAllPosts() {
        return ResponseEntity.ok(postRepository.findAll());
    }

    @GetMapping("/{id}")
    @Cacheable(value = "posts", key = "#id")
    public ResponseEntity<Post> getPostById(@PathVariable Long id) {
        return postRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Post>> getPostsByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(postRepository.findByUserId(userId));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Post>> searchPostsByTitle(@RequestParam String title) {
        return ResponseEntity.ok(postRepository.findByTitleContainingIgnoreCase(title));
    }

    @PostMapping
    @CacheEvict(value = "posts", allEntries = true)
    public ResponseEntity<Post> createPost(@RequestBody PostRequest request) {
        return userRepository.findById(request.getUserId())
            .map(user -> {
                Post post = new Post();
                post.setTitle(request.getTitle());
                post.setContent(request.getContent());
                post.setUser(user);
                Post savedPost = postRepository.save(post);
                return ResponseEntity.status(HttpStatus.CREATED).body(savedPost);
            })
            .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PutMapping("/{id}")
    @CacheEvict(value = "posts", allEntries = true)
    public ResponseEntity<Post> updatePost(@PathVariable Long id, @RequestBody PostRequest request) {
        return postRepository.findById(id)
            .map(existingPost -> {
                existingPost.setTitle(request.getTitle());
                existingPost.setContent(request.getContent());

                if (request.getUserId() != null && !request.getUserId().equals(existingPost.getUser().getId())) {
                    return userRepository.findById(request.getUserId())
                        .map(newUser -> {
                            existingPost.setUser(newUser);
                            return ResponseEntity.ok(postRepository.save(existingPost));
                        })
                        .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                }

                return ResponseEntity.ok(postRepository.save(existingPost));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @CacheEvict(value = "posts", allEntries = true)
    public ResponseEntity<Void> deletePost(@PathVariable Long id) {
        if (postRepository.existsById(id)) {
            postRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // Inner class for request body
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PostRequest {
        private String title;
        private String content;
        private Long userId;
    }
}
