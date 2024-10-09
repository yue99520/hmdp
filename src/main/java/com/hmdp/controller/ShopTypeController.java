package com.hmdp.controller;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {

    @Autowired
    IShopTypeService typeService;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    StringRedisTemplate redisTemplate;

    @GetMapping("list")
    public Result queryTypeList() {
        List<ShopType> typeList = getShopTypeListFromRedis();
        if (typeList != null) {
            return Result.ok(typeList);
        }
        typeList = typeService.query().orderByAsc("sort").list();
        if (typeList == null || typeList.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        saveShopTypeListToRedis(typeList);
        return Result.ok(typeList);
    }

    private List<ShopType> getShopTypeListFromRedis() {
        Set<String> result = redisTemplate.opsForZSet().range(CACHE_SHOP_TYPE_KEY, 0, -1);
        if (result == null || result.isEmpty()) {
            return null;
        }
        try {
            return deserializeShopTypes(result);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize shop types", e);
            return null;
        }
    }

    private void saveShopTypeListToRedis(List<ShopType> typeList) {
        try {
            Set<ZSetOperations.TypedTuple<String>> shopTypes = serializeShopTypes(typeList);
            redisTemplate.execute(new SessionCallback<List<Object>>() {
                @Override
                public <K, V> List<Object> execute(RedisOperations<K, V> redisOperations) throws DataAccessException {
                    StringRedisTemplate operations = (StringRedisTemplate) redisOperations;
                    operations.multi();
                    operations.opsForZSet().add(CACHE_SHOP_TYPE_KEY, shopTypes);
                    operations.expire(CACHE_SHOP_TYPE_KEY, 30, TimeUnit.MINUTES);
                    return operations.exec();
                }
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize shop types", e);
        }
    }

    private List<ShopType> deserializeShopTypes(Set<String> result) throws JsonProcessingException {
        List<ShopType> typeList = new ArrayList<>();
        for (String json : result) {
            typeList.add(objectMapper.readValue(json, ShopType.class));
        }
        return typeList;
    }

    private Set<ZSetOperations.TypedTuple<String>> serializeShopTypes(List<ShopType> typeList) throws JsonProcessingException {
        Set<ZSetOperations.TypedTuple<String>> shopTypes = new HashSet<>();
        for (ShopType shopType : typeList) {
            Integer sort = shopType.getSort();
            shopTypes.add(new DefaultTypedTuple<>(objectMapper.writeValueAsString(shopType), sort == null ? 0.0 : sort.doubleValue()));
        }
        return shopTypes;
    }
}
