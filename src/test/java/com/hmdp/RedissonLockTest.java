package com.hmdp;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
@Slf4j
public class RedissonLockTest {
    @Resource
    private RedissonClient redissonClient;
    private RLock lock;
    /**
     * 方法1获取一次锁
     */
    @Test
    void method1() {
        boolean isLock = false;
        // 创建锁对象
        lock = redissonClient.getLock("lock");
        try {
            isLock = lock.tryLock();
            if (!isLock) {
                log.error("获取锁失败，1");
                return;
            }
            log.info("获取锁成功，1");
            method2();
        } finally {
            if (isLock) {
                log.info("释放锁，1");
                lock.unlock();
            }
        }
    }

    /**
     * 方法二再获取一次锁
     */
    void method2() {
        boolean isLock = false;
        try {
            isLock = lock.tryLock();
            if (!isLock) {
                log.error("获取锁失败, 2");
                return;
            }
            log.info("获取锁成功，2");
        } finally {
            if (isLock) {
                log.info("释放锁，2");
                lock.unlock();
            }
        }
    }
}
