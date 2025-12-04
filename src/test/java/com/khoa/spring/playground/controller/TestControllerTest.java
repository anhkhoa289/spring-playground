package com.khoa.spring.playground.controller;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.khoa.spring.playground.dto.IdempotencyResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for TestController.
 * Tests idempotency behavior using Hazelcast distributed cache.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private HazelcastInstance hazelcastInstance;

	private IMap<Object, Object> cache;

	@BeforeEach
	void setUp() {
		cache = hazelcastInstance.getMap("idempotency");
		cache.clear();
	}

	@Nested
	@DisplayName("GET /api/test/random - Basic Idempotency Tests")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	class BasicIdempotencyTests {

		@Test
		@Order(1)
		void getRandomNumber_ShouldReturnRandomNumber_WhenRequestIdProvided() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/test/random").param("requestId", "test-123"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.requestId").value("test-123"))
				.andExpect(jsonPath("$.randomNumber").isNumber())
				.andExpect(jsonPath("$.randomNumber").value(both(greaterThanOrEqualTo(0)).and(lessThan(1000))))
				.andExpect(header().doesNotExist("X-Idempotent-Replayed"));
		}

		@Test
		@Order(2)
		void getRandomNumber_ShouldReturnCachedResponse_WhenCalledWithSameRequestId() throws Exception {
			// Arrange - First call
			MvcResult firstResult = mockMvc.perform(get("/api/test/random").param("requestId", "cache-test-456"))
				.andExpect(status().isOk())
				.andReturn();

			String firstResponse = firstResult.getResponse().getContentAsString();

			// Act - Second call with same requestId
			MvcResult secondResult = mockMvc.perform(get("/api/test/random").param("requestId", "cache-test-456"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Idempotent-Replayed", "true"))
				.andReturn();

			String secondResponse = secondResult.getResponse().getContentAsString();

			// Assert - Both responses should be identical
			assertEquals(firstResponse, secondResponse);
		}

		@Test
		@Order(3)
		void getRandomNumber_ShouldReturnDifferentRandomNumber_WhenCalledWithDifferentRequestId() throws Exception {
			// Arrange - First call
			MvcResult firstResult = mockMvc.perform(get("/api/test/random").param("requestId", "unique-789"))
				.andExpect(status().isOk())
				.andReturn();

			// Act - Second call with different requestId
			MvcResult secondResult = mockMvc.perform(get("/api/test/random").param("requestId", "unique-790"))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist("X-Idempotent-Replayed"))
				.andReturn();

			// Assert - Responses should be different (different requestIds)
			assertNotEquals(firstResult.getResponse().getContentAsString(),
					secondResult.getResponse().getContentAsString());
		}

		@Test
		@Order(4)
		void getRandomNumber_ShouldStoreResponseInCache() throws Exception {
			// Arrange
			String requestId = "store-cache-123";

			// Act
			mockMvc.perform(get("/api/test/random").param("requestId", requestId)).andExpect(status().isOk());

			// Assert
			Object cachedResponse = cache.get(requestId);
			assertNotNull(cachedResponse, "Response should be stored in cache");
			assertTrue(cachedResponse instanceof IdempotencyResponse);

			IdempotencyResponse response = (IdempotencyResponse) cachedResponse;
			assertEquals(200, response.getStatusCode());
			assertNotNull(response.getResponseBody());
			assertNotNull(response.getTimestamp());
		}

		@Test
		@Order(5)
		void getRandomNumber_ShouldHandleMissingRequestId() throws Exception {
			// Act & Assert - When requestId is not provided, should proceed without caching
			mockMvc.perform(get("/api/test/random"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.requestId").doesNotExist())
				.andExpect(jsonPath("$.randomNumber").isNumber());
		}

	}

	@Nested
	@DisplayName("GET /api/test/random/{userId} - Composite Key Tests")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	class CompositeKeyTests {

		@Test
		@Order(1)
		void getRandomNumberWithCustomKey_ShouldReturnRandomNumber() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/test/random/user123").param("action", "create"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value("user123"))
				.andExpect(jsonPath("$.action").value("create"))
				.andExpect(jsonPath("$.randomNumber").isNumber())
				.andExpect(header().doesNotExist("X-Idempotent-Replayed"));
		}

		@Test
		@Order(2)
		void getRandomNumberWithCustomKey_ShouldReturnCachedResponse_WhenCalledWithSameCompositeKey()
				throws Exception {
			// Arrange - First call
			MvcResult firstResult = mockMvc
				.perform(get("/api/test/random/user456").param("action", "process"))
				.andExpect(status().isOk())
				.andReturn();

			String firstResponse = firstResult.getResponse().getContentAsString();

			// Act - Second call with same userId and action
			MvcResult secondResult = mockMvc
				.perform(get("/api/test/random/user456").param("action", "process"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Idempotent-Replayed", "true"))
				.andReturn();

			String secondResponse = secondResult.getResponse().getContentAsString();

			// Assert - Both responses should be identical
			assertEquals(firstResponse, secondResponse);
		}

		@Test
		@Order(3)
		void getRandomNumberWithCustomKey_ShouldReturnDifferentResponse_WhenUserIdDiffers() throws Exception {
			// Arrange - First call
			MvcResult firstResult = mockMvc
				.perform(get("/api/test/random/user789").param("action", "generate"))
				.andExpect(status().isOk())
				.andReturn();

			// Act - Second call with different userId but same action
			MvcResult secondResult = mockMvc
				.perform(get("/api/test/random/user790").param("action", "generate"))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist("X-Idempotent-Replayed"))
				.andReturn();

			// Assert - Responses should be different
			assertNotEquals(firstResult.getResponse().getContentAsString(),
					secondResult.getResponse().getContentAsString());
		}

		@Test
		@Order(4)
		void getRandomNumberWithCustomKey_ShouldReturnDifferentResponse_WhenActionDiffers() throws Exception {
			// Arrange - First call
			MvcResult firstResult = mockMvc
				.perform(get("/api/test/random/user999").param("action", "create"))
				.andExpect(status().isOk())
				.andReturn();

			// Act - Second call with same userId but different action
			MvcResult secondResult = mockMvc
				.perform(get("/api/test/random/user999").param("action", "update"))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist("X-Idempotent-Replayed"))
				.andReturn();

			// Assert - Responses should be different
			assertNotEquals(firstResult.getResponse().getContentAsString(),
					secondResult.getResponse().getContentAsString());
		}

		@Test
		@Order(5)
		void getRandomNumberWithCustomKey_ShouldUseDefaultAction_WhenActionNotProvided() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/test/random/user111"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value("user111"))
				.andExpect(jsonPath("$.action").value("generate"))
				.andExpect(jsonPath("$.randomNumber").isNumber());
		}

		@Test
		@Order(6)
		void getRandomNumberWithCustomKey_ShouldStoreCompositeKeyInCache() throws Exception {
			// Arrange
			String userId = "cache-user-test";
			String action = "process";
			String expectedKey = userId + "-" + action;

			// Act
			mockMvc.perform(get("/api/test/random/" + userId).param("action", action)).andExpect(status().isOk());

			// Assert
			Object cachedResponse = cache.get(expectedKey);
			assertNotNull(cachedResponse, "Response with composite key should be stored in cache");
			assertTrue(cachedResponse instanceof IdempotencyResponse);

			IdempotencyResponse response = (IdempotencyResponse) cachedResponse;
			assertEquals(200, response.getStatusCode());
			assertNotNull(response.getResponseBody());
		}

	}

	@Nested
	@DisplayName("Cache Isolation Tests")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	class CacheIsolationTests {

		@Test
		@Order(1)
		void testEndpoints_ShouldHaveIndependentCaches() throws Exception {
			// Arrange - Call both endpoints with values that could collide if not handled properly
			String requestId = "collision-test";
			String userId = "collision";
			String action = "test";

			// Act
			MvcResult result1 = mockMvc.perform(get("/api/test/random").param("requestId", requestId))
				.andExpect(status().isOk())
				.andReturn();

			MvcResult result2 = mockMvc.perform(get("/api/test/random/" + userId).param("action", action))
				.andExpect(status().isOk())
				.andReturn();

			// Assert - Results should be different (different endpoints, different cache keys)
			assertNotEquals(result1.getResponse().getContentAsString(), result2.getResponse().getContentAsString());

			// Verify both are cached independently
			assertNotNull(cache.get(requestId));
			assertNotNull(cache.get(userId + "-" + action));
		}

		@Test
		@Order(2)
		void multipleConcurrentRequests_ShouldBeHandledCorrectly() throws Exception {
			// Arrange
			String requestId = "concurrent-test";

			// Act - Make multiple concurrent requests
			MvcResult result1 = mockMvc.perform(get("/api/test/random").param("requestId", requestId))
				.andExpect(status().isOk())
				.andReturn();

			MvcResult result2 = mockMvc.perform(get("/api/test/random").param("requestId", requestId))
				.andExpect(status().isOk())
				.andReturn();

			MvcResult result3 = mockMvc.perform(get("/api/test/random").param("requestId", requestId))
				.andExpect(status().isOk())
				.andReturn();

			// Assert - All results should be identical (cached)
			assertEquals(result1.getResponse().getContentAsString(), result2.getResponse().getContentAsString());
			assertEquals(result2.getResponse().getContentAsString(), result3.getResponse().getContentAsString());
		}

	}

	@Nested
	@DisplayName("Cache Behavior Tests")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	class CacheBehaviorTests {

		@Test
		@Order(1)
		void cache_ShouldBeSharedAcrossMultipleRequests() throws Exception {
			// Arrange
			String requestId = "shared-cache-test";

			// Act - First request
			mockMvc.perform(get("/api/test/random").param("requestId", requestId)).andExpect(status().isOk());

			// Verify cache entry exists
			Object cacheEntry = cache.get(requestId);
			assertNotNull(cacheEntry);

			// Act - Second request should use cached entry
			mockMvc.perform(get("/api/test/random").param("requestId", requestId))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Idempotent-Replayed", "true"));
		}

		@Test
		@Order(2)
		void cache_ShouldClearSuccessfully() {
			// Arrange
			cache.put("test-key", new IdempotencyResponse());

			// Act
			cache.clear();

			// Assert
			assertTrue(cache.isEmpty());
		}

		@Test
		@Order(3)
		void cache_ShouldSupportMultipleEntries() throws Exception {
			// Act - Create multiple cache entries
			mockMvc.perform(get("/api/test/random").param("requestId", "key1"));
			mockMvc.perform(get("/api/test/random").param("requestId", "key2"));
			mockMvc.perform(get("/api/test/random").param("requestId", "key3"));

			// Assert
			assertEquals(3, cache.size());
			assertNotNull(cache.get("key1"));
			assertNotNull(cache.get("key2"));
			assertNotNull(cache.get("key3"));
		}

	}

}
