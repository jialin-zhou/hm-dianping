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
    /**
     * 在请求处理之前进行拦截。用于判断用户是否登录。
     *
     * @param request  HttpServletRequest对象，代表客户端的请求
     * @param response HttpServletResponse对象，用于向客户端发送响应
     * @param handler  将要处理请求的处理器对象
     * @return 如果用户已登录，返回true，请求继续处理；如果用户未登录，返回false，请求被拦截
     * @throws Exception 抛出异常的处理
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        log.info("LoginInterceptor拦截到请求：{}",uri);

        if(UserHolder.getUser() == null){
            // 检查用户是否登录，未登录则拦截请求
            log.info("用户未登录，LoginInterceptor未放行请求{}",uri);
            response.setStatus(401); // 设置响应状态码为401，表示未授权
            return false;
        }

        log.info("LoginInterceptor已放行请求：{}",uri);
        // 用户已登录，放行请求
        return true;
    }

}
