package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author jialin.zhou
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询商铺类型，优先从Redis缓存中读取，若缓存中不存在则从数据库查询，并将查询结果存入Redis缓存。
     *
     * @return Result对象，包含查询到的商铺类型列表或错误信息。
     */
    @Override
    public Result queryShopType() {
        // 从redis查询商铺类型
        String shopTypeJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);
        // 如果缓存中存在商铺类型数据，则直接返回
        if (StrUtil.isNotBlank(shopTypeJson)){
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(shopTypes);
        }
        // 缓存中不存在商铺类型数据时，从数据库中查询
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        // 如果数据库中也不存在商铺类型数据，则返回错误信息
        if (shopTypeList.isEmpty()){
            return Result.fail("商铺类型不存在");
        }
        // 将从数据库查询到的商铺类型数据写入Redis缓存
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY,JSONUtil.toJsonStr(shopTypeList));
        // 返回查询结果
        return Result.ok(shopTypeList);
    }

}
