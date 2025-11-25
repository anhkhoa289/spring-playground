package com.khoa.spring.playground.repository;

import com.khoa.spring.playground.entity.Post;
import com.khoa.spring.playground.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class PostRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private Post testPost;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser = entityManager.persistAndFlush(testUser);

        testPost = new Post();
        testPost.setTitle("Test Post");
        testPost.setContent("Test Content");
        testPost.setUser(testUser);
    }

    @Test
    void findByUserId_ShouldReturnPosts_WhenUserHasPosts() {
        // Arrange
        Post post2 = new Post();
        post2.setTitle("Second Post");
        post2.setContent("Second Content");
        post2.setUser(testUser);

        entityManager.persistAndFlush(testPost);
        entityManager.persistAndFlush(post2);

        // Act
        List<Post> posts = postRepository.findByUserId(testUser.getId());

        // Assert
        assertEquals(2, posts.size());
        assertTrue(posts.stream().anyMatch(p -> p.getTitle().equals("Test Post")));
        assertTrue(posts.stream().anyMatch(p -> p.getTitle().equals("Second Post")));
    }

    @Test
    void findByUserId_ShouldReturnEmptyList_WhenUserHasNoPosts() {
        // Arrange
        User anotherUser = new User();
        anotherUser.setUsername("anotheruser");
        anotherUser.setEmail("another@example.com");
        anotherUser = entityManager.persistAndFlush(anotherUser);

        entityManager.persistAndFlush(testPost);

        // Act
        List<Post> posts = postRepository.findByUserId(anotherUser.getId());

        // Assert
        assertTrue(posts.isEmpty());
    }

    @Test
    void findByUserId_ShouldReturnEmptyList_WhenUserDoesNotExist() {
        // Act
        List<Post> posts = postRepository.findByUserId(999L);

        // Assert
        assertTrue(posts.isEmpty());
    }

    @Test
    void findByTitleContainingIgnoreCase_ShouldReturnMatchingPosts() {
        // Arrange
        Post post2 = new Post();
        post2.setTitle("Another Test");
        post2.setContent("Content");
        post2.setUser(testUser);

        Post post3 = new Post();
        post3.setTitle("Different Title");
        post3.setContent("Content");
        post3.setUser(testUser);

        entityManager.persistAndFlush(testPost);
        entityManager.persistAndFlush(post2);
        entityManager.persistAndFlush(post3);

        // Act
        List<Post> posts = postRepository.findByTitleContainingIgnoreCase("test");

        // Assert
        assertEquals(2, posts.size());
        assertTrue(posts.stream().anyMatch(p -> p.getTitle().equals("Test Post")));
        assertTrue(posts.stream().anyMatch(p -> p.getTitle().equals("Another Test")));
    }

    @Test
    void findByTitleContainingIgnoreCase_ShouldBeCaseInsensitive() {
        // Arrange
        entityManager.persistAndFlush(testPost);

        // Act
        List<Post> postsLower = postRepository.findByTitleContainingIgnoreCase("test");
        List<Post> postsUpper = postRepository.findByTitleContainingIgnoreCase("TEST");
        List<Post> postsMixed = postRepository.findByTitleContainingIgnoreCase("TeSt");

        // Assert
        assertEquals(1, postsLower.size());
        assertEquals(1, postsUpper.size());
        assertEquals(1, postsMixed.size());
    }

    @Test
    void findByTitleContainingIgnoreCase_ShouldReturnEmptyList_WhenNoMatch() {
        // Arrange
        entityManager.persistAndFlush(testPost);

        // Act
        List<Post> posts = postRepository.findByTitleContainingIgnoreCase("nonexistent");

        // Assert
        assertTrue(posts.isEmpty());
    }

    @Test
    void findByTitleContainingIgnoreCase_ShouldMatchPartialTitle() {
        // Arrange
        entityManager.persistAndFlush(testPost);

        // Act
        List<Post> posts = postRepository.findByTitleContainingIgnoreCase("Post");

        // Assert
        assertEquals(1, posts.size());
        assertEquals("Test Post", posts.get(0).getTitle());
    }

    @Test
    void save_ShouldPersistPost() {
        // Act
        Post saved = postRepository.save(testPost);

        // Assert
        assertNotNull(saved.getId());
        assertEquals("Test Post", saved.getTitle());
        assertEquals("Test Content", saved.getContent());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void save_ShouldTriggerPrePersist() {
        // Arrange
        assertNull(testPost.getCreatedAt());
        assertNull(testPost.getUpdatedAt());

        // Act
        Post saved = postRepository.save(testPost);

        // Assert
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void update_ShouldTriggerPreUpdate() throws InterruptedException {
        // Arrange
        Post saved = entityManager.persistAndFlush(testPost);
        entityManager.detach(saved);

        // Small delay to ensure different timestamp
        Thread.sleep(10);

        // Act
        saved.setTitle("Updated Title");
        Post updated = postRepository.saveAndFlush(saved);

        // Assert
        assertEquals("Updated Title", updated.getTitle());
        assertNotNull(updated.getUpdatedAt());
        // Updated time should be after or equal to created time
        assertTrue(updated.getUpdatedAt().compareTo(updated.getCreatedAt()) >= 0);
    }

    @Test
    void findById_ShouldReturnPost_WhenExists() {
        // Arrange
        Post saved = entityManager.persistAndFlush(testPost);

        // Act
        Optional<Post> found = postRepository.findById(saved.getId());

        // Assert
        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
        assertEquals("Test Post", found.get().getTitle());
    }

    @Test
    void deleteById_ShouldRemovePost() {
        // Arrange
        Post saved = entityManager.persistAndFlush(testPost);
        Long postId = saved.getId();

        // Act
        postRepository.deleteById(postId);
        entityManager.flush();

        // Assert
        Optional<Post> found = postRepository.findById(postId);
        assertFalse(found.isPresent());
    }

    @Test
    void findAll_ShouldReturnAllPosts() {
        // Arrange
        Post post2 = new Post();
        post2.setTitle("Post 2");
        post2.setContent("Content 2");
        post2.setUser(testUser);

        entityManager.persistAndFlush(testPost);
        entityManager.persistAndFlush(post2);

        // Act
        var posts = postRepository.findAll();

        // Assert
        assertEquals(2, posts.size());
    }

    @Test
    void databaseCascadeDelete_ShouldDeletePostsWhenUserIsDeleted() {
        // Arrange
        entityManager.persistAndFlush(testPost);
        Long postId = testPost.getId();
        Long userId = testUser.getId();

        // Act
        // Using repository instead of entityManager to trigger database-level cascade
        userRepository.deleteById(userId);
        entityManager.flush();
        entityManager.clear();

        // Assert
        // Database-level cascade delete should remove posts
        Optional<Post> foundPost = postRepository.findById(postId);
        assertFalse(foundPost.isPresent());
    }

    @Test
    void noCascade_PostsShouldNotBeDeletedWhenRemovedFromUserCollection() {
        // Arrange
        entityManager.persistAndFlush(testPost);
        Long postId = testPost.getId();

        // Act
        // Removing from collection should NOT delete posts (no orphan removal)
        testUser.getPosts().clear();
        entityManager.merge(testUser);
        entityManager.flush();
        entityManager.clear();

        // Assert
        // Post should still exist because orphan removal is not configured
        Optional<Post> foundPost = postRepository.findById(postId);
        assertTrue(foundPost.isPresent());
    }
}
