package com.khoa.spring.playground.service;

import com.khoa.spring.playground.entity.DeleteJob;
import com.khoa.spring.playground.entity.DeleteJobStatus;
import com.khoa.spring.playground.repository.DeleteJobRepository;
import com.khoa.spring.playground.repository.FavoriteRepository;
import com.khoa.spring.playground.repository.PostRepository;
import com.khoa.spring.playground.repository.ResourceRepository;
import com.khoa.spring.playground.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDeletionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private DeleteJobRepository deleteJobRepository;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache usersCache;

    @Mock
    private Cache postsCache;

    @Mock
    private Cache favoritesCache;

    @Mock
    private Cache resourcesCache;

    @Spy
    private UserDeletionService userDeletionService;

    private DeleteJob testJob;
    private Long testUserId;
    private String testJobId;

    @BeforeEach
    void setUp() {
        testUserId = 1L;
        testJobId = "test-job-id-123";

        testJob = new DeleteJob();
        testJob.setId(testJobId);
        testJob.setUserId(testUserId);
        testJob.setStatus(DeleteJobStatus.PENDING);
        testJob.setTotalRecords(100L);
        testJob.setProcessedRecords(0L);
        testJob.setCreatedAt(Instant.now());

        // Manually inject mocks into the spy
        userDeletionService = new UserDeletionService(
            userRepository,
            postRepository,
            favoriteRepository,
            resourceRepository,
            deleteJobRepository,
            cacheManager
        );
        userDeletionService = spy(userDeletionService);
    }

    // ==================== scheduleDelete Tests ====================

    @Test
    void scheduleDelete_ShouldScheduleSuccessfully_WhenUserExists() {
        // Arrange
        when(userRepository.existsById(testUserId)).thenReturn(true);
        when(deleteJobRepository.countByUserIdAndStatusIn(eq(testUserId), anyList())).thenReturn(0L);
        when(postRepository.countByUserId(testUserId)).thenReturn(50L);
        when(favoriteRepository.countByUserId(testUserId)).thenReturn(20L);
        when(resourceRepository.countByUserId(testUserId)).thenReturn(15L);
        when(resourceRepository.countResourceDetailsByUserId(testUserId)).thenReturn(15L);
        when(deleteJobRepository.save(any(DeleteJob.class))).thenAnswer(invocation -> {
            DeleteJob job = invocation.getArgument(0);
            job.setId(testJobId);
            return job;
        });

        // Mock the async call to prevent actual execution
        doReturn(CompletableFuture.completedFuture(null))
            .when(userDeletionService).deleteUserAsync(anyString());

        // Act
        String jobId = userDeletionService.scheduleDelete(testUserId);

        // Assert
        assertNotNull(jobId);
        assertEquals(testJobId, jobId);

        // Verify job was saved with correct data
        ArgumentCaptor<DeleteJob> jobCaptor = ArgumentCaptor.forClass(DeleteJob.class);
        verify(deleteJobRepository).save(jobCaptor.capture());
        DeleteJob savedJob = jobCaptor.getValue();

        assertEquals(testUserId, savedJob.getUserId());
        assertEquals(DeleteJobStatus.PENDING, savedJob.getStatus());
        assertEquals(100L, savedJob.getTotalRecords()); // 50 + 20 + 15 + 15
        assertEquals(0L, savedJob.getProcessedRecords());

        // Verify all repositories were called
        verify(userRepository).existsById(testUserId);
        verify(deleteJobRepository).countByUserIdAndStatusIn(eq(testUserId), anyList());
        verify(postRepository).countByUserId(testUserId);
        verify(favoriteRepository).countByUserId(testUserId);
        verify(resourceRepository).countByUserId(testUserId);
        verify(resourceRepository).countResourceDetailsByUserId(testUserId);
        verify(userDeletionService).deleteUserAsync(testJobId);
    }

    @Test
    void scheduleDelete_ShouldThrowException_WhenUserNotFound() {
        // Arrange
        when(userRepository.existsById(testUserId)).thenReturn(false);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> userDeletionService.scheduleDelete(testUserId)
        );

        assertEquals("User not found with id: " + testUserId, exception.getMessage());
        verify(userRepository).existsById(testUserId);
        verify(deleteJobRepository, never()).save(any());
    }

    @Test
    void scheduleDelete_ShouldThrowException_WhenPendingJobExists() {
        // Arrange
        when(userRepository.existsById(testUserId)).thenReturn(true);
        when(deleteJobRepository.countByUserIdAndStatusIn(eq(testUserId), anyList())).thenReturn(1L);

        // Act & Assert
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> userDeletionService.scheduleDelete(testUserId)
        );

        assertEquals("User deletion already in progress for user id: " + testUserId, exception.getMessage());
        verify(userRepository).existsById(testUserId);
        verify(deleteJobRepository).countByUserIdAndStatusIn(
            eq(testUserId),
            eq(Arrays.asList(DeleteJobStatus.PENDING, DeleteJobStatus.IN_PROGRESS))
        );
        verify(deleteJobRepository, never()).save(any());
    }

    @Test
    void scheduleDelete_ShouldThrowException_WhenInProgressJobExists() {
        // Arrange
        when(userRepository.existsById(testUserId)).thenReturn(true);
        when(deleteJobRepository.countByUserIdAndStatusIn(eq(testUserId), anyList())).thenReturn(2L);

        // Act & Assert
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> userDeletionService.scheduleDelete(testUserId)
        );

        assertTrue(exception.getMessage().contains("already in progress"));
        verify(deleteJobRepository, never()).save(any());
    }

    // ==================== deleteUserAsync Tests ====================

    @Test
    void deleteUserAsync_ShouldCompleteSuccessfully_WhenJobExists() throws Exception {
        // Arrange
        when(deleteJobRepository.findById(testJobId)).thenReturn(Optional.of(testJob));
        when(deleteJobRepository.save(any(DeleteJob.class))).thenReturn(testJob);
        when(cacheManager.getCache("users")).thenReturn(usersCache);
        when(cacheManager.getCache("posts")).thenReturn(postsCache);
        when(cacheManager.getCache("favorites")).thenReturn(favoritesCache);
        when(cacheManager.getCache("resources")).thenReturn(resourcesCache);
        doNothing().when(userRepository).deleteById(testUserId);

        // Act
        CompletableFuture<Void> future = userDeletionService.deleteUserAsync(testJobId);
        future.get(); // Wait for async operation

        // Assert
        // Verify job status updates
        ArgumentCaptor<DeleteJob> jobCaptor = ArgumentCaptor.forClass(DeleteJob.class);
        verify(deleteJobRepository, atLeast(2)).save(jobCaptor.capture());

        DeleteJob finalJob = jobCaptor.getAllValues().get(jobCaptor.getAllValues().size() - 1);
        assertEquals(DeleteJobStatus.COMPLETED, finalJob.getStatus());
        assertEquals(testJob.getTotalRecords(), finalJob.getProcessedRecords());
        assertNotNull(finalJob.getCompletedAt());

        // Verify user deletion
        verify(userRepository).deleteById(testUserId);

        // Verify cache eviction
        verify(usersCache).evict(testUserId);
        verify(postsCache).clear();
        verify(favoritesCache).clear();
        verify(resourcesCache).clear();
    }

    @Test
    void deleteUserAsync_ShouldHandleFailure_WhenDeletionFails() throws Exception {
        // Arrange
        when(deleteJobRepository.findById(testJobId)).thenReturn(Optional.of(testJob));
        when(deleteJobRepository.save(any(DeleteJob.class))).thenReturn(testJob);
        doThrow(new RuntimeException("Database error")).when(userRepository).deleteById(testUserId);

        // Act
        CompletableFuture<Void> future = userDeletionService.deleteUserAsync(testJobId);

        // Assert
        java.util.concurrent.ExecutionException exception = assertThrows(
            java.util.concurrent.ExecutionException.class,
            () -> future.get()
        );

        assertTrue(exception.getCause().getMessage().contains("Delete operation failed"));

        // Verify job status was set to FAILED
        ArgumentCaptor<DeleteJob> jobCaptor = ArgumentCaptor.forClass(DeleteJob.class);
        verify(deleteJobRepository, atLeast(2)).save(jobCaptor.capture());

        DeleteJob finalJob = jobCaptor.getAllValues().get(jobCaptor.getAllValues().size() - 1);
        assertEquals(DeleteJobStatus.FAILED, finalJob.getStatus());
        assertNotNull(finalJob.getErrorMessage());
        assertNotNull(finalJob.getCompletedAt());
    }

    @Test
    void deleteUserAsync_ShouldThrowException_WhenJobNotFound() {
        // Arrange
        when(deleteJobRepository.findById(testJobId)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> userDeletionService.deleteUserAsync(testJobId)
        );

        assertEquals("Job not found: " + testJobId, exception.getMessage());
        verify(deleteJobRepository).findById(testJobId);
        verify(userRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteUserAsync_ShouldHandleNullCaches_Gracefully() throws Exception {
        // Arrange
        when(deleteJobRepository.findById(testJobId)).thenReturn(Optional.of(testJob));
        when(deleteJobRepository.save(any(DeleteJob.class))).thenReturn(testJob);
        when(cacheManager.getCache(anyString())).thenReturn(null);
        doNothing().when(userRepository).deleteById(testUserId);

        // Act
        CompletableFuture<Void> future = userDeletionService.deleteUserAsync(testJobId);
        future.get(); // Wait for async operation

        // Assert
        verify(userRepository).deleteById(testUserId);
        verify(cacheManager, atLeastOnce()).getCache(anyString());
        // Should not throw NullPointerException
    }

    // ==================== deleteUserSync Tests ====================

    @Test
    void deleteUserSync_ShouldDeleteSuccessfully_WhenUserExists() {
        // Arrange
        when(userRepository.existsById(testUserId)).thenReturn(true);
        when(cacheManager.getCache("users")).thenReturn(usersCache);
        when(cacheManager.getCache("posts")).thenReturn(postsCache);
        when(cacheManager.getCache("favorites")).thenReturn(favoritesCache);
        when(cacheManager.getCache("resources")).thenReturn(resourcesCache);
        doNothing().when(userRepository).deleteById(testUserId);

        // Act
        userDeletionService.deleteUserSync(testUserId);

        // Assert
        verify(userRepository).existsById(testUserId);
        verify(userRepository).deleteById(testUserId);

        // Verify cache eviction
        verify(usersCache).evict(testUserId);
        verify(postsCache).clear();
        verify(favoritesCache).clear();
        verify(resourcesCache).clear();
    }

    @Test
    void deleteUserSync_ShouldThrowException_WhenUserNotFound() {
        // Arrange
        when(userRepository.existsById(testUserId)).thenReturn(false);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> userDeletionService.deleteUserSync(testUserId)
        );

        assertEquals("User not found with id: " + testUserId, exception.getMessage());
        verify(userRepository).existsById(testUserId);
        verify(userRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteUserSync_ShouldHandleNullCaches_Gracefully() {
        // Arrange
        when(userRepository.existsById(testUserId)).thenReturn(true);
        when(cacheManager.getCache(anyString())).thenReturn(null);
        doNothing().when(userRepository).deleteById(testUserId);

        // Act
        userDeletionService.deleteUserSync(testUserId);

        // Assert
        verify(userRepository).deleteById(testUserId);
        verify(cacheManager, atLeastOnce()).getCache(anyString());
        // Should not throw NullPointerException
    }

    // ==================== getJobStatus Tests ====================

    @Test
    void getJobStatus_ShouldReturnJob_WhenJobExists() {
        // Arrange
        when(deleteJobRepository.findById(testJobId)).thenReturn(Optional.of(testJob));

        // Act
        DeleteJob result = userDeletionService.getJobStatus(testJobId);

        // Assert
        assertNotNull(result);
        assertEquals(testJobId, result.getId());
        assertEquals(testUserId, result.getUserId());
        assertEquals(DeleteJobStatus.PENDING, result.getStatus());
        verify(deleteJobRepository).findById(testJobId);
    }

    @Test
    void getJobStatus_ShouldThrowException_WhenJobNotFound() {
        // Arrange
        when(deleteJobRepository.findById(testJobId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> userDeletionService.getJobStatus(testJobId)
        );

        assertEquals("Job not found: " + testJobId, exception.getMessage());
        verify(deleteJobRepository).findById(testJobId);
    }

    @Test
    void getJobStatus_ShouldReturnJobWithAllStatuses() {
        // Test all possible job statuses
        DeleteJobStatus[] statuses = {
            DeleteJobStatus.PENDING,
            DeleteJobStatus.IN_PROGRESS,
            DeleteJobStatus.COMPLETED,
            DeleteJobStatus.FAILED
        };

        for (DeleteJobStatus status : statuses) {
            // Arrange
            testJob.setStatus(status);
            when(deleteJobRepository.findById(testJobId)).thenReturn(Optional.of(testJob));

            // Act
            DeleteJob result = userDeletionService.getJobStatus(testJobId);

            // Assert
            assertEquals(status, result.getStatus());
        }

        verify(deleteJobRepository, times(statuses.length)).findById(testJobId);
    }
}
