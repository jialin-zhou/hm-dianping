package com.hmdp.utils;

import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.lang.UUID;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    /**
     * 简单的Redis锁实现类。
     * 使用StringRedisTemplate进行Redis操作，提供基本的锁机制来保证并发安全。
     */
    private StringRedisTemplate stringRedisTemplate;

    // 锁的唯一标识符，用于区分不同的锁
    private String name;

    // 锁键的前缀，用于在Redis中标识锁
    private static final String KEY_PREFIX = "lock:";

    // 为当前线程生成的唯一标识符前缀，用于区分不同线程持有的锁
    private static final String THREAD_PREFIX = UUID.randomUUID().toString(true) + "-";

    // 静态初始化块：初始化用于执行Redis脚本的DefaultRedisScript对象
    private static final DefaultRedisScript<Long> UNLOCKSCRIPT;

    static {
        UNLOCKSCRIPT = new DefaultRedisScript<>();
        // 设置脚本资源的位置，脚本文件unlock.lua位于类路径下
        UNLOCKSCRIPT.setLocation((Resource) new ClassPathResource("unlock.lua"));
        // 指定脚本执行结果的类型为Long
        UNLOCKSCRIPT.setResultType(Long.class);
    }

    /**
     * 构造函数，初始化锁实例。
     *
     * @param name 锁的名称，必须唯一，用于标识不同的锁。
     * @param stringRedisTemplate 用于操作Redis的模板类，不能为空。
     */
    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }


    /**
     * 尝试获取分布式锁。
     * 在指定超时时间内尝试获取锁，如果在超时时间内成功获取，则返回true，否则返回false。
     * 这个方法使用Redis的值存在性检查来实现锁的获取，利用Redis的高并发性和原子性保证锁的正确性。
     *
     * @param timeSec 尝试获取锁的超时时间，单位为秒。这个参数指定了尝试获取锁的最大时间限制。
     * @return 如果成功获取锁返回true，否则返回false。这使得调用者可以基于返回值决定是否继续加锁逻辑。
     */
    @Override
    public boolean tryLock(Long timeSec) {
        // 生成当前线程的唯一标识，用作分布式锁的标识。
        String threadId = THREAD_PREFIX + Thread.currentThread().getId();
        // 尝试在Redis中设置锁，如果锁未被其他线程（即锁的值不存在）持有，则设置成功，这实现了锁的获取逻辑。
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId + "", timeSec, TimeUnit.SECONDS);
        // 判断设置操作是否成功，即是否成功获取了锁。
        return Boolean.TRUE.equals(success);
    }


    /**
     * 解锁操作。
     * 该方法通过调用预定义的Lua脚本实现解锁逻辑。它会针对指定的锁键执行Lua脚本，确保锁的正确释放。
     * 注意：该方法不接受参数，也不返回任何值。
     */
    @Override
    public void unLock() {
        // 执行解锁Lua脚本，传入锁的键名和当前线程ID作为参数
        stringRedisTemplate.execute(
                UNLOCKSCRIPT,
                Collections.singletonList(KEY_PREFIX + name), // 锁的键名，通过KEY_PREFIX和锁名拼接
                THREAD_PREFIX + Thread.currentThread().getId() // 当前线程ID，通过THREAD_PREFIX和Java线程的getId()方法获取
        );
    }



    /**
     * 释放锁。
     * 此方法首先确定当前线程是否是持有锁的线程，如果是，则释放该锁。
     * 释放锁的操作是通过在Redis中删除对应的锁键来实现的。
     *
     * 参数说明：
     * 无参数
     *
     * 返回值：
     * 无返回值
     */
//    @Override
//    public void unLock() {
//        // 获取当前线程的标识，格式为"线程前缀+线程ID"
//        String threadId = THREAD_PREFIX + Thread.currentThread().getId();
//        // 从Redis获取当前锁的持有线程标识
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        // 判断当前线程是否是锁的持有者
//        if (threadId.equals(id)) {
//            // 如果是，则释放锁，即从Redis中删除对应的锁键
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }
}
