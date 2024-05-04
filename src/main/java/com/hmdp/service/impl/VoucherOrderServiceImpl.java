package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author jialin.zhou
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHander());
    }


//    /**
//     * 执行秒杀券操作
//     * @param voucherId 秒杀券的ID
//     * @return 返回操作结果，成功则返回秒杀订单信息，失败则返回错误信息
//     */
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 根据券ID获取秒杀券信息
//        SeckillVoucher vocher = seckillVoucherService.getById(voucherId);
//
//        // 判断秒杀时间是否在有效期内
//        if (vocher.getBeginTime().isAfter(LocalDateTime.now())){
//            return Result.fail("秒杀尚未开始");
//        }
//        if (vocher.getBeginTime().isBefore(LocalDateTime.now())){
//            return Result.fail("已经结束");
//        }
//
//        // 判断库存是否充足
//        if (vocher.getStock() < 1){
//            return Result.fail("库存不足");
//        }
//
//        // 获取当前用户ID
//        Long userId = UserHolder.getUser().getId();
//        // 尝试创建锁对象
//        // SimpleRedisLock lock = new SimpleRedisLock("order:" + userId , stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//
//        // 获取锁
//        boolean isLock = lock.tryLock();
//        if (!isLock){
//            // 获取锁失败，返回错误信息或者重试
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            // 获取代理对象以支持AOP（如事务）进行订单创建
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            // 创建秒杀订单并返回结果
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            // 释放锁
//            lock.unlock();
//        }
//    }

    private class VoucherOrderHander implements Runnable{
        String queueName = "stream.orders";
        /**
         * 该方法是一个无限循环，用于持续从消息队列中获取订单信息并处理。
         * 它不断监听指定的消息队列，一旦有新的订单信息，则对其进行处理。
         * 处理流程包括：获取订单信息、解析订单、处理订单、确认消息处理完成。
         */
        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
                    );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有消息，继续下一次循环
                        continue;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.创建订单
                    createVoucherOrder(voucherOrder);
                    // 4.确认消息 XACK
                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常111", e);
                    handlePendingList();
                }
            }
        }


        /**
         * 处理待处理订单列表。
         * 该方法从Redis的指定流中读取消息（订单信息），处理这些消息（下单操作），并确认处理成功的消息。
         * 使用Redis Stream作为消息队列，以group和consumer的方式消费消息。
         */
        private void handlePendingList() {
            while (true) {
                try {
                    // 从Redis流中读取一条消息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "ci"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );

                    // 判断是否成功获取到消息
                    if (list == null || list.isEmpty()) {
                        // 若没有消息，则退出循环
                        break;
                    }

                    // 处理获取到的消息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    createVoucherOrder(voucherOrder);

                    // 确认消息处理成功
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    // 记录处理过程中出现的异常
                    log.error("处理pending-list异常", e);
                    try {
                        // 异常处理后短暂休眠，避免快速重试造成资源压力
                        Thread.sleep(20);
                    } catch (InterruptedException interruptedException) {
                        interruptedException.printStackTrace();
                    }
                }
            }
        }

    }


//    // 静态初始化块：初始化用于执行Redis脚本的DefaultRedisScript对象
//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
//    /**
//     * 该类为处理代金券订单的线程处理器，实现了Runnable接口。
//     */
//    private class VoucherOrderHander implements Runnable{
//        @Override
//        public void run() {
//            // 无限循环，持续处理订单
//            while (true) {
//                try {
//                    // 从队列中获取订单信息
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    // 处理获取到的代金券订单
//                    handleVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    // 记录处理订单过程中的异常
//                    log.error("处理订单异常", e);
//                }
//            }
//        }
//    }


