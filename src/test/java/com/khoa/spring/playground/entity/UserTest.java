package com.khoa.spring.playground.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUsername("testuser");
        user.setEmail("test@example.com");
    }

    @Test
    void onCreate_ShouldSetCreatedAt() {
        // Arrange
        assertNull(user.getCreatedAt());
        LocalDateTime beforeCall = LocalDateTime.now();

        // Act
        user.onCreate();

        // Assert
        assertNotNull(user.getCreatedAt());
        assertTrue(user.getCreatedAt().isAfter(beforeCall.minusSeconds(1)));
        assertTrue(user.getCreatedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    void constructor_NoArgs_ShouldCreateEmptyUser() {
        // Act
        User newUser = new User();

        // Assert
        assertNull(newUser.getId());
        assertNull(newUser.getUsername());
        assertNull(newUser.getEmail());
        assertNotNull(newUser.getPosts());
        assertTrue(newUser.getPosts().isEmpty());
        assertNull(newUser.getCreatedAt());
    }

    @Test
    void constructor_AllArgs_ShouldCreateUserWithAllFields() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        ArrayList<Post> posts = new ArrayList<>();

        // Act
        User newUser = new User(1L, "user", "user@test.com", posts, now);

        // Assert
        assertEquals(1L, newUser.getId());
        assertEquals("user", newUser.getUsername());
        assertEquals("user@test.com", newUser.getEmail());
        assertEquals(posts, newUser.getPosts());
        assertEquals(now, newUser.getCreatedAt());
    }

    @Test
    void settersAndGetters_ShouldWorkCorrectly() {
        // Act
        user.setId(1L);
        user.setUsername("newusername");
        user.setEmail("newemail@test.com");
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        ArrayList<Post> posts = new ArrayList<>();
        user.setPosts(posts);

        // Assert
        assertEquals(1L, user.getId());
        assertEquals("newusername", user.getUsername());
        assertEquals("newemail@test.com", user.getEmail());
        assertEquals(now, user.getCreatedAt());
        assertEquals(posts, user.getPosts());
    }

    @Test
    void posts_ShouldBeInitializedAsEmptyList() {
        // Arrange
        User newUser = new User();

        // Assert
        assertNotNull(newUser.getPosts());
        assertTrue(newUser.getPosts() instanceof ArrayList);
        assertEquals(0, newUser.getPosts().size());
    }

    @Test
    void equals_ShouldWorkCorrectly() {
        // Arrange
        User user1 = new User();
        user1.setId(1L);
        user1.setUsername("user");
        user1.setEmail("test@test.com");

        User user2 = new User();
        user2.setId(1L);
        user2.setUsername("user");
        user2.setEmail("test@test.com");

        // Assert
        assertEquals(user1, user2);
    }

    @Test
    void hashCode_ShouldBeConsistent() {
        // Arrange
        User user1 = new User();
        user1.setId(1L);
        user1.setUsername("user");

        User user2 = new User();
        user2.setId(1L);
        user2.setUsername("user");

        // Assert
        assertEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    void toString_ShouldContainAllFields() {
        // Arrange
        user.setId(1L);
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);

        // Act
        String result = user.toString();

        // Assert
        assertTrue(result.contains("testuser"));
        assertTrue(result.contains("test@example.com"));
        assertTrue(result.contains("1"));
    }
}
