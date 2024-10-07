package com.hmdp.utils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import static com.hmdp.utils.RedisConstants.GLOBAL_ID_INCREMENT_KEY;

@Component
public class GlobalIDGenerator {

    private static final int SERIAL_BITS = 32;
    private static final long BEGIN_TIMESTAMP = LocalDateTime
            .of(2024, 1, 1, 0, 0, 0)
            .toEpochSecond(ZoneOffset.UTC);

    private final StringRedisTemplate redisTemplate;

    public GlobalIDGenerator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public long nextId(String prefix) {
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        String yyyyMMdd = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long count = redisTemplate.opsForValue().increment(GLOBAL_ID_INCREMENT_KEY + prefix + ":" + yyyyMMdd);
        if (count == null) {
            // this won't happen
            count = 0L;
        }
        return (timestamp << SERIAL_BITS) | count;
    }
}
