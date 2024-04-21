package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    /**
     * 用户关注或取消关注操作。
     *
     * @param followUserId 被关注用户的ID。
     * @param isFollow 是否关注的标志，true表示关注，false表示取消关注。
     * @return 返回操作结果，成功返回Result.ok()。
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId(); // 获取当前用户的ID
        // 根据isFollow标志进行关注或取消关注的操作
        if (isFollow){
            // 关注操作
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);// 保存关注关系
            if (isSuccess){
                // 关注成功，更新Redis中的关注数
                stringRedisTemplate.opsForSet().add("follow:" + userId, followUserId.toString());
            }
        }else{
            // 取消关注操作
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId).eq("follow_user_id", followUserId));// 根据用户ID和被关注用户ID删除关注关系
            // 把关注用户的id从redis中移除
            if (isSuccess){
                stringRedisTemplate.opsForSet().remove("follow:" + userId, followUserId.toString());
            }
        }
        return Result.ok(); // 返回操作成功的结果
    }


    /**
     * 检查当前用户是否关注了指定的用户。
     *
     * @param followUserId 要检查是否被关注的用户的ID。
     * @return 返回一个结果对象，如果指定用户被当前用户关注，则结果对象的标识为真，否则为假。
     */
    @Override
    public Result isFollow(Long followUserId) {
        // 获取当前用户的ID
        Long userId = UserHolder.getUser().getId();
        // 查询当前用户是否关注了指定的用户，返回关注数量
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        // 根据查询结果的数量判断当前用户是否关注了指定用户，返回对应的结果对象
        return Result.ok(count > 0);
    }

    /**
     * 用户关注普通用户。
     *
     * @param id 目标用户ID。
     * @return 返回操作结果，如果目标用户是被关注的，则返回该用户的信息列表；否则返回空列表。
     */
    @Override
    public Result followCommons(Long id) {
        // 获取当前操作用户的ID
        Long userId = UserHolder.getUser().getId();
        // 构造当前用户和目标用户的关注键
        String key = "follow:" + userId;
        String key2 = "follow:" + id;
        // 查找当前用户和目标用户之间关注的交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        // 如果没有交集，则返回空列表
        if (intersect == null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        // 将交集中的字符串转换为Long类型，并收集到列表中
        List<Long> idx = intersect.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        // 根据ID列表查询用户信息，并转换为UserDTO列表
        List<UserDTO> userDTOS = userService.listByIds(idx)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        // 返回用户信息列表
        return Result.ok(userDTOS);
    }


}
