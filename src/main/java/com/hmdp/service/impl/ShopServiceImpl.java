package com.hmdp.service.impl;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheResult;
import com.hmdp.utils.RedisData;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;

/**
 * @author Ernie Lee
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Value("${cache.shop.ttl.seconds:120}")
    int cacheShopTtlSeconds;

    @Value("${cache.shop.null.ttl.seconds:30}")
    int cacheShopNullTtlSeconds;

    @Value("${cache.shop.rebuild.strategy:logic-expiration}")
    String cacheShopRebuildStrategy;

    ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public Result query(Long id) throws JsonProcessingException {
        Shop shop = null;
        switch (cacheShopRebuildStrategy) {
            case "simple":
                shop = queryWithSimpleRebuildStrategy(id);
                break;
            case "mutex":
                shop = queryWithMutexRebuildStrategy(id);
                break;
            case "logic-expiration":
                shop = queryWithLogicExpirationRebuildStrategy(id);
                break;
        }

        if (shop == null) {
            return Result.fail("Shop not found");
        }
        return Result.ok(shop);
    }

    private Shop queryWithSimpleRebuildStrategy(Long id) throws JsonProcessingException {
        CacheResult<Shop> result = queryFromCache(id);
        if (result.isHit()) {
            return result.getData();
        }
        return rebuildCache(id);
    }

    /**
     * Busy waiting for high consistency.
     * */
    private Shop queryWithMutexRebuildStrategy(Long id) throws JsonProcessingException {
        final String lockKey = LOCK_SHOP_KEY + id;

        CacheResult<Shop> result = queryFromCache(id);
        if (result.isHit()) {
            return result.getData();
        }

        try {
            boolean isLocked = tryLock(lockKey);
            if (!isLocked) {
                Thread.sleep(30);
                return queryWithMutexRebuildStrategy(id);
            }

            // double check to prevent race condition
            result = queryFromCache(id);
            if (result.isHit()) {
                return result.getData();
            }

            return rebuildCache(id);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }
    }

    private Shop queryWithLogicExpirationRebuildStrategy(Long id) throws JsonProcessingException {
        final String lockKey = LOCK_SHOP_KEY + id;

        CacheResult<RedisData<Shop>> result = queryFromCacheWithLogicalExpiration(id);
        if (result.isHit()) {
            if (result.getData().getExpireTime().isAfter(LocalDateTime.now())) {
                return result.getData().getData();
            }

            // async rebuild
            boolean isLocked = tryLock(lockKey);
            if (isLocked) {
                executorService.submit(() -> {
                    try {
                        // double check to prevent race condition
                        CacheResult<RedisData<Shop>> r = queryFromCacheWithLogicalExpiration(id);
                        if (r.isHit() && r.getData().getExpireTime().isAfter(LocalDateTime.now())) {
                            return;
                        }
                        rebuildCacheWithLogicalExpiration(id);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    } finally {
                        unlock(lockKey);
                    }
                });
            }
            return result.getData().getData();
        }

        try {
            boolean isLocked = tryLock(lockKey);
            if (!isLocked) {
                Thread.sleep(30);
                return queryWithLogicExpirationRebuildStrategy(id);
            }
            log.info("Initializing shop cache: {}", id);

            // double check to prevent race condition
            result = queryFromCacheWithLogicalExpiration(id);
            if (result.isHit()) {
                return result.getData().getData();
            }

            return rebuildCacheWithLogicalExpiration(id);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }
    }

    private CacheResult<Shop> queryFromCache(Long id) {
        String result = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (StringUtils.isNotBlank(result)) {
            try {
                return CacheResult.hit(objectMapper.readValue(result, Shop.class));
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize shop data for id: {}", id, e);
            }
        }
        if (result != null) {
            return CacheResult.hit(null);
        }
        return CacheResult.miss();
    }

    private Shop rebuildCache(Long id) throws JsonProcessingException {
        log.info("Rebuilding cache for shop id: {}", id);
        Shop shop = getById(id);

        // simulate rebuilding
        try {
            log.info("Sleeping for 200ms to simulate rebuilding cache");
            Thread.sleep(200);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (shop == null) {
            redisTemplate.opsForValue().set(
                    CACHE_SHOP_KEY + id,
                    "",
                    cacheShopNullTtlSeconds, TimeUnit.SECONDS);
        } else {
            redisTemplate.opsForValue().set(
                    CACHE_SHOP_KEY + id,
                    objectMapper.writeValueAsString(shop),
                    cacheShopTtlSeconds, TimeUnit.SECONDS);
        }
        log.info("Cache rebuilt for shop id: {}", id);
        return shop;
    }

    private CacheResult<RedisData<Shop>> queryFromCacheWithLogicalExpiration(Long id) {
        String result = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (StringUtils.isNotBlank(result)) {
            try {
                return CacheResult.hit(objectMapper.readValue(result, new TypeReference<RedisData<Shop>>() {}));
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize shop data for id: {}", id, e);
            }
        }
        if (result != null) {
            return CacheResult.hit(null);
        }
        return CacheResult.miss();
    }

    private Shop rebuildCacheWithLogicalExpiration(Long id) throws JsonProcessingException {
        log.info("Rebuilding cache for shop id: {}", id);
        Shop shop = getById(id);

        // simulate rebuilding
        try {
            log.info("Sleeping for 200ms to simulate rebuilding cache");
            Thread.sleep(200);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (shop == null) {
            redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "");
        } else {
            RedisData<Shop> data = new RedisData<>();
            data.setData(shop);
            data.setExpireTime(LocalDateTime.now().plusSeconds(cacheShopTtlSeconds));
            redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, objectMapper.writeValueAsString(data));
        }
        log.info("Cache rebuilt for shop id: {}", id);
        return shop;
    }

    private boolean tryLock(String key) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return result != null && result;
    }

    private void unlock(String key) {
        redisTemplate.delete(key);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("Invalid shop id");
        }
        updateById(shop);
        redisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
