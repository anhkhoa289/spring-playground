package com.khoa.spring.playground.aspect;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.khoa.spring.playground.annotation.Idempotent;
import com.khoa.spring.playground.dto.IdempotencyResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyAspectTest {

	@Mock
	private HazelcastInstance hazelcastInstance;

	@Mock
	private IMap<Object, Object> cache;

	@Mock
	private ProceedingJoinPoint joinPoint;

	@Mock
	private MethodSignature signature;

	@Mock
	private Idempotent idempotentAnnotation;

	private IdempotencyAspect idempotencyAspect;

	@BeforeEach
	void setUp() {
		idempotencyAspect = new IdempotencyAspect(hazelcastInstance);
		lenient().when(hazelcastInstance.getMap("idempotency")).thenReturn(cache);
	}

	@Test
	void handleIdempotency_ShouldReturnCachedResponse_WhenCacheHit() throws Throwable {
		// Arrange
		String key = "test-key-123";
		when(idempotentAnnotation.key()).thenReturn("#requestId");
		lenient().when(idempotentAnnotation.ttl()).thenReturn(300);

		Method method = getTestMethod("testMethod", String.class);
		when(joinPoint.getSignature()).thenReturn(signature);
		when(signature.getMethod()).thenReturn(method);
		when(joinPoint.getArgs()).thenReturn(new Object[] { key });

		Map<String, Object> cachedBody = new HashMap<>();
		cachedBody.put("cached", true);
		IdempotencyResponse cachedResponse = new IdempotencyResponse(cachedBody, 200, LocalDateTime.now(), false);
		when(cache.get("test-key-123")).thenReturn(cachedResponse);

		// Act
		Object result = idempotencyAspect.handleIdempotency(joinPoint, idempotentAnnotation);

		// Assert
		assertNotNull(result);
		assertTrue(result instanceof ResponseEntity);
		ResponseEntity<?> responseEntity = (ResponseEntity<?>) result;
		assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
		assertTrue(responseEntity.getHeaders().containsKey("X-Idempotent-Replayed"));
		assertEquals("true", responseEntity.getHeaders().getFirst("X-Idempotent-Replayed"));
		assertTrue(cachedResponse.isFromCache());

		verify(joinPoint, never()).proceed();
		verify(cache, never()).put(anyString(), any(), anyLong(), any());
	}

	@Test
	void handleIdempotency_ShouldExecuteMethodAndCache_WhenCacheMiss() throws Throwable {
		// Arrange
		String key = "test-key-456";
		when(idempotentAnnotation.key()).thenReturn("#requestId");
		when(idempotentAnnotation.ttl()).thenReturn(300);

		Method method = getTestMethod("testMethod", String.class);
		when(joinPoint.getSignature()).thenReturn(signature);
		when(signature.getMethod()).thenReturn(method);
		when(joinPoint.getArgs()).thenReturn(new Object[] { key });

		when(cache.get("test-key-456")).thenReturn(null);

		Map<String, Object> responseBody = new HashMap<>();
		responseBody.put("result", "success");
		ResponseEntity<Map<String, Object>> expectedResponse = ResponseEntity.ok(responseBody);
		when(joinPoint.proceed()).thenReturn(expectedResponse);

		// Act
		Object result = idempotencyAspect.handleIdempotency(joinPoint, idempotentAnnotation);

		// Assert
		assertNotNull(result);
		assertEquals(expectedResponse, result);

		verify(joinPoint, times(1)).proceed();
		ArgumentCaptor<IdempotencyResponse> responseCaptor = ArgumentCaptor.forClass(IdempotencyResponse.class);
		verify(cache).put(eq("test-key-456"), responseCaptor.capture(), eq(300L), eq(TimeUnit.SECONDS));

		IdempotencyResponse cachedResponse = responseCaptor.getValue();
		assertEquals(200, cachedResponse.getStatusCode());
		assertEquals(responseBody, cachedResponse.getResponseBody());
		assertFalse(cachedResponse.isFromCache());
		assertNotNull(cachedResponse.getTimestamp());
	}

	@Test
	void handleIdempotency_ShouldGenerateCompositeKey_WhenUsingComplexSpEL() throws Throwable {
		// Arrange
		when(idempotentAnnotation.key()).thenReturn("#userId + '-' + #action");
		when(idempotentAnnotation.ttl()).thenReturn(300);

		Method method = getTestMethod("testMethodWithTwoParams", String.class, String.class);
		when(joinPoint.getSignature()).thenReturn(signature);
		when(signature.getMethod()).thenReturn(method);
		when(joinPoint.getArgs()).thenReturn(new Object[] { "user123", "create" });

		when(cache.get("user123-create")).thenReturn(null);

		ResponseEntity<String> expectedResponse = ResponseEntity.ok("created");
		when(joinPoint.proceed()).thenReturn(expectedResponse);

		// Act
		Object result = idempotencyAspect.handleIdempotency(joinPoint, idempotentAnnotation);

		// Assert
		assertNotNull(result);
		verify(cache).put(eq("user123-create"), any(IdempotencyResponse.class), eq(300L), eq(TimeUnit.SECONDS));
	}

	@Test
	void handleIdempotency_ShouldProceedWithoutCache_WhenKeyIsNull() throws Throwable {
		// Arrange
		when(idempotentAnnotation.key()).thenReturn("#requestId");

		Method method = getTestMethod("testMethod", String.class);
		when(joinPoint.getSignature()).thenReturn(signature);
		when(signature.getMethod()).thenReturn(method);
		when(joinPoint.getArgs()).thenReturn(new Object[] { null });

		ResponseEntity<String> expectedResponse = ResponseEntity.ok("result");
		when(joinPoint.proceed()).thenReturn(expectedResponse);

		// Act
		Object result = idempotencyAspect.handleIdempotency(joinPoint, idempotentAnnotation);

		// Assert
		assertEquals(expectedResponse, result);
		verify(cache, never()).get(anyString());
		verify(cache, never()).put(anyString(), any(), anyLong(), any());
	}

	@Test
	void handleIdempotency_ShouldProceedWithoutCache_WhenKeyIsEmpty() throws Throwable {
		// Arrange
		when(idempotentAnnotation.key()).thenReturn("#requestId");

		Method method = getTestMethod("testMethod", String.class);
		when(joinPoint.getSignature()).thenReturn(signature);
		when(signature.getMethod()).thenReturn(method);
		when(joinPoint.getArgs()).thenReturn(new Object[] { "   " });

		ResponseEntity<String> expectedResponse = ResponseEntity.ok("result");
		when(joinPoint.proceed()).thenReturn(expectedResponse);

		// Act
		Object result = idempotencyAspect.handleIdempotency(joinPoint, idempotentAnnotation);

		// Assert
		assertEquals(expectedResponse, result);
		verify(cache, never()).get(anyString());
		verify(cache, never()).put(anyString(), any(), anyLong(), any());
	}

	@Test
	void handleIdempotency_ShouldNotCache_WhenReturnTypeIsNotResponseEntity() throws Throwable {
		// Arrange
		String key = "test-key-789";
		when(idempotentAnnotation.key()).thenReturn("#requestId");
		lenient().when(idempotentAnnotation.ttl()).thenReturn(300);

		Method method = getTestMethod("testMethod", String.class);
		when(joinPoint.getSignature()).thenReturn(signature);
		when(signature.getMethod()).thenReturn(method);
		when(joinPoint.getArgs()).thenReturn(new Object[] { key });

		when(cache.get("test-key-789")).thenReturn(null);

		String plainResult = "plain string result";
		when(joinPoint.proceed()).thenReturn(plainResult);

		// Act
		Object result = idempotencyAspect.handleIdempotency(joinPoint, idempotentAnnotation);

		// Assert
		assertEquals(plainResult, result);
		verify(joinPoint, times(1)).proceed();
		verify(cache, never()).put(anyString(), any(), anyLong(), any());
	}

	@Test
	void handleIdempotency_ShouldUseTTLFromAnnotation() throws Throwable {
		// Arrange
		String key = "test-ttl-key";
		when(idempotentAnnotation.key()).thenReturn("#requestId");
		when(idempotentAnnotation.ttl()).thenReturn(600); // Custom TTL

		Method method = getTestMethod("testMethod", String.class);
		when(joinPoint.getSignature()).thenReturn(signature);
		when(signature.getMethod()).thenReturn(method);
		when(joinPoint.getArgs()).thenReturn(new Object[] { key });

		when(cache.get("test-ttl-key")).thenReturn(null);

		ResponseEntity<String> expectedResponse = ResponseEntity.ok("result");
		when(joinPoint.proceed()).thenReturn(expectedResponse);

		// Act
		idempotencyAspect.handleIdempotency(joinPoint, idempotentAnnotation);

		// Assert
		verify(cache).put(eq("test-ttl-key"), any(IdempotencyResponse.class), eq(600L), eq(TimeUnit.SECONDS));
	}

	@Test
	void handleIdempotency_ShouldPreserveHttpStatusCode() throws Throwable {
		// Arrange
		String key = "test-status-key";
		when(idempotentAnnotation.key()).thenReturn("#requestId");
		when(idempotentAnnotation.ttl()).thenReturn(300);

		Method method = getTestMethod("testMethod", String.class);
		when(joinPoint.getSignature()).thenReturn(signature);
		when(signature.getMethod()).thenReturn(method);
		when(joinPoint.getArgs()).thenReturn(new Object[] { key });

		when(cache.get("test-status-key")).thenReturn(null);

		ResponseEntity<String> expectedResponse = ResponseEntity.status(HttpStatus.CREATED).body("created");
		when(joinPoint.proceed()).thenReturn(expectedResponse);

		// Act
		idempotencyAspect.handleIdempotency(joinPoint, idempotentAnnotation);

		// Assert
		ArgumentCaptor<IdempotencyResponse> responseCaptor = ArgumentCaptor.forClass(IdempotencyResponse.class);
		verify(cache).put(eq("test-status-key"), responseCaptor.capture(), eq(300L), eq(TimeUnit.SECONDS));

		IdempotencyResponse cachedResponse = responseCaptor.getValue();
		assertEquals(201, cachedResponse.getStatusCode());
	}

	// Helper method to get test methods
	private Method getTestMethod(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
		return TestClass.class.getMethod(methodName, parameterTypes);
	}

	// Test class for reflection
	public static class TestClass {

		public ResponseEntity<String> testMethod(String requestId) {
			return ResponseEntity.ok("test");
		}

		public ResponseEntity<String> testMethodWithTwoParams(String userId, String action) {
			return ResponseEntity.ok("test");
		}

	}

}
