package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.VoucherOrder;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author jialin.zhou
 */
public interface IVoucherService extends IService<Voucher> {


    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);
}
