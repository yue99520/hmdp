package com.hmdp.utils;

import java.text.SimpleDateFormat;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * @author Ernie Lee
 */
@Configuration
public class ObjectMapperProvider {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        return om;
    }
}
