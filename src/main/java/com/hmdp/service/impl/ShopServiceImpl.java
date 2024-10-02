package com.hmdp.service.impl;

import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;


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

    @Override
    public Result query(Long id) throws JsonProcessingException {
        String result = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (StringUtils.isNotBlank(result)) {
            try {
                Shop shop = objectMapper.readValue(result, Shop.class);
                return Result.ok(shop);
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize shop data for id: {}", id, e);
            }
        }
        if (result != null) {
            return Result.fail("Shop not found");
        }

        Shop shop = getById(id);
        if (shop == null) {
            redisTemplate.opsForValue().set(
                    CACHE_SHOP_KEY + id,
                    "",
                    cacheShopNullTtlSeconds, TimeUnit.SECONDS);
            return Result.fail("Shop not found");
        }
        redisTemplate.opsForValue().set(
                CACHE_SHOP_KEY + id,
                objectMapper.writeValueAsString(shop),
                cacheShopTtlSeconds, TimeUnit.SECONDS);
        return Result.ok(shop);
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
