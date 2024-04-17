package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    /**
     * 查询指定店铺的优惠券信息
     *
     * @param shopId 店铺ID，用于指定要查询的店铺
     * @return Result对象，包含查询到的优惠券信息列表。如果查询成功，Result的code为200，data为优惠券列表；如果查询失败，Result的code为其他值，data可能为空或包含错误信息。
     */
    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询指定店铺的优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回查询结果
        return Result.ok(vouchers);
    }


    /**
     * 添加秒杀优惠券
     * @param voucher 优惠券对象，包含优惠券的基本信息和秒杀相关属性
     * @Transactional 注解表明该方法是一个事务方法，操作失败时需要回滚
     */
    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券基本信息
        save(voucher);
        // 初始化并保存秒杀优惠券信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId()); // 设置优惠券ID
        seckillVoucher.setStock(voucher.getStock()); // 设置秒杀库存
        seckillVoucher.setBeginTime(voucher.getBeginTime()); // 设置秒杀开始时间
        seckillVoucher.setEndTime(voucher.getEndTime()); // 设置秒杀结束时间
        seckillVoucherService.save(seckillVoucher); // 保存秒杀优惠券信息
    }

}
