package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        log.info("LoginInterceptor拦截到请求：{}",uri);

        if(UserHolder.getUser() == null){
            //说明用户未登录,直接拦截
            log.info("用户未登录，LoginInterceptor未放行请求{}",uri);
            response.setStatus(401);
            return false;
        }

        log.info("LoginInterceptor已放行请求：{}",uri);
        //说明用户已登录，直接放行
        return true;
    }
}
