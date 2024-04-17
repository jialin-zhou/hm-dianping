package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override

    /**
     * 执行秒杀券操作
     * @param voucherId 秒杀券的ID
     * @return 返回操作结果，成功则返回秒杀订单信息，失败则返回错误信息
     */
    public Result seckillVoucher(Long voucherId) {
        // 根据券ID获取秒杀券信息
        SeckillVoucher vocher = seckillVoucherService.getById(voucherId);

        // 判断秒杀时间是否在有效期内
        if (vocher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始");
        }
        if (vocher.getBeginTime().isBefore(LocalDateTime.now())){
            return Result.fail("已经结束");
        }

        // 判断库存是否充足
        if (vocher.getStock() < 1){
            return Result.fail("库存不足");
        }

        // 获取当前用户ID
        Long userId = UserHolder.getUser().getId();
        // 尝试创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId , stringRedisTemplate);

        // 获取锁
        boolean isLock = lock.tryLock(1200L);
        if (!isLock){
            // 获取锁失败，返回错误信息或者重试
            return Result.fail("不允许重复下单");
        }
        try {
            // 获取代理对象以支持AOP（如事务）进行订单创建
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            // 创建秒杀订单并返回结果
            return proxy.createVoucherOrder(voucherId);
        } finally {
            // 释放锁
            lock.unLock();
        }
    }


    /**
     * 创建优惠券订单
     * @param voucherId 优惠券ID
     * @return Result 结果对象，包含订单ID或错误信息
     */
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 判断用户是否已经有一单对应的购买
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0){
            return Result.fail("该用户已经购买过");
        }

        // 扣减优惠券库存
        boolean success = seckillVoucherService.update().
                setSql("stock = stock - 1").
                eq("voucher_id", voucherId).gt("stock", 0) // 只有在库存大于0时才扣减
                .update();
        if (!success){
            return Result.fail("库存不足");
        }

        // 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderID = redisIdWorker.nextId("order");  // 生成订单ID
        voucherOrder.setId(orderID);
        voucherOrder.setVoucherId(voucherId);
        Long userID = UserHolder.getUser().getId();  // 获取当前用户ID
        voucherOrder.setUserId(userID);
        save(voucherOrder);  // 保存订单信息
        return Result.ok(orderID);
    }

}
