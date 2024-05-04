package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.management.RuntimeMBeanException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author jialin.zhou
 */
@Slf4j
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

//    /**
//     * 带互斥锁的查询商铺方法
//     * 本方法首先尝试从Redis缓存中查询商铺信息，如果缓存中存在则直接返回缓存数据。
//     * 如果缓存中不存在或数据为空，则尝试获取互斥锁进行数据的重建和缓存更新。
//     * 在获取互斥锁失败的情况下，会进行重试，以避免数据竞争。
//     *
//     * @param id 商铺的唯一标识符
//     * @return 返回查询到的商铺对象，如果未查询到则返回null
//     */
//    public Shop queryWithMutex(Long id){
//        // 从Redis查询商铺id，尝试获取缓存中的商铺数据
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//
//        // 判断缓存中是否存在商铺数据
//        if (StrUtil.isNotBlank(shopJson)){
//            // 缓存中存在，直接返回解析后的商铺对象
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//
//        // 判断是否命中的缓存是空值
//        if (shopJson != null){
//            // 返回错误信息，表示未查询到商铺
//            return null;
//        }
//
//        // 尝试获取互斥锁进行缓存重建
//        Shop shop = null;
//        try {
//            // 尝试获取锁，如果失败则休眠后重试
//            boolean isLock = tryLock(LOCK_SHOP_KEY + id);
//            if (!isLock){
//                Thread.sleep(50);
//                return queryWithMutex(id); // 重试查询
//            }
//
//            // 获取锁成功，从数据库查询商铺信息
//            shop = getById(id);
//            Thread.sleep(200); // 模拟数据重建的延时
//
//            // 如果查询到的商铺为空，则在缓存中存储空值并返回错误信息
//            if (shop == null) {
//                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//
//            // 将查询到的商铺数据写入缓存
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            // 无论如何都释放互斥锁
//            unLock(LOCK_SHOP_KEY + id);
//        }
//
//        // 返回查询到的商铺对象
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

    /**
     * 根据商店类型、页码以及坐标查询商店信息。
     * 如果提供了坐标（x和y非空），则按照坐标附近的距离排序并分页查询商店；
     * 如果未提供坐标，则仅按照类型ID分页查询。
     *
     * @param typeId 商店类型ID
     * @param current 当前页码
     * @param x 坐标X值（可选）
     * @param y 坐标Y值（可选）
     * @return 返回查询结果，包含商店信息列表。如果查询不到任何商店，返回空列表。
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 判断是否需要根据坐标查询
        if (x == null || y == null){
            // 不需要坐标查询，按照id分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        // 需要根据坐标进行查询时的处理
        // 分页查询的参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        String key = SHOP_GEO_KEY + typeId;
        // 查询redis，按照距离排序、分页，结果包括shopId和距离
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 根据查询结果处理
        if (results == null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from){
            return Result.ok(Collections.emptyList());
        }
        // 截取所需部分结果，并准备相关数据
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // log.info("店铺信息: {}", result);
            Distance distance = result.getDistance();
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            distanceMap.put(shopIdStr, distance);
        });
        // 根据截取到的ID查询商店信息，并设置每个商店的距离信息
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops){
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 返回查询到的商店信息
        // log.info("店铺信息: {}", shops);
        return Result.ok(shops);
    }
}
