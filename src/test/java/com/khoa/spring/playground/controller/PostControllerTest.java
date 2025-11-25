package com.khoa.spring.playground.controller;

import com.khoa.spring.playground.controller.PostController.PostRequest;
import com.khoa.spring.playground.entity.Post;
import com.khoa.spring.playground.entity.User;
import com.khoa.spring.playground.repository.PostRepository;
import com.khoa.spring.playground.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostControllerTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PostController postController;

    private User testUser;
    private Post testPost;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");

        testPost = new Post();
        testPost.setId(1L);
        testPost.setTitle("Test Post");
        testPost.setContent("Test Content");
        testPost.setUser(testUser);
        testPost.setCreatedAt(Instant.now());
        testPost.setUpdatedAt(Instant.now());
    }

    @Test
    void getAllPosts_ShouldReturnAllPosts() {
        // Arrange
        Post post2 = new Post();
        post2.setId(2L);
        post2.setTitle("Post 2");
        post2.setContent("Content 2");
        post2.setUser(testUser);

        List<Post> posts = Arrays.asList(testPost, post2);
        when(postRepository.findAll()).thenReturn(posts);

        // Act
        ResponseEntity<List<Post>> response = postController.getAllPosts();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        verify(postRepository, times(1)).findAll();
    }

    @Test
    void getAllPosts_ShouldReturnEmptyList_WhenNoPosts() {
        // Arrange
        when(postRepository.findAll()).thenReturn(Arrays.asList());

        // Act
        ResponseEntity<List<Post>> response = postController.getAllPosts();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
        verify(postRepository, times(1)).findAll();
    }

    @Test
    void getPostById_ShouldReturnPost_WhenPostExists() {
        // Arrange
        when(postRepository.findById(1L)).thenReturn(Optional.of(testPost));

        // Act
        ResponseEntity<Post> response = postController.getPostById(1L);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Test Post", response.getBody().getTitle());
        assertEquals("Test Content", response.getBody().getContent());
        verify(postRepository, times(1)).findById(1L);
    }

    @Test
    void getPostById_ShouldReturnNotFound_WhenPostDoesNotExist() {
        // Arrange
        when(postRepository.findById(anyLong())).thenReturn(Optional.empty());

        // Act
        ResponseEntity<Post> response = postController.getPostById(999L);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
        verify(postRepository, times(1)).findById(999L);
    }

    @Test
    void getPostsByUserId_ShouldReturnPosts() {
        // Arrange
        List<Post> userPosts = Arrays.asList(testPost);
        when(postRepository.findByUserId(1L)).thenReturn(userPosts);

        // Act
        ResponseEntity<List<Post>> response = postController.getPostsByUserId(1L);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("Test Post", response.getBody().get(0).getTitle());
        verify(postRepository, times(1)).findByUserId(1L);
    }

    @Test
    void getPostsByUserId_ShouldReturnEmptyList_WhenNoPostsFound() {
        // Arrange
        when(postRepository.findByUserId(anyLong())).thenReturn(Arrays.asList());

        // Act
        ResponseEntity<List<Post>> response = postController.getPostsByUserId(999L);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
        verify(postRepository, times(1)).findByUserId(999L);
    }

    @Test
    void searchPostsByTitle_ShouldReturnMatchingPosts() {
        // Arrange
        List<Post> posts = Arrays.asList(testPost);
        when(postRepository.findByTitleContainingIgnoreCase("Test")).thenReturn(posts);

        // Act
        ResponseEntity<List<Post>> response = postController.searchPostsByTitle("Test");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("Test Post", response.getBody().get(0).getTitle());
        verify(postRepository, times(1)).findByTitleContainingIgnoreCase("Test");
    }

    @Test
    void searchPostsByTitle_ShouldReturnEmptyList_WhenNoMatches() {
        // Arrange
        when(postRepository.findByTitleContainingIgnoreCase(anyString())).thenReturn(Arrays.asList());

        // Act
        ResponseEntity<List<Post>> response = postController.searchPostsByTitle("NonExistent");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
        verify(postRepository, times(1)).findByTitleContainingIgnoreCase("NonExistent");
    }

    @Test
    void createPost_ShouldReturnCreatedPost_WhenUserExists() {
        // Arrange
        PostRequest request = new PostRequest("New Post", "New Content", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(postRepository.save(any(Post.class))).thenReturn(testPost);

        // Act
        ResponseEntity<Post> response = postController.createPost(request);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(userRepository, times(1)).findById(1L);
        verify(postRepository, times(1)).save(any(Post.class));
    }

    @Test
    void createPost_ShouldReturnNotFound_WhenUserDoesNotExist() {
        // Arrange
        PostRequest request = new PostRequest("New Post", "New Content", 999L);
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // Act
        ResponseEntity<Post> response = postController.createPost(request);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
        verify(userRepository, times(1)).findById(999L);
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void updatePost_ShouldUpdateAndReturnPost_WhenPostExists_WithoutUserChange() {
        // Arrange
        PostRequest request = new PostRequest("Updated Title", "Updated Content", 1L);
        when(postRepository.findById(1L)).thenReturn(Optional.of(testPost));
        when(postRepository.save(any(Post.class))).thenReturn(testPost);

        // Act
        ResponseEntity<Post> response = postController.updatePost(1L, request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(postRepository, times(1)).findById(1L);
        verify(postRepository, times(1)).save(any(Post.class));
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void updatePost_ShouldUpdateAndReturnPost_WhenPostExists_WithUserChange() {
        // Arrange
        User newUser = new User();
        newUser.setId(2L);
        newUser.setUsername("newuser");

        PostRequest request = new PostRequest("Updated Title", "Updated Content", 2L);
        when(postRepository.findById(1L)).thenReturn(Optional.of(testPost));
        when(userRepository.findById(2L)).thenReturn(Optional.of(newUser));
        when(postRepository.save(any(Post.class))).thenReturn(testPost);

        // Act
        ResponseEntity<Post> response = postController.updatePost(1L, request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(postRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).findById(2L);
        verify(postRepository, times(1)).save(any(Post.class));
    }

    @Test
    void updatePost_ShouldReturnNotFound_WhenPostExists_ButNewUserDoesNotExist() {
        // Arrange
        PostRequest request = new PostRequest("Updated Title", "Updated Content", 999L);
        when(postRepository.findById(1L)).thenReturn(Optional.of(testPost));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // Act
        ResponseEntity<Post> response = postController.updatePost(1L, request);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(postRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).findById(999L);
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void updatePost_ShouldReturnNotFound_WhenPostDoesNotExist() {
        // Arrange
        PostRequest request = new PostRequest("Updated Title", "Updated Content", 1L);
        when(postRepository.findById(anyLong())).thenReturn(Optional.empty());

        // Act
        ResponseEntity<Post> response = postController.updatePost(999L, request);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
        verify(postRepository, times(1)).findById(999L);
        verify(postRepository, never()).save(any(Post.class));
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void updatePost_ShouldNotChangeUser_WhenUserIdIsNull() {
        // Arrange
        PostRequest request = new PostRequest("Updated Title", "Updated Content", null);
        when(postRepository.findById(1L)).thenReturn(Optional.of(testPost));
        when(postRepository.save(any(Post.class))).thenReturn(testPost);

        // Act
        ResponseEntity<Post> response = postController.updatePost(1L, request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(postRepository, times(1)).findById(1L);
        verify(postRepository, times(1)).save(any(Post.class));
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void deletePost_ShouldReturnNoContent_WhenPostExists() {
        // Arrange
        when(postRepository.existsById(1L)).thenReturn(true);
        doNothing().when(postRepository).deleteById(1L);

        // Act
        ResponseEntity<Void> response = postController.deletePost(1L);

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(postRepository, times(1)).existsById(1L);
        verify(postRepository, times(1)).deleteById(1L);
    }

    @Test
    void deletePost_ShouldReturnNotFound_WhenPostDoesNotExist() {
        // Arrange
        when(postRepository.existsById(anyLong())).thenReturn(false);

        // Act
        ResponseEntity<Void> response = postController.deletePost(999L);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(postRepository, times(1)).existsById(999L);
        verify(postRepository, never()).deleteById(anyLong());
    }
}
