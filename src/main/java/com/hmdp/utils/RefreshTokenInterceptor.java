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

    /**
     * 在请求处理之前进行拦截。
     * 主要用于处理请求时的token验证，如果验证通过，则允许请求继续；
     * 如果验证不通过，则直接放行请求。
     *
     * @param request  HttpServletRequest对象，代表客户端的请求
     * @param response HttpServletResponse对象，用于向客户端发送响应
     * @param handler  将要执行的处理器对象
     * @return boolean 返回值表示是否允许继续处理该请求。true表示继续，false表示中断。
     * @throws Exception 抛出异常的处理
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        log.info("RefreshTokenInterceptor拦截到请求：{}",uri);
        // 获取和打印更多路径信息
        StringBuffer url = request.getRequestURL();
        String queryString = request.getQueryString();
        String contextPath = request.getContextPath();
        String servletPath = request.getServletPath();
        String pathInfo = request.getPathInfo();

        log.info("完整URL: {}", url);
        if (queryString != null) {
            log.info("查询字符串: {}", queryString);
        }
        log.info("上下文路径: {}", contextPath);
        log.info("Servlet路径: {}", servletPath);
        if (pathInfo != null) {
            log.info("路径信息: {}", pathInfo);
        }
        // 打印所有请求头
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            log.info("Header: {} = {}", headerName, request.getHeader(headerName));
        }
        // 从请求头中获取token
        String token = request.getHeader("authorization");
        log.info("RefreshTokenInterceptor----token：{}",token);
        if (StrUtil.isBlank(token)){
            log.info("令牌不存在1，RefreshTokenInterceptor已放行请求：{}",uri);
            return true; // 令牌不存在或为空，直接放行
        }

        // 基于token从Redis获取用户信息
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        // 判断用户信息是否存在
        if (userMap.isEmpty()){
            log.info("令牌不存在2，RefreshTokenInterceptor已放行请求：{}",uri);
            return true; // 用户信息不存在，放行
        }

        // 将获取到的用户信息转换为UserDTO，并保存到ThreadLocal中以供后续使用
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);
        // 刷新token的有效期
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
        log.info("用户已登录，RefreshTokenInterceptor已放行请求，用户为{}",userDTO.getId());
        return true; // 验证通过，放行请求
    }


    /**
     * 在处理完成后执行的回调方法。
     * 该方法用于在请求处理完成后，从线程中移除用户信息。
     *
     * @param request 本次请求的HttpServletRequest对象
     * @param response 本次请求的HttpServletResponse对象
     * @param handler 处理本次请求的处理器对象
     * @param ex 在处理过程中抛出的异常，如果处理过程中没有异常则为null
     * @throws Exception 如果在清理过程中发生异常，则抛出Exception
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 从线程中移除用户信息，确保用户信息在请求完成后被清理
        UserHolder.removeUser();
    }

}
