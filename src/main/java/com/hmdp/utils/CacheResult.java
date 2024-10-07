package com.hmdp.utils;

import lombok.Value;

@Value
public class CacheResult<T> {

    public static <E> CacheResult<E> hit(E data) {
        return new CacheResult<>(data, true);
    }

    public static <ANY> CacheResult<ANY> miss() {
        return new CacheResult<>(null, false);
    }

    T data;
    boolean hit;

    private CacheResult(T data, boolean hit) {
        this.data = data;
        this.hit = hit;
    }
}
