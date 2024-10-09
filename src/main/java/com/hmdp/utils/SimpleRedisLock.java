package com.hmdp.utils;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;


public class SimpleRedisLock implements Lock {

    private static final String LOCK_PREFIX = "lock:";
    private static final DefaultRedisScript<Long> DELETE_SCRIPT;
    static {
        DELETE_SCRIPT = new DefaultRedisScript<>();
        DELETE_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        DELETE_SCRIPT.setResultType(Long.class);
    }

    private final String name;
    private final String LOCK_VALUE_PREFIX = UUID.randomUUID().toString();
    private final StringRedisTemplate redisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.name = name;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSeconds) {
        String value = LOCK_VALUE_PREFIX + Thread.currentThread().getId();
        Boolean lock = redisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + name, value, timeoutSeconds, TimeUnit.SECONDS);
        return lock != null && lock;
    }

    @Override
    public void unlock() {
        String value = LOCK_VALUE_PREFIX + Thread.currentThread().getId();
        redisTemplate.execute(DELETE_SCRIPT, Collections.singletonList(LOCK_PREFIX + name), value);
    }
}
