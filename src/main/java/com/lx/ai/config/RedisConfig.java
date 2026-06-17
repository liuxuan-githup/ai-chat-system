package com.lx.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lx.ai.entity.po.Msg;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {


    @Bean
    public RedisTemplate<String, Msg> redisTemplate(RedisConnectionFactory factory, ObjectMapper objectMapper) {
        RedisTemplate<String, Msg> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());

        // 通过构造函数传入 ObjectMapper
        Jackson2JsonRedisSerializer<Msg> jacksonSerializer = new Jackson2JsonRedisSerializer<>(objectMapper, Msg.class);
        template.setValueSerializer(jacksonSerializer);
        template.setHashValueSerializer(jacksonSerializer);
        return template;
    }
}
