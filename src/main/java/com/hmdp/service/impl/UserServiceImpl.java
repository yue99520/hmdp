package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.SystemConstants.*;

/**
 * @author Ernie Lee
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper mapper;

    @Value("${user.login.code.expiration.seconds:120}")
    public Integer USER_LOGIN_CODE_EXPIRATION_SECONDS;

    @Value("${user.login.token.expiration.seconds:36000}")
    public Integer USER_LOGIN_SESSION_EXPIRATION_SECONDS;

    /*
     * 1. validate phone format 09123456789001
     * 2. gen code
     * 3. save code to session
     * 4. send code
     * 5. return OK
     * */
    @Override
    public Result sendCode(String phone, HttpSession session) {

        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("Invalid phone number.");
        }
        String code = RandomUtil.randomNumbers(6);

        stringRedisTemplate.opsForValue().set(
                LOGIN_CODE_KEY + phone,
                code,
                USER_LOGIN_CODE_EXPIRATION_SECONDS, TimeUnit.SECONDS);

        log.info("Send login code for phone {}: {}", phone, code);
        return Result.ok();
    }

    /*
     * 1. validate login code
     * 2. get user by phone
     * 3. create user or login
     * 4. save to session
     * 5. return ok
     * */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("Invalid phone number.");
        }
        String code = loginForm.getCode();
        String expectedCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (expectedCode == null || !expectedCode.equals(code)) {
            return Result.fail("Bad code number");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createUserWithPhone(phone);
        }

        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, String> userDTOMap = mapper.convertValue(userDTO, new TypeReference<Map<String, String>>() {});

        stringRedisTemplate.execute(new SessionCallback<List<Object>>() {
            @Override
            public <K, V> List<Object> execute(RedisOperations<K, V> operations) throws DataAccessException {
                String key = LOGIN_USER_KEY + token;
                stringRedisTemplate.multi();
                stringRedisTemplate.opsForHash().putAll(key, userDTOMap);
                stringRedisTemplate.expire(key, USER_LOGIN_SESSION_EXPIRATION_SECONDS, TimeUnit.SECONDS);
                return stringRedisTemplate.exec();
            }
        });

        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
