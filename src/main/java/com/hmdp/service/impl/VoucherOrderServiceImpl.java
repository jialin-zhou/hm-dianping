package com.hmdp.service.impl;

import cn.hutool.core.io.resource.ClassPathResource;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
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

    private static final DefaultRedisScript<Long>  SECKILLSCRIPT;
    static {
        SECKILLSCRIPT = new DefaultRedisScript<>();
        // 设置脚本资源的位置，脚本文件unlock.lua位于类路径下
        SECKILLSCRIPT.setLocation((org.springframework.core.io.Resource) new ClassPathResource("seckill.lua"));
        // 指定脚本执行结果的类型为Long
        SECKILLSCRIPT.setResultType(Long.class);
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


    // 静态初始化块：初始化用于执行Redis脚本的DefaultRedisScript对象

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private class VoucherOrderHander implements Runnable{
        @Override
        public void run() {
            while (true) {
                try {
                    // 获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    private IVoucherService proxy;
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 获取用户
        Long userId = voucherOrder.getUserId();
        // 创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 获取锁
        boolean isLock = lock.tryLock();
        // 判断是否获取锁成功
        if (!isLock){
            // 获取锁失败，返回错误信息
            log.error("不允许重复下单");
            return;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILLSCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        // 判断结果是否为0
        int res = result.intValue();
        // 不为0 没有购买资格
        if (res != 0){
            return Result.fail(res == 1 ? "库存不足" : "不能重复下单");
        }
        // 为0 有购买资格，将下单信息保存到阻塞队列中
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        orderTasks.add(voucherOrder);

        // 获取代理对象
        proxy = (IVoucherService) AopContext.currentProxy();

        // 返回订单id
        return Result.ok(orderId);
    }


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

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 判断用户是否已经有一单对应的购买
        Long userId = voucherOrder.getUserId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder).count();
        if (count > 0){
            log.error("用户已经购买过一次了");
            return;
        }

        // 扣减优惠券库存
        boolean success = seckillVoucherService.update().
                setSql("stock = stock - 1").
                eq("voucher_id", voucherOrder).gt("stock", 0) // 只有在库存大于0时才扣减
                .update();
        if (!success){
            log.error("库存不足");
            return;
        }
        save(voucherOrder);  // 保存订单信息
    }


}
