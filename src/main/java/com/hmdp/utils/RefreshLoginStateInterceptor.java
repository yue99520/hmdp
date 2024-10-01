package com.hmdp.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.UserDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.SystemConstants.AUTHORIZATION_TOKEN_HEADER;

@Component
public class RefreshLoginStateInterceptor implements HandlerInterceptor {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${user.login.token.expiration.seconds:36000}")
    public Integer USER_LOGIN_SESSION_EXPIRATION_SECONDS;

    private final StringRedisTemplate redisTemplate;

    public RefreshLoginStateInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader(AUTHORIZATION_TOKEN_HEADER);
        if (token == null) {
            return true;
        }
        String userTokenKey = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = redisTemplate.opsForHash().entries(userTokenKey);
        if (!userMap.isEmpty()) {
            UserDTO userDTO = objectMapper.convertValue(userMap, UserDTO.class);
            UserHolder.saveUser(userDTO);
            redisTemplate.expire(userTokenKey, USER_LOGIN_SESSION_EXPIRATION_SECONDS, TimeUnit.SECONDS);
        }
        return true;
    }
}
