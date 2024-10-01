package com.hmdp.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.UserDTO;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class UserServiceImplTest {

    @Test
    public void testCanConvertUserDTO() {
        ObjectMapper objectMapper = new ObjectMapper();

        UserDTO userDTO = new UserDTO();
        userDTO.setId(10L);
        userDTO.setNickName("test");

        Map<String, String> map = objectMapper.convertValue(userDTO, new TypeReference<Map<String, String>>() {});

        assertEquals(userDTO.getId(), Long.valueOf(map.get("id")));
        assertEquals(userDTO.getNickName(), map.get("nickName"));

        UserDTO userDTO1 = objectMapper.convertValue(map, UserDTO.class);
        assertEquals(userDTO.getId(), userDTO1.getId());
        assertEquals(userDTO.getNickName(), userDTO1.getNickName());
    }
}