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

    @Resource
    private IFollowService followService;
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
        if (UserHolder.getUser() == null) {
            // 如果当前用户未登录，则无法判断是否已点赞，直接返回
            return;
        }
        // 获取当前登录用户的ID
        Long userId = UserHolder.getUser().getId();
        Long id = blog.getId();
        // 检查该博客是否被当前用户点赞
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, userId.toString());
        // 设置blog对象的isLike属性来表示是否已点赞
        blog.setIsLike(score != null);
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
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, userId.toString());
        // 用户未点赞时的操作
        if (score == null){
            // 在数据库中将点赞数加1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 如果更新成功，则将用户ID添加到Redis的点赞集合中
            if (isSuccess){
                stringRedisTemplate.opsForZSet().add(BLOG_LIKED_KEY + id, userId.toString(), System.currentTimeMillis());
            }
        }else {
            // 在数据库中将点赞数减1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 如果更新成功，则从Redis的点赞集合中移除用户ID
            stringRedisTemplate.opsForZSet().remove(BLOG_LIKED_KEY + id, userId.toString());
        }
        // 返回操作成功的结果
        return Result.ok();
    }



    /**
     * 查询指定博客的点赞前五名的用户信息。
     *
     * @param id 博客的ID，用于查询对应的点赞信息。
     * @return 返回一个Result对象，其中包含了点赞前五名的用户信息列表。如果没有任何点赞信息，则返回一个空列表。
     */
    @Override
    public Result queryBlogLikes(Long id) {
        // 从Redis中获取top5点赞用户ID
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 0, 4);
        if (top5 == null || top5.isEmpty()){
            // 没有点赞时返回空列表
            return Result.ok(Collections.emptyList());
        }
        // 将字符串形式的ID转换为Long类型，并查询用户信息
        List<Long> idx = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", idx);
        // 根据用户ID列表查询并转换用户信息
        List<UserDTO> userDTOS = userService.query().in("id", idx)
                .last("ORDER BY FILED(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    /**
     * 保存博客文章
     * @param blog 探店笔记对象，包含文章内容等信息
     * @return 返回保存结果，成功返回笔记的ID，失败返回错误信息
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 获取当前登录的用户信息
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId()); // 设置博客作者的用户ID
        // 保存博客到数据库
        boolean isSuccess = save(blog);
        if (!isSuccess){
            // 如果保存失败，返回失败信息
            return Result.fail("新增笔记失败");
        }
        // 查询当前用户的所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        // 给所有粉丝推送新博客信息
        for (Follow follow : follows){
            Long userId = follow.getUserId(); // 粉丝的用户ID
            String key = FEED_KEY + userId; // 构造粉丝的动态消息键
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis()); // 将新博客ID添加到粉丝的动态消息队列中
        }
        // 返回成功保存的博客ID
        return Result.ok(blog.getId());
    }

    /**
     * 查询用户关注的博客列表
     * @param max 只返回得分不大于max的成员。如果想获取得分最小的成员, 使用负无穷大。
     * @param offset 返回结果的开始位置（偏移量）
     * @return 返回查询结果，包括博客列表、偏移量和最小时间戳
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 获取当前用户ID
        Long userId = UserHolder.getUser().getId();

        // 从Redis的有序集合中查询出符合条件的博客ID，按时间戳逆序排列
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(FEED_KEY + userId, 0, max, offset, 2);
        // 如果没有查询到数据，则直接返回空结果
        if (typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }
        // 解析查询结果，获取博客ID列表和最小时间戳
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1; // 计算偏移调整量
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples){
            ids.add(Long.valueOf(tuple.getValue()));
            long time = tuple.getScore().longValue();
            if (time == minTime){
                os ++;
            }else {
                minTime = time;
                os = 1;
            }
            minTime = tuple.getScore().longValue(); // 更新最小时间戳
        }
        // 根据博客ID查询博客详情，并按ID顺序排列
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        // 为每个博客查询相关用户信息和是否被点赞
        for (Blog blog : blogs){
            queryBlogUser(blog);
            isBlogLiked(blog);
        }
        // 封装查询结果并返回
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);
        return Result.ok(r);
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
