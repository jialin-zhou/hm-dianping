package com.hmdp;

import cn.hutool.core.lang.hash.Hash;
import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private ExecutorService es = Executors.newFixedThreadPool(500);
    @Test
    void testSaveShop() throws InterruptedException {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }

    /**
     * 测试IdWorker生成ID的性能。
     * 该测试模拟了300个任务并发地从ID生成器获取100个ID，共计生成30000个ID。
     * 通过计算完成所有ID生成所需的时间，可以评估ID生成器的性能。
     *
     * @throws InterruptedException 如果等待任务完成时被中断
     */
    @Test
    void testIdWorker() throws InterruptedException {
        // 创建一个计数器，用于在所有任务完成时释放等待的线程
        CountDownLatch countDownLatch = new CountDownLatch(300);

        // 定义一个任务，该任务会连续获取100个ID并打印
        Runnable task = () ->{
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order"); // 从ID生成器获取订单ID
                System.out.println("id = " + id);
            }
        };

        // 记录测试开始时间
        long begin = System.currentTimeMillis();

        // 提交300个任务到执行服务，并立即开始执行
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }

        // 等待所有任务完成
        countDownLatch.await();

        // 记录测试结束时间
        long end = System.currentTimeMillis();

        // 打印测试执行时间
        System.out.println("time = " + (end - begin));
    }


    /**
     * 加载店铺数据并存储到Redis中。
     * 该方法首先从店铺服务中查询所有店铺信息，然后按照店铺的类型ID进行分组。
     * 接着，分批将这些分组后的店铺数据写入Redis的地理信息系统中。
     */
    @Test
    void loadShopData() {
        // 查询所有店铺信息
        List<Shop> list = shopService.list();

        // 将店铺信息按照类型ID进行分组
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));

        // 遍历每个分组，将店铺数据存储到Redis的地理信息系统中
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 获取当前分组的类型ID
            Long typeId = entry.getKey();
            // 构建Redis中存储的key
            String key = SHOP_GEO_KEY + typeId;
            // 获取当前分组的店铺列表
            List<Shop> value = entry.getValue();
            // 将店铺ID转换为列表形式
            List<Long> ids = value.stream().map(Shop::getId).collect(Collectors.toList());
            // 准备存储到Redis的地理位置信息
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            // 遍历店铺列表，构建地理位置信息并添加到待存储列表中
            for (Shop shop : value) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(), new Point(shop.getX(), shop.getY())
                ));
            }
            // 批量将地理位置信息添加到Redis
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

}
