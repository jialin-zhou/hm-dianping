package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author jialin.zhou
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;


    /**
     * 保存博客文章
     *
     * @param blog 接收前端传来的博客对象，包含博客的全部信息
     * @return 返回操作结果，其中包含新保存的博客的ID
     */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        // 修改点赞数量
        // blogService.update().setSql("liked = liked + 1").eq("id", id).update();
        blogService.updateBlogLiked(id);
        return Result.ok();
    }


    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        return blogService.queryBlogLikes(id);
    }

    /**
     * 查询我的博客列表
     *
     * @param current 当前页码，默认为1
     * @return Result对象，包含当前页的博客列表
     */
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取当前登录的用户
        UserDTO user = UserHolder.getUser();
        // 根据用户ID进行查询，设置分页信息
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页的博客列表
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }


    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        return blogService.queryBlogById(id);
    }

    /**
     * 根据用户ID查询博客列表。
     *
     * @param current 当前页码，默认为1。
     * @param id 用户ID。
     * @return 返回博客列表的查询结果，包括当前页的博客记录。
     */
    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        // 根据用户ID查询博客，分页获取数据
        Page<Blog> page = blogService.query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 提取当前页的博客记录
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 查询关注者的博客
     *
     * @param max 上一次查询的最小时间戳 提供一个上界ID，查询从此ID之后（不包括此ID）的博客，用于分页查询
     * @param offset 查询偏移量，默认为0，用于分页查询
     * @return 返回查询到的博客结果，封装在Result对象中
     */
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(
            @RequestParam("lastId") Long max, @RequestParam(value = "offset", defaultValue = "0") Integer offset){
        // 调用blogService中的方法，查询关注者的博客
        return blogService.queryBlogOfFollow(max, offset);
    }



}
