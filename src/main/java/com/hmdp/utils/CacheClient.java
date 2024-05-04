package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.management.RuntimeMBeanException;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 定义一个固定大小的线程池，用于缓存重建任务的执行。
     * 线程池的最大线程数为10。
     * 这是一个静态常量，意味着它在类的生命周期中只被初始化一次，并且在所有的方法中都可以访问。
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }
    /**
     * 将给定的键值对存储到Redis中，并设定过期时间。
     *
     * @param key 键，用于在Redis中标识存储的数据。
     * @param value 值，需要存储的数据，将被转换为JSON字符串存储。
     * @param time 过期时间，单位由unit参数指定。
     * @param unit 时间单位，指定key的value的过期时间。
     */
    public void set(String key, Object value, Long time, TimeUnit unit){
        // 使用stringRedisTemplate将value以JSON格式存储到Redis中，同时设置过期时间
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }


    /**
     * 为指定的键设置值，并设定逻辑过期时间。
     * @param key 键
     * @param value 值
     * @param time 过期时间
     * @param unit 时间单位
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        // 创建RedisData对象并设置过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 将RedisData对象序列化后，设置到Redis中
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    /**
     * 根据键前缀和ID查询数据，首先尝试从Redis中获取，如果未命中再从数据库查询，并将结果缓存到Redis中。
     *
     * @param keyPrefix 键的前缀。
     * @param id 数据的唯一标识。
     * @param type 查询结果的类型。
     * @param dbFallback 从数据库查询数据的函数。
     * @param time 缓存时间。
     * @param unit 时间单位。
     * @return 查询到的数据，如果未查询到则返回null。
     * @param <R> 查询结果的类型。
     * @param <ID> 数据的唯一标识的类型。
     *
     * 使用缓存空值的方式解决缓存击穿问题
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        // 从Redis中根据键获取值
        String json = stringRedisTemplate.opsForValue().get(key);

        // 如果Redis中存在该键，则直接返回反序列化后的对象
        if (StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json, type);
        }

        // 判断Redis中返回的是否是空值
        if (json != null){
            // Redis中存在空值时，直接返回null
            return null;
        }

        // 从数据库中查询数据
        R r = dbFallback.apply(id);

        // 如果数据库中未查询到数据，则在Redis中设置一个空值的缓存，并返回null
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, unit);
            return null;
        }

        // 将查询到的数据缓存在Redis中
        this.set(key, r, time, unit);

        // 返回查询到的数据
        return r;
    }


    /**
     * 通过逻辑过期时间查询数据，如果数据存在且未过期，则直接返回数据；如果数据已过期，则尝试重建缓存。
     *
     * @param keyPrefix 键前缀
     * @param id 数据唯一标识
     * @param type 返回数据的类型
     * @param dbFallback 从数据库中获取数据的函数
     * @param time 缓存时间
     * @param unit 时间单位
     * @return 查询到的数据，如果缓存不存在或已过期，则返回通过dbFallback从数据库获取的数据
     *
     * 设置逻辑过期时间解决缓存击穿问题
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        // 从Redis查询数据
        String json = stringRedisTemplate.opsForValue().get(key);

        // 判断缓存是否存在
        if (StrUtil.isBlank(json)){
            // 缓存不存在，直接返回null
            return null;
        }

        // 反序列化缓存数据
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);

        LocalDateTime expireTime = redisData.getExpireTime();
        // 判断缓存是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            // 缓存未过期，直接返回缓存数据
            return r;
        }

        // 缓存已过期，需要重建缓存
        // 尝试获取缓存重建的互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(key);

        if (isLock){
            // 获取锁成功，开启新线程进行缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 从数据库中查询最新数据
                    R r1 = dbFallback.apply(id);
                    // 将最新数据写入Redis缓存
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e){
                    throw new RuntimeMBeanException((RuntimeException) e);
                }finally {
                    // 重建缓存完成后释放锁
                    unLock(lockKey);
                }
            });
        }
        // 无论是否成功获取锁，都返回原有缓存数据（已过期）
        return r;
    }


    /**
     * 尝试获取锁。
     * 使用Redis的setIfAbsent方法来尝试为指定的key设置值，如果key不存在，则设置成功并返回true，表示获取锁成功；
     * 如果key已存在，则设置失败，返回false，表示获取锁失败。
     *
     * @param key 锁的关键字，用于标识锁。
     * @return boolean 返回true表示成功获取锁，返回false表示获取锁失败。
     */
    private boolean tryLock(String key){
        // 使用Redis的setIfAbsent方法尝试设置key的值为"1"，并设置过期时间为10秒，如果key不存在则设置成功并返回true
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        // 判断设置结果，返回true表示成功获取锁，返回false表示获取锁失败
        return BooleanUtil.isTrue(flag);
    }


    // 释放锁
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }
}
