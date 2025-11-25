package com.khoa.spring.playground.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PostTest {

    private Post post;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setEmail("test@example.com");

        post = new Post();
        post.setTitle("Test Post");
        post.setContent("Test Content");
        post.setUser(user);
    }

    @Test
    void timestamps_ShouldBeNullByDefault() {
        // Assert
        assertNull(post.getCreatedAt());
        assertNull(post.getUpdatedAt());
    }

    @Test
    void timestamps_CanBeSetManually() {
        // Arrange
        Instant now = Instant.now();

        // Act
        post.setCreatedAt(now);
        post.setUpdatedAt(now);

        // Assert
        assertNotNull(post.getCreatedAt());
        assertNotNull(post.getUpdatedAt());
        assertEquals(now, post.getCreatedAt());
        assertEquals(now, post.getUpdatedAt());
    }

    @Test
    void constructor_NoArgs_ShouldCreateEmptyPost() {
        // Act
        Post newPost = new Post();

        // Assert
        assertNull(newPost.getId());
        assertNull(newPost.getTitle());
        assertNull(newPost.getContent());
        assertNull(newPost.getUser());
        assertNull(newPost.getCreatedAt());
        assertNull(newPost.getUpdatedAt());
    }

    @Test
    void constructor_AllArgs_ShouldCreatePostWithAllFields() {
        // Arrange & Act
        Post newPost = new Post(1L, "Title", "Content", user);

        // Assert
        assertEquals(1L, newPost.getId());
        assertEquals("Title", newPost.getTitle());
        assertEquals("Content", newPost.getContent());
        assertEquals(user, newPost.getUser());
    }

    @Test
    void settersAndGetters_ShouldWorkCorrectly() {
        // Act
        post.setId(1L);
        post.setTitle("New Title");
        post.setContent("New Content");
        Instant now = Instant.now();
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        User newUser = new User();
        post.setUser(newUser);

        // Assert
        assertEquals(1L, post.getId());
        assertEquals("New Title", post.getTitle());
        assertEquals("New Content", post.getContent());
        assertEquals(now, post.getCreatedAt());
        assertEquals(now, post.getUpdatedAt());
        assertEquals(newUser, post.getUser());
    }

    @Test
    void equals_ShouldWorkCorrectly() {
        // Arrange
        Instant now = Instant.now();

        Post post1 = new Post();
        post1.setId(1L);
        post1.setTitle("Title");
        post1.setContent("Content");
        post1.setUser(user);
        post1.setCreatedAt(now);
        post1.setUpdatedAt(now);

        Post post2 = new Post();
        post2.setId(1L);
        post2.setTitle("Title");
        post2.setContent("Content");
        post2.setUser(user);
        post2.setCreatedAt(now);
        post2.setUpdatedAt(now);

        // Assert
        assertEquals(post1, post2);
    }

    @Test
    void hashCode_ShouldBeConsistent() {
        // Arrange
        Post post1 = new Post();
        post1.setId(1L);
        post1.setTitle("Title");
        post1.setContent("Content");

        Post post2 = new Post();
        post2.setId(1L);
        post2.setTitle("Title");
        post2.setContent("Content");

        // Assert
        assertEquals(post1.hashCode(), post2.hashCode());
    }

    @Test
    void toString_ShouldContainAllFields() {
        // Arrange
        post.setId(1L);
        Instant now = Instant.now();
        post.setCreatedAt(now);
        post.setUpdatedAt(now);

        // Act
        String result = post.toString();

        // Assert
        assertTrue(result.contains("Test Post"));
        assertTrue(result.contains("Test Content"));
        assertTrue(result.contains("1"));
    }
}
