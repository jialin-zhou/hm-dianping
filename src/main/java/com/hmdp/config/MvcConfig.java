package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import java.util.Locale;

@Configuration // 表示这是一个配置类
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate; // 注入String类型的Redis模板

    /**
     * 配置拦截器。
     * @param registry 此方法用于注册拦截器，配置了两个拦截器：
     *                 1、登录拦截器，拦截除特定路径外的所有请求；
     *                 2、token刷新拦截器，拦截所有请求，用户自动刷新用户的token，保证用户的登录状态不失效
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 添加登录拦截器，拦截除特定路径外的所有请求
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/shop/**",       // 排除购物相关路径
                        "/voucher/**",    // 排除凭证相关路径
                        "/shop-type/**",  // 排除店铺类型相关路径
                        "/upload/**",     // 排除上传相关路径
                        "/blog/hot",      // 排除热门博客路径
                        "/user/code",     // 排除用户验证码路径
                        "/user/login"     // 排除用户登录路径
                ).order(1); // 设置拦截器执行顺序

        // 添加token刷新拦截器，拦截所有请求以实现token的自动刷新
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)) // 使用StringRedisTemplate进行token管理的拦截器
                .addPathPatterns("/**") // 拦截所有路径
                .order(0); // 设置拦截器执行顺序为最先
    }
}

