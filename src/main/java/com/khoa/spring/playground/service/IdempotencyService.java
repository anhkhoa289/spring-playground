package com.khoa.spring.playground.service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.khoa.spring.playground.dto.IdempotencyRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Service to handle idempotency operations using Hazelcast distributed cache
 */
@Service
@Slf4j
public class IdempotencyService {

    private static final String IDEMPOTENCY_CACHE_NAME = "idempotency-cache";

    private final HazelcastInstance hazelcastInstance;
    private final IMap<String, IdempotencyRequest> idempotencyCache;

    @Autowired
    public IdempotencyService(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
        this.idempotencyCache = hazelcastInstance.getMap(IDEMPOTENCY_CACHE_NAME);
    }

    /**
     * Check if a request with this idempotency key has been processed before
     *
     * @param idempotencyKey Unique key from X-Idempotency-Key header
     * @return Optional containing the previous response if exists
     */
    public Optional<IdempotencyRequest> get(String idempotencyKey) {
        IdempotencyRequest cachedRequest = idempotencyCache.get(idempotencyKey);
        if (cachedRequest != null) {
            log.info("Found cached idempotency request for key: {}", idempotencyKey);
            return Optional.of(cachedRequest);
        }
        return Optional.empty();
    }

    /**
     * Store the processed request in cache
     *
     * @param idempotencyKey Unique key from X-Idempotency-Key header
     * @param response Response object to cache
     * @param statusCode HTTP status code
     * @param requestHash Hash of request body for validation
     * @param ttl Time to live in seconds
     */
    public void store(String idempotencyKey, Object response, int statusCode, String requestHash, long ttl) {
        IdempotencyRequest idempotencyRequest = new IdempotencyRequest(
            idempotencyKey,
            response,
            statusCode,
            Instant.now(),
            requestHash
        );

        idempotencyCache.put(idempotencyKey, idempotencyRequest, ttl, TimeUnit.SECONDS);
        log.info("Stored idempotency request for key: {} with TTL: {} seconds", idempotencyKey, ttl);
    }

    /**
     * Remove idempotency key from cache
     *
     * @param idempotencyKey Key to remove
     */
    public void remove(String idempotencyKey) {
        idempotencyCache.remove(idempotencyKey);
        log.info("Removed idempotency request for key: {}", idempotencyKey);
    }

    /**
     * Generate hash from request body for validation
     *
     * @param requestBody Request body object
     * @return Base64 encoded SHA-256 hash
     */
    public String generateRequestHash(Object requestBody) {
        if (requestBody == null) {
            return "";
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String requestString = requestBody.toString();
            byte[] hash = digest.digest(requestString.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate request hash", e);
            return "";
        }
    }

    /**
     * Validate if the request hash matches the cached one
     *
     * @param cachedRequest Cached idempotency request
     * @param currentRequestHash Current request hash
     * @return true if hashes match or validation is disabled
     */
    public boolean validateRequestHash(IdempotencyRequest cachedRequest, String currentRequestHash) {
        if (cachedRequest.getRequestHash() == null || cachedRequest.getRequestHash().isEmpty()) {
            return true; // No hash stored, skip validation
        }

        boolean isValid = cachedRequest.getRequestHash().equals(currentRequestHash);
        if (!isValid) {
            log.warn("Request hash mismatch for idempotency key: {}. " +
                    "Same key with different request body detected.",
                    cachedRequest.getIdempotencyKey());
        }
        return isValid;
    }

    /**
     * Get cache size
     */
    public int getCacheSize() {
        return idempotencyCache.size();
    }

    /**
     * Clear all entries in idempotency cache (use with caution)
     */
    public void clearCache() {
        idempotencyCache.clear();
        log.warn("Cleared all idempotency cache entries");
    }
}
