package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

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
            save(follow); // 保存关注关系
        }else{
            // 取消关注操作
            remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId).eq("follow_user_id", followUserId)); // 根据用户ID和被关注用户ID删除关注关系
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

}
