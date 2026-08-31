package com.lx.ai.memory;

import com.lx.ai.entity.po.Msg;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.RedisTemplate;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

public class RedisChatMemory implements ChatMemory {

    private static final String KEY_PREFIX = "chat:memory:";

    private final RedisTemplate<String, Msg> redisTemplate;

    /** 记忆过期天数：>0 表示 N 天后过期，<=0 表示永久保存 */
    private final long ttlDays;

    public RedisChatMemory(RedisTemplate<String, Msg> redisTemplate, long ttlDays) {
        this.redisTemplate = redisTemplate;
        this.ttlDays = ttlDays;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        String key = KEY_PREFIX + conversationId;
        List<Msg> msgList = messages.stream().map(Msg::new).collect(Collectors.toList());
        redisTemplate.opsForList().rightPushAll(key, msgList);
        // TTL 配置化：ttlDays > 0 时 N 天过期；<=0 永久保存
        if (ttlDays > 0) {
            redisTemplate.expire(key, Duration.ofDays(ttlDays));
        }
    }

    @Override
    public void add(String conversationId, Message message) {
        add(conversationId, List.of(message));
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        String key = KEY_PREFIX + conversationId;
        long size = redisTemplate.opsForList().size(key);
        if (size <= 0) return List.of();
        long start = Math.max(0, size - lastN);
        List<Msg> msgs = redisTemplate.opsForList().range(key, start, -1);
        if (msgs == null) return List.of();
        List<Message> collect = msgs.stream().map(Msg::toMessage).collect(Collectors.toList());
        return collect;

    }

    @Override
    public void clear(String conversationId) {
        redisTemplate.delete(KEY_PREFIX + conversationId);
    }
}
