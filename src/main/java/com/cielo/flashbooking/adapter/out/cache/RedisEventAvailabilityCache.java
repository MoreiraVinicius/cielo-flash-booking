package com.cielo.flashbooking.adapter.out.cache;

import com.cielo.flashbooking.domain.event.Event;
import com.cielo.flashbooking.event.application.EventAvailabilityCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RedisEventAvailabilityCache implements EventAvailabilityCache {

    static final Duration TTL = Duration.ofSeconds(1);
    private static final String KEY_PREFIX = "event-availability:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    RedisEventAvailabilityCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<Event> findById(UUID id) {
        String value = redisTemplate.opsForValue().get(key(id));
        if (value == null) {
            return Optional.empty();
        }
        try {
            CacheEntry entry = objectMapper.readValue(value, CacheEntry.class);
            return Optional.of(Event.restore(
                    entry.id(), entry.name(), entry.capacity(), entry.available(), entry.createdAt(), entry.startsAt(), entry.endsAt()));
        } catch (JsonProcessingException invalidCacheEntry) {
            throw new IllegalStateException("invalid event cache entry", invalidCacheEntry);
        }
    }

    @Override
    public void put(Event event) {
        CacheEntry entry = new CacheEntry(
                event.id(), event.name(), event.capacity(), event.available(), event.createdAt(), event.startsAt(), event.endsAt());
        try {
            redisTemplate.opsForValue().set(
                    key(event.id()), objectMapper.writeValueAsString(entry), TTL);
        } catch (JsonProcessingException serializationFailure) {
            throw new IllegalStateException("could not serialize event cache entry", serializationFailure);
        }
    }

    @Override
    public void evict(UUID id) {
        redisTemplate.delete(key(id));
    }

    private String key(UUID id) {
        return KEY_PREFIX + id;
    }

    private record CacheEntry(
            UUID id, String name, int capacity, int available, Instant createdAt, Instant startsAt, Instant endsAt) {
    }
}
