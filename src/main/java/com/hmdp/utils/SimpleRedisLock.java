package com.hmdp.utils;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;

public class SimpleRedisLock implements Lock {

    private static final String LOCK_PREFIX = "lock:";

    private final String name;
    private final StringRedisTemplate redisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.name = name;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSeconds) {
        Boolean lock = redisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + name, "1", timeoutSeconds, TimeUnit.SECONDS);
        return lock != null && lock;
    }

    @Override
    public void unlock() {
        redisTemplate.delete(LOCK_PREFIX + name);
    }
}
