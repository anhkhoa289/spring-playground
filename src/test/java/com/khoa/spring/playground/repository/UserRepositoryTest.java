package com.khoa.spring.playground.repository;

import com.khoa.spring.playground.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
    }

    @Test
    void findByUsername_ShouldReturnUser_WhenUsernameExists() {
        // Arrange
        entityManager.persistAndFlush(testUser);

        // Act
        Optional<User> found = userRepository.findByUsername("testuser");

        // Assert
        assertTrue(found.isPresent());
        assertEquals("testuser", found.get().getUsername());
        assertEquals("test@example.com", found.get().getEmail());
    }

    @Test
    void findByUsername_ShouldReturnEmpty_WhenUsernameDoesNotExist() {
        // Act
        Optional<User> found = userRepository.findByUsername("nonexistent");

        // Assert
        assertFalse(found.isPresent());
    }

    @Test
    void findByUsername_ShouldBeCaseExact() {
        // Arrange
        entityManager.persistAndFlush(testUser);

        // Act
        Optional<User> found = userRepository.findByUsername("TESTUSER");

        // Assert
        assertFalse(found.isPresent());
    }

    @Test
    void findByEmail_ShouldReturnUser_WhenEmailExists() {
        // Arrange
        entityManager.persistAndFlush(testUser);

        // Act
        Optional<User> found = userRepository.findByEmail("test@example.com");

        // Assert
        assertTrue(found.isPresent());
        assertEquals("testuser", found.get().getUsername());
        assertEquals("test@example.com", found.get().getEmail());
    }

    @Test
    void findByEmail_ShouldReturnEmpty_WhenEmailDoesNotExist() {
        // Act
        Optional<User> found = userRepository.findByEmail("nonexistent@example.com");

        // Assert
        assertFalse(found.isPresent());
    }

    @Test
    void existsByUsername_ShouldReturnTrue_WhenUsernameExists() {
        // Arrange
        entityManager.persistAndFlush(testUser);

        // Act
        boolean exists = userRepository.existsByUsername("testuser");

        // Assert
        assertTrue(exists);
    }

    @Test
    void existsByUsername_ShouldReturnFalse_WhenUsernameDoesNotExist() {
        // Act
        boolean exists = userRepository.existsByUsername("nonexistent");

        // Assert
        assertFalse(exists);
    }

    @Test
    void save_ShouldPersistUser() {
        // Act
        User saved = userRepository.save(testUser);

        // Assert
        assertNotNull(saved.getId());
        assertEquals("testuser", saved.getUsername());
        assertEquals("test@example.com", saved.getEmail());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void save_ShouldTriggerPrePersist() {
        // Arrange
        assertNull(testUser.getCreatedAt());

        // Act
        User saved = userRepository.save(testUser);

        // Assert
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void findById_ShouldReturnUser_WhenExists() {
        // Arrange
        User saved = entityManager.persistAndFlush(testUser);

        // Act
        Optional<User> found = userRepository.findById(saved.getId());

        // Assert
        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
        assertEquals("testuser", found.get().getUsername());
    }

    @Test
    void deleteById_ShouldRemoveUser() {
        // Arrange
        User saved = entityManager.persistAndFlush(testUser);
        Long userId = saved.getId();

        // Act
        userRepository.deleteById(userId);
        entityManager.flush();

        // Assert
        Optional<User> found = userRepository.findById(userId);
        assertFalse(found.isPresent());
    }

    @Test
    void findAll_ShouldReturnAllUsers() {
        // Arrange
        User user2 = new User();
        user2.setUsername("user2");
        user2.setEmail("user2@example.com");

        entityManager.persistAndFlush(testUser);
        entityManager.persistAndFlush(user2);

        // Act
        var users = userRepository.findAll();

        // Assert
        assertEquals(2, users.size());
    }

    @Test
    void usernameConstraint_ShouldEnforceUniqueness() {
        // Arrange
        entityManager.persistAndFlush(testUser);

        User duplicate = new User();
        duplicate.setUsername("testuser");
        duplicate.setEmail("different@example.com");

        // Act & Assert
        assertThrows(Exception.class, () -> {
            entityManager.persistAndFlush(duplicate);
        });
    }
}
