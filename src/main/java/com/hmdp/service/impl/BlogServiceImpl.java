package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 根据ID查询博客信息。
     *
     * @param id 博客的唯一标识符。
     * @return 返回查询结果，如果博客存在则返回成功的查询结果包含博客信息，否则返回失败结果提示笔记不存在。
     */
    @Override
    public Result queryBlogById(Long id) {
        // 根据ID查询具体的博客
        Blog blog = getById(id);
        if (blog == null) {
            // 如果查询到的博客为null，则返回笔记不存在的错误信息
            return Result.fail("笔记不存在！");
        }
        // 查询与该博客相关的用户信息
        queryBlogUser(blog);
        // 检查当前用户是否已经点赞了该博客
        isBlogLiked(blog);
        // 返回成功的查询结果，包含博客信息
        return Result.ok(blog);
    }


    /**
     * 判断当前登录用户是否已经点赞了指定的博客
     *
     * @param blog 博客对象，需要判断是否被点赞
     * 该方法不返回任何值，但会通过设置blog对象的isLike属性来表示是否已点赞
     */
    private void isBlogLiked(Blog blog) {
        // 获取当前登录用户的ID
        Long userId = UserHolder.getUser().getId();
        Long id = blog.getId();
        // 检查该博客是否被当前用户点赞
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(BLOG_LIKED_KEY + id, userId.toString());
        // 设置blog对象的isLike属性来表示是否已点赞
        blog.setIsLike(BooleanUtil.isTrue(isMember));
    }


    /**
     * 查询热门博客列表。
     *
     * @param current 当前页码，用于分页查询。
     * @return 返回博客列表的结果对象，其中包含查询到的博客记录。
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据当前页码进行分页查询，按点赞数降序排列
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        // 获取当前页的博客列表
        List<Blog> records = page.getRecords();

        // 为每篇博客查询用户信息和是否被点赞
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });

        // 返回处理后的博客列表
        return Result.ok(records);
    }


    /**
     * 更新博客点赞状态。如果用户未点赞，则点赞数加1，并将用户ID保存到点赞集合中；如果用户已点赞，则点赞数减1，并从点赞集合中移除用户ID。
     *
     * @param id 博客的ID，用于标识需要更新点赞状态的博客。
     * @return 返回操作结果，成功则返回OK。
     */
    @Override
    public Result updateBlogLiked(Long id) {
        // 获取当前登录用户的ID
        Long userId = UserHolder.getUser().getId();
        // 检查当前用户是否已经为该博客点赞
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(BLOG_LIKED_KEY + id, userId.toString());

        // 用户未点赞时的操作
        if (BooleanUtil.isTrue(isMember)){
            // 在数据库中将点赞数加1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 如果更新成功，则将用户ID添加到Redis的点赞集合中
            if (isSuccess){
                stringRedisTemplate.opsForSet().add(BLOG_LIKED_KEY + id, userId.toString());
            }
        }else {
            // 在数据库中将点赞数减1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 如果更新成功，则从Redis的点赞集合中移除用户ID
            stringRedisTemplate.opsForSet().remove(BLOG_LIKED_KEY + id, userId.toString());
        }
        // 返回操作成功的结果
        return Result.ok();
    }


    /**
     * 查询博客用户信息。
     * 该方法通过博客对象中的用户ID，查询用户服务以获取用户信息，并将用户的昵称和图标更新到博客对象中。
     *
     * @param blog 博客对象，包含需要查询用户的ID。
     */
    private void queryBlogUser(Blog blog) {
        // 根据博客中的用户ID获取用户信息
        Long userId = blog.getUserId();
        User user = userService.getById(userId);

        // 更新博客信息，设置用户的昵称和图标
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

}
