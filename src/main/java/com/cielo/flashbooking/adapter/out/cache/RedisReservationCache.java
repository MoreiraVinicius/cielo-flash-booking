package com.cielo.flashbooking.adapter.out.cache;

import com.cielo.flashbooking.reservation.application.ReservationCache;
import com.cielo.flashbooking.reservation.application.ReservationDetails;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RedisReservationCache implements ReservationCache {

    static final Duration TTL = Duration.ofSeconds(1);
    private static final String KEY_PREFIX = "reservation:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    RedisReservationCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ReservationDetails> findById(UUID id) {
        String value = redisTemplate.opsForValue().get(key(id));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, ReservationDetails.class));
        } catch (JsonProcessingException invalidCacheEntry) {
            throw new IllegalStateException("invalid reservation cache entry", invalidCacheEntry);
        }
    }

    @Override
    public void put(ReservationDetails reservation) {
        try {
            redisTemplate.opsForValue().set(
                    key(reservation.id()), objectMapper.writeValueAsString(reservation), TTL);
        } catch (JsonProcessingException serializationFailure) {
            throw new IllegalStateException("could not serialize reservation cache entry", serializationFailure);
        }
    }

    @Override
    public void evict(UUID id) {
        redisTemplate.delete(key(id));
    }

    private String key(UUID id) {
        return KEY_PREFIX + id;
    }
}
