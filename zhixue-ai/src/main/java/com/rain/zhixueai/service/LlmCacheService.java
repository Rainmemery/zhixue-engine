package com.rain.zhixueai.service;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    
    private static final String CACHE_PREFIX = "llm:cache:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    public Optional<String> get(String prompt, String modelId) {
        String cacheKey = buildCacheKey(prompt, modelId);
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("[LlmCache] Cache hit: key={}", cacheKey);
                return Optional.of(cached);
            }
            log.debug("[LlmCache] Cache miss: key={}", cacheKey);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[LlmCache] Failed to get cache: key={}, error={}", cacheKey, e.getMessage());
            return Optional.empty();
        }
    }

    public void put(String prompt, String modelId, String response) {
        if (response == null || response.isEmpty()) {
            return;
        }
        String cacheKey = buildCacheKey(prompt, modelId);
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, response, DEFAULT_TTL);
            log.debug("[LlmCache] Cached response: key={}, ttl={}h", cacheKey, DEFAULT_TTL.toHours());
        } catch (Exception e) {
            log.warn("[LlmCache] Failed to cache response: key={}, error={}", cacheKey, e.getMessage());
        }
    }

    public void putWithTtl(String prompt, String modelId, String response, Duration ttl) {
        if (response == null || response.isEmpty()) {
            return;
        }
        String cacheKey = buildCacheKey(prompt, modelId);
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, response, ttl);
            log.debug("[LlmCache] Cached response: key={}, ttl={}s", cacheKey, ttl.toSeconds());
        } catch (Exception e) {
            log.warn("[LlmCache] Failed to cache response: key={}, error={}", cacheKey, e.getMessage());
        }
    }

    public void invalidate(String prompt, String modelId) {
        String cacheKey = buildCacheKey(prompt, modelId);
        try {
            stringRedisTemplate.delete(cacheKey);
            log.debug("[LlmCache] Invalidated cache: key={}", cacheKey);
        } catch (Exception e) {
            log.warn("[LlmCache] Failed to invalidate cache: key={}, error={}", cacheKey, e.getMessage());
        }
    }

    private String buildCacheKey(String prompt, String modelId) {
        String rawKey = modelId + ":" + prompt;
        String hash = md5Hash(rawKey);
        return CACHE_PREFIX + hash;
    }

    private String md5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
