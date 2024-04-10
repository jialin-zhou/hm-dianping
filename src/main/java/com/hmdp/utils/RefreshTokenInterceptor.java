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
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        log.info("RefreshTokenInterceptor拦截到请求：{}",uri);
        // 获取和打印更多路径信息
//        StringBuffer url = request.getRequestURL();
//        String queryString = request.getQueryString();
//        String contextPath = request.getContextPath();
//        String servletPath = request.getServletPath();
//        String pathInfo = request.getPathInfo();
//
//        log.info("完整URL: {}", url);
//        if (queryString != null) {
//            log.info("查询字符串: {}", queryString);
//        }
//        log.info("上下文路径: {}", contextPath);
//        log.info("Servlet路径: {}", servletPath);
//        if (pathInfo != null) {
//            log.info("路径信息: {}", pathInfo);
//        }
//        // 打印所有请求头
//        Enumeration<String> headerNames = request.getHeaderNames();
//        while (headerNames.hasMoreElements()) {
//            String headerName = headerNames.nextElement();
//            log.info("Header: {} = {}", headerName, request.getHeader(headerName));
//        }

        // 1.获取请求头中的token
        String token = request.getHeader("authorization");
        log.info("RefreshTokenInterceptor----token：{}",token);
        if (StrUtil.isBlank(token)){
            log.info("令牌不存在1，RefreshTokenInterceptor已放行请求：{}",uri);
            return true;
        }


        // 2.基于token获取redis中的数据
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        // 3.判断用户是否存在
        if (userMap.isEmpty()){
            log.info("令牌不存在2，RefreshTokenInterceptor已放行请求：{}",uri);
            return true;
        }
        // 5.讲查询到的hash数据转为UserDTO数据
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 6.存在 保存用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);
        // 7.刷新token有效期
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
        log.info("用户已登录，RefreshTokenInterceptor已放行请求，用户为{}",userDTO.getId());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 在线程中删除用户
        UserHolder.removeUser();
    }
}
