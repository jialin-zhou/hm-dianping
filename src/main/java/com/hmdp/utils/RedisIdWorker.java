package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    // 开始时间
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    // 序列号的位数
    private static final long COUNT_BITS = 32L;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public long nextId(String keyPrefix) {
        // 1.生成时间戳
        long currentTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timestamp = currentTime - BEGIN_TIMESTAMP;
        // 2.生成序列号
        // 2.1 获取当前日期 精确到天
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2 自增长
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        // 3.拼接并返回
        return timestamp << COUNT_BITS | count;
    }

}
