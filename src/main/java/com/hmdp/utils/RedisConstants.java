package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shop-type";

    public static final String LOCK_SHOP_KEY = "shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String GLOBAL_ID_INCREMENT_KEY = "id:increment:";
    public static final String LOCK_ORDER_KEY = "order:";

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
