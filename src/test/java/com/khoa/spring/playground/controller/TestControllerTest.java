package com.khoa.spring.playground.controller;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.khoa.spring.playground.dto.IdempotencyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

@SpringBootTest(properties = {"spring.cache.type=hazelcast"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
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

	@Test
	void getRandomNumber_ShouldReturnRandomNumber() throws Exception {
		// Act & Assert
		mockMvc.perform(get("/api/test/random").param("requestId", "test-123"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.requestId").value("test-123"))
			.andExpect(jsonPath("$.randomNumber").isNumber())
			.andExpect(header().doesNotExist("X-Idempotent-Replayed"));
	}

	@Test
	void getRandomNumber_ShouldReturnCachedResponse_WhenCalledWithSameRequestId() throws Exception {
		// Arrange - First call
		MvcResult firstResult = mockMvc.perform(get("/api/test/random").param("requestId", "test-456"))
			.andExpect(status().isOk())
			.andReturn();

		String firstResponse = firstResult.getResponse().getContentAsString();

		// Act - Second call with same requestId
		MvcResult secondResult = mockMvc.perform(get("/api/test/random").param("requestId", "test-456"))
			.andExpect(status().isOk())
			.andExpect(header().string("X-Idempotent-Replayed", "true"))
			.andReturn();

		String secondResponse = secondResult.getResponse().getContentAsString();

		// Assert - Both responses should be identical
		assertEquals(firstResponse, secondResponse);
	}

	@Test
	void getRandomNumber_ShouldReturnDifferentRandomNumber_WhenCalledWithDifferentRequestId() throws Exception {
		// Arrange - First call
		MvcResult firstResult = mockMvc.perform(get("/api/test/random").param("requestId", "test-789"))
			.andExpect(status().isOk())
			.andReturn();

		// Act - Second call with different requestId
		MvcResult secondResult = mockMvc.perform(get("/api/test/random").param("requestId", "test-790"))
			.andExpect(status().isOk())
			.andExpect(header().doesNotExist("X-Idempotent-Replayed"))
			.andReturn();

		// Assert - Responses should be different (different requestIds)
		assertNotEquals(firstResult.getResponse().getContentAsString(),
				secondResult.getResponse().getContentAsString());
	}

	@Test
	void getRandomNumber_ShouldStoreResponseInCache() throws Exception {
		// Arrange
		String requestId = "test-cache-123";

		// Act
		mockMvc.perform(get("/api/test/random").param("requestId", requestId)).andExpect(status().isOk());

		// Assert
		IdempotencyResponse cachedResponse = (IdempotencyResponse) cache.get(requestId);
		assertNotNull(cachedResponse);
		assertEquals(200, cachedResponse.getStatusCode());
		assertNotNull(cachedResponse.getResponseBody());
		assertNotNull(cachedResponse.getTimestamp());
	}

	@Test
	void getRandomNumberWithCustomKey_ShouldReturnRandomNumber() throws Exception {
		// Act & Assert
		mockMvc.perform(get("/api/test/random/user123").param("action", "generate"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId").value("user123"))
			.andExpect(jsonPath("$.action").value("generate"))
			.andExpect(jsonPath("$.randomNumber").isNumber())
			.andExpect(header().doesNotExist("X-Idempotent-Replayed"));
	}

	@Test
	void getRandomNumberWithCustomKey_ShouldReturnCachedResponse_WhenCalledWithSameCompositeKey() throws Exception {
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
	void getRandomNumberWithCustomKey_ShouldUseDefaultAction_WhenActionNotProvided() throws Exception {
		// Act & Assert
		mockMvc.perform(get("/api/test/random/user111"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId").value("user111"))
			.andExpect(jsonPath("$.action").value("generate"))
			.andExpect(jsonPath("$.randomNumber").isNumber());
	}

	@Test
	void getRandomNumberWithCustomKey_ShouldStoreCompositeKeyInCache() throws Exception {
		// Arrange
		String userId = "user-cache-test";
		String action = "process";
		String expectedKey = userId + "-" + action;

		// Act
		mockMvc.perform(get("/api/test/random/" + userId).param("action", action)).andExpect(status().isOk());

		// Assert
		IdempotencyResponse cachedResponse = (IdempotencyResponse) cache.get(expectedKey);
		assertNotNull(cachedResponse);
		assertEquals(200, cachedResponse.getStatusCode());
		assertNotNull(cachedResponse.getResponseBody());
	}

	@Test
	void testEndpoints_ShouldHaveIndependentCaches() throws Exception {
		// Arrange - Call both endpoints with values that could collide if not handled properly
		String requestId = "test-collision";
		String userId = "test";
		String action = "collision";

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

}
