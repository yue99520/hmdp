package com.hmdp.service.impl;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.hmdp.entity.Shop;
import com.hmdp.utils.ObjectMapperProvider;
import com.hmdp.utils.RedisConstants;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ShopServiceImplTest {

    ShopServiceImpl shopService;

    @BeforeEach
    void setUp() {
        shopService = spy(new ShopServiceImpl());
        shopService.redisTemplate = mock(StringRedisTemplate.class);
        shopService.objectMapper = new ObjectMapperProvider().objectMapper();
        shopService.cacheShopTtlSeconds = 60;
    }

    @Test
    void testShopByIdNoCache() throws JsonProcessingException {
        shopService.cacheShopTtlSeconds = 60;
        ValueOperations<String, String> mock = mock(ValueOperations.class);
        when(mock.get(RedisConstants.CACHE_SHOP_KEY + 1L)).thenReturn(null);
        when(shopService.redisTemplate.opsForValue()).thenReturn(mock);

        Shop dbShop = new Shop();
        dbShop.setId(1L);
        dbShop.setName("test");
        dbShop.setTypeId(1L);
        dbShop.setImages("test");
        dbShop.setArea("test");
        dbShop.setAddress("test");
        dbShop.setAvgPrice(500L);
        dbShop.setComments(1);
        dbShop.setScore(1);
        dbShop.setOpenHours("test");
        dbShop.setCreateTime(null);
        dbShop.setUpdateTime(null);
        doReturn(dbShop).when(shopService).getById(1L);

        shopService.query(1L);

        verify(shopService, times(1)).getById(1L);
        verify(mock, times(1)).set(
                RedisConstants.CACHE_SHOP_KEY + 1L,
                shopService.objectMapper.writeValueAsString(dbShop),
                shopService.cacheShopTtlSeconds,
                TimeUnit.SECONDS);
    }

    @Test
    void testShopByIdWithCache() throws JsonProcessingException {
        Shop dbShop = new Shop();
        dbShop.setId(1L);
        dbShop.setName("test");
        dbShop.setTypeId(1L);
        dbShop.setImages("test");
        dbShop.setArea("test");
        dbShop.setAddress("test");
        dbShop.setAvgPrice(500L);
        dbShop.setComments(1);
        dbShop.setScore(1);
        dbShop.setOpenHours("test");
        dbShop.setCreateTime(null);
        dbShop.setUpdateTime(null);

        String json = shopService.objectMapper.writeValueAsString(dbShop);

        shopService.cacheShopTtlSeconds = 60;
        ValueOperations<String, String> mock = mock(ValueOperations.class);
        when(mock.get(RedisConstants.CACHE_SHOP_KEY + 1L)).thenReturn(json);
        when(shopService.redisTemplate.opsForValue()).thenReturn(mock);

        shopService.query(1L);

        verify(shopService, times(0)).getById(1L);
        verify(mock, times(0)).set(any(), any(), anyLong(), any());
    }
}