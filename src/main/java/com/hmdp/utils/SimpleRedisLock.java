package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private StringRedisTemplate stringRedisTemplate;
    // 锁的名称
    private String name;
    // 锁的前缀
    private static final String KEY_PREFIX = "lock:";
    // 构造函数
    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    /**
     * 尝试获取分布式锁。
     *
     * @param timeSec 尝试获取锁的超时时间，单位为秒。
     * @return 如果成功获取锁返回true，否则返回false。
     */
    @Override
    public boolean tryLock(Long timeSec) {
        // 获取当前线程的ID，用于作为锁的标识
        long threadId = Thread.currentThread().getId();
        // 尝试以当前线程ID为值设置锁，如果锁未被其他线程持有则设置成功
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId + "", timeSec, TimeUnit.SECONDS);
        // 判断是否成功获取锁
        return Boolean.TRUE.equals(success);
    }


    @Override
    public void unLock() {
        stringRedisTemplate.delete(KEY_PREFIX + name);
    }
}
