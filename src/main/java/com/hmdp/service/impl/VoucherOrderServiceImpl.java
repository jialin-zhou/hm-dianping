package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
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

    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher vocher = seckillVoucherService.getById(voucherId);
        // 1.判断是否在时间内
        if (vocher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始");
        }
        if (vocher.getBeginTime().isBefore(LocalDateTime.now())){
            return Result.fail("已经结束");
        }
        // 2.判断库存是否充足
        if (vocher.getStock() < 1){
            return Result.fail("库存不足");
        }

        // 一人一单业务
        // 查询订单
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 判断订单是否已经存在
        if (count > 0){
            return Result.fail("该用户已经购买过");
        }

        // 3.扣减库存
        boolean success = seckillVoucherService.update().
                setSql("stock = stock - 1").
                eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                .update();
        if (!success){
            return Result.fail("库存不足");
        }

        // 4.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderID = redisIdWorker.nextId("order");
        // 订单id
        voucherOrder.setId(orderID);
        // 优惠券id
        voucherOrder.setVoucherId(voucherId);
        // 下单的用户id
        Long userID = UserHolder.getUser().getId();
        voucherOrder.setUserId(userID);
        save(voucherOrder);
        return Result.ok(orderID);

    }
}