//    private IVoucherOrderService proxy;
//    /**
//     * 处理代金券订单
//     * @param voucherOrder 代金券订单对象，包含订单相关信息
//     * 本方法通过用户ID获取锁，确保同一用户在同一时间只能创建一个代金券订单，
//     * 以避免重复下单的问题。
//     */
//    private void handleVoucherOrder(VoucherOrder voucherOrder) {
//        Long userId = voucherOrder.getUserId();
//        RLock lock = redissonClient.getLock(LOCK_ORDER_KEY + userId);
//        boolean isLock = lock.tryLock();
//        if (!isLock) {
//            // 索取锁失败，重试或者直接抛异常（这个业务是一人一单，所以直接返回失败信息）
//            log.error("一人只能下一单");
//            return;
//        }
//        try {
//            // 创建订单（使用代理对象调用，是为了确保事务生效）
//            proxy.createVoucherOrder(voucherOrder);
//        } finally {
//            lock.unlock();
//        }
//    }


    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId(); // 获取当前用户ID
        long orderId = redisIdWorker.nextId("order"); // 获取订单ID
        // 执行lua脚本进行秒杀操作，校验库存和防止重复下单
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        // 解析lua脚本返回的结果
        int res = result.intValue();
        // 如果结果不为0，则表示无法购买，根据返回值提供具体原因
        if (res != 0){
            return Result.fail(res == 1 ? "库存不足" : "不能重复下单");
        }
        // 返回生成的订单ID
        return Result.ok(orderId);
    }



//    /**
//     * 执行秒杀优惠券的逻辑。
//     *
//     * @param voucherId 优惠券ID
//     * @return Result 结果对象，包含订单ID或失败信息
//     */
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        Long userId = UserHolder.getUser().getId(); // 获取当前用户ID
//        // 执行lua脚本进行秒杀操作，校验库存和防止重复下单
//        Long result = stringRedisTemplate.execute(
//                SECKILLSCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(),
//                userId.toString()
//        );
//        // 解析lua脚本返回的结果
//        int res = result.intValue();
//        // 如果结果不为0，则表示无法购买，根据返回值提供具体原因
//        if (res != 0){
//            return Result.fail(res == 1 ? "库存不足" : "不能重复下单");
//        }
//        // 如果可以购买，则生成订单并加入到阻塞队列中
//        VoucherOrder voucherOrder = new VoucherOrder();
//        long orderId = redisIdWorker.nextId("order"); // 生成订单ID
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
//        orderTasks.add(voucherOrder); // 将订单任务加入到队列
//
//        // 获取当前秒杀服务的代理对象，用于后续操作
//        proxy = (IVoucherService) AopContext.currentProxy();
//
//        // 返回生成的订单ID
//        return Result.ok(orderId);
//    }



//    /**
//     * 创建优惠券订单
//     * @param voucherId 优惠券ID
//     * @return Result 结果对象，包含订单ID或错误信息
//     */
//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        // 判断用户是否已经有一单对应的购买
//        Long userId = UserHolder.getUser().getId();
//        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//        if (count > 0){
//            return Result.fail("该用户已经购买过");
//        }
//
//        // 扣减优惠券库存
//        boolean success = seckillVoucherService.update().
//                setSql("stock = stock - 1").
//                eq("voucher_id", voucherId).gt("stock", 0) // 只有在库存大于0时才扣减
//                .update();
//        if (!success){
//            return Result.fail("库存不足");
//        }
//
//        // 创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        long orderID = redisIdWorker.nextId("order");  // 生成订单ID
//        voucherOrder.setId(orderID);
//        voucherOrder.setVoucherId(voucherId);
//        Long userID = UserHolder.getUser().getId();  // 获取当前用户ID
//        voucherOrder.setUserId(userID);
//        save(voucherOrder);  // 保存订单信息
//        return Result.ok(orderID);
//    }

    /**
     * 创建代金券订单
     * @param voucherOrder 代金券订单对象，包含订单详细信息
     * @Transactional 注解表明该方法是一个事务方法，保证数据的一致性
     */
    private void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // 创建锁对象
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 尝试获取锁
        boolean isLock = redisLock.tryLock();
        // 判断
        if (!isLock) {
            // 获取锁失败，直接返回失败或者重试
            log.error("不允许重复下单！");
            return;
        }

        try {
            // 5.1.查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 5.2.判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                log.error("不允许重复下单！");
                return;
            }

            // 6.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // 扣减失败
                log.error("库存不足！");
                return;
            }

            // 7.创建订单
            save(voucherOrder);
        } finally {
            // 释放锁
            redisLock.unlock();
        }
    }



}
