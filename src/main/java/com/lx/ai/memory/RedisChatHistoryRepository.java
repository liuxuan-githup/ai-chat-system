package com.lx.ai.memory;

import com.lx.ai.repository.ChatHistoryRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class RedisChatHistoryRepository implements ChatHistoryRepository {

    private static final String KEY_PREFIX = "chat:ids:";

    private final RedisTemplate<String, String> stringRedisTemplate;

    public RedisChatHistoryRepository(RedisTemplate<String, String> stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void save(String type, String chatId) {
        stringRedisTemplate.opsForSet().add(KEY_PREFIX + type, chatId);
    }

    @Override
    public List<String> getChatIds(String type) {
        Set<String> ids = stringRedisTemplate.opsForSet().members(KEY_PREFIX + type);
        return ids == null ? List.of() : List.copyOf(ids);
    }

    @Override
    public void delete(String type, String chatId) {
        stringRedisTemplate.opsForSet().remove(KEY_PREFIX + type, chatId);
    }
}
