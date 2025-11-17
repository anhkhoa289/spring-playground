package com.khoa.spring.playground.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

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
    void onCreate_ShouldSetCreatedAtAndUpdatedAt() {
        // Arrange
        assertNull(post.getCreatedAt());
        assertNull(post.getUpdatedAt());
        LocalDateTime beforeCall = LocalDateTime.now();

        // Act
        post.onCreate();

        // Assert
        assertNotNull(post.getCreatedAt());
        assertNotNull(post.getUpdatedAt());
        assertTrue(post.getCreatedAt().isAfter(beforeCall.minusSeconds(1)));
        assertTrue(post.getUpdatedAt().isAfter(beforeCall.minusSeconds(1)));
        assertTrue(post.getCreatedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
        assertTrue(post.getUpdatedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    void onCreate_ShouldSetBothTimestampsToSameValue() {
        // Act
        post.onCreate();

        // Assert
        assertNotNull(post.getCreatedAt());
        assertNotNull(post.getUpdatedAt());
        // Both timestamps should be equal or very close (within same second)
        assertEquals(post.getCreatedAt(), post.getUpdatedAt());
    }

    @Test
    void onUpdate_ShouldUpdateUpdatedAtOnly() throws InterruptedException {
        // Arrange
        post.onCreate();
        LocalDateTime originalCreatedAt = post.getCreatedAt();
        LocalDateTime originalUpdatedAt = post.getUpdatedAt();

        // Small delay to ensure different timestamp
        Thread.sleep(10);

        // Act
        post.onUpdate();

        // Assert
        assertEquals(originalCreatedAt, post.getCreatedAt()); // createdAt should not change
        assertNotEquals(originalUpdatedAt, post.getUpdatedAt()); // updatedAt should change
        assertTrue(post.getUpdatedAt().isAfter(originalUpdatedAt));
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
        // Arrange
        LocalDateTime now = LocalDateTime.now();

        // Act
        Post newPost = new Post(1L, "Title", "Content", user, now, now);

        // Assert
        assertEquals(1L, newPost.getId());
        assertEquals("Title", newPost.getTitle());
        assertEquals("Content", newPost.getContent());
        assertEquals(user, newPost.getUser());
        assertEquals(now, newPost.getCreatedAt());
        assertEquals(now, newPost.getUpdatedAt());
    }

    @Test
    void settersAndGetters_ShouldWorkCorrectly() {
        // Act
        post.setId(1L);
        post.setTitle("New Title");
        post.setContent("New Content");
        LocalDateTime now = LocalDateTime.now();
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
        LocalDateTime now = LocalDateTime.now();

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
        LocalDateTime now = LocalDateTime.now();
        post.setCreatedAt(now);
        post.setUpdatedAt(now);

        // Act
        String result = post.toString();

        // Assert
        assertTrue(result.contains("Test Post"));
        assertTrue(result.contains("Test Content"));
        assertTrue(result.contains("1"));
    }

    @Test
    void multipleUpdates_ShouldKeepUpdatingTimestamp() throws InterruptedException {
        // Arrange
        post.onCreate();
        LocalDateTime firstCreated = post.getCreatedAt();

        // Act & Assert - First update
        Thread.sleep(10);
        post.onUpdate();
        LocalDateTime firstUpdate = post.getUpdatedAt();
        assertEquals(firstCreated, post.getCreatedAt());
        assertTrue(firstUpdate.isAfter(firstCreated));

        // Act & Assert - Second update
        Thread.sleep(10);
        post.onUpdate();
        LocalDateTime secondUpdate = post.getUpdatedAt();
        assertEquals(firstCreated, post.getCreatedAt());
        assertTrue(secondUpdate.isAfter(firstUpdate));
    }
}
