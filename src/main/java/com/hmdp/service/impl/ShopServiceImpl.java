package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.management.RuntimeMBeanException;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    /**
     * 根据ID查询店铺信息。
     *
     * @param id 店铺的唯一标识符。
     * @return 返回查询结果，如果店铺存在则返回成功的查询结果，否则返回失败的查询结果。
     */
    @Override
    public Result queryById(Long id) {
        // 使用缓存并应用逻辑过期时间来避免缓存击穿
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);
        if (shop == null){
            return Result.fail("店铺不存在");
        }

        return Result.ok(shop);
    }


//    public Shop queryWithMutex(Long id){
//        // 1.从redis查询商铺id
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        // 2.判断是否存在
//        if (StrUtil.isNotBlank(shopJson)){
//            // 3.存在 直接返回
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        // 判断命中的是否是空值
//        if (shopJson != null){
//            // 返回错误信息
//            return null;
//        }
//        // 4.实现缓存重建
//        // 4.1获取互斥锁
//        Shop shop = null;
//        try {
//            boolean isLock = tryLock(LOCK_SHOP_KEY + id);
//            // 4.2判断是否获取成功
//            if (!isLock){
//                // 4.3失败，则休眠并重试
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            // 4.4 成功 根据id查询商铺
//            shop = getById(id);
//            // 模拟重建的延时
//            Thread.sleep(200);
//            // 5.不存在 返回错误
//            if (shop == null) {
//                // 将空值写入redis
//                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                // 返回错误信息
//                return null;
//            }
//            // 6.存在 写入redis
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            // 7.释放互斥锁
//            unLock(LOCK_SHOP_KEY + id);
//        }
//        // 8.返回
//        return shop;
//    }

    /**
     * 更新店铺信息
     * @param shop 包含更新后店铺信息的对象
     * @return 返回操作结果，成功返回Result.ok()，失败返回Result.fail("错误信息")
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null){
            // 检查店铺id是否为空，如果为空则返回失败结果
            return Result.fail("店铺id不能为空");
        }
        // 更新数据库中的店铺信息
        updateById(shop);
        // 删除缓存中对应的店铺信息，以保证缓存与数据库一致
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        // 返回成功结果
        return Result.ok();
    }

}
