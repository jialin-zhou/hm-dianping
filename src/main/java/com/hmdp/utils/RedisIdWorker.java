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
    /**
     * 生成下一个ID
     * @param keyPrefix 键前缀，用于区分不同的ID序列
     * @return 生成的ID，是一个长整型数字
     */
    public long nextId(String keyPrefix) {

        // 1.生成时间戳
        long currentTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timestamp = currentTime - BEGIN_TIMESTAMP;
        // 2.生成序列号
        // 2.1 获取当前日期 精确到天
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2 使用Redis的自增操作获取当前日期内的序列号，保证唯一性
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        // 3.将时间戳和序列号拼接成一个长整型数字，返回
        return timestamp << COUNT_BITS | count;
    }


}
