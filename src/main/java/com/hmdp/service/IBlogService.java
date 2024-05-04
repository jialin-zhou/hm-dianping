package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author jialin.zhou
 */
public interface IBlogService extends IService<Blog> {


    Result queryBlogById(Long id);

    Result queryHotBlog(Integer current);

    Result updateBlogLiked(Long id);

    Result queryBlogLikes(Long id);

    Result saveBlog(Blog blog);

    Result queryBlogOfFollow(Long max, Integer offset);
}
