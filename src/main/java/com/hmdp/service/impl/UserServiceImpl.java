package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码到指定手机
     *
     * @param phone 手机号码，用于发送验证码
     * @param session HttpSession对象，用于保持会话，本方法未使用该参数
     * @return Result对象，包含操作结果的状态和信息。如果手机号格式错误，返回错误信息；否则，返回操作成功的提示。
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号格式是否合法
        if (RegexUtils.isPhoneInvalid(phone)){
            // 手机号格式不合法时，返回错误信息
            return Result.fail("手机号格式错误!");
        }
        // 2. 生成6位数字验证码
        String code = RandomUtil.randomNumbers(6);
        // 3. 将验证码保存到Redis中，设置过期时间为2分钟
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 4. 打印日志，表示验证码发送成功
        log.debug("发送短信验证码成功，验证码：{}",code);
        // 5. 返回操作成功的Result对象
        return Result.ok();
    }


    /**
     * 用户登录接口
     * @param loginForm 登录表单，包含手机号和验证码
     * @param session HttpSession对象，用于登录状态管理
     * @return Result对象，表示登录结果，成功返回ok，失败返回错误信息
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号格式是否合法
        String phone  = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
            // 手机号格式不合法，返回错误信息
            return Result.fail("手机号格式错误!");
        }
        // 2. 从Redis中获取验证码并与输入的验证码进行校验
        Object cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if(cacheCode == null || !cacheCode.toString().equals(code)){
            // 验证码不匹配，返回错误信息
            return Result.fail("验证码错误!");
        }
        // 3. 根据手机号查询用户信息
        User user = query().eq("phone", phone).one();
        // 4. 判断用户是否存在，若不存在则创建新用户
        if (user == null){
            // 用户不存在，创建新用户
            user = createUserWithPhone(phone);
        }
        // 5. 保存用户信息到Redis中，用于登录状态管理
        // 5.1 生成登录令牌
        String token = UUID.randomUUID().toString(true);
        // 5.2 将用户对象转换为Map形式存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 5.3 存储用户信息到Redis，并设置过期时间
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.SECONDS);

        // 登录成功，返回成功信息
        return Result.ok();
    }


    /**
     * 使用电话号码创建用户。
     *
     * @param phone 用户的电话号码。
     * @return 创建完成的用户对象。
     */
    private User createUserWithPhone(String phone) {
        // 1.新建用户并设置电话号码和随机昵称
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 2.保存用户到数据库
        save(user);
        return user;
    }

}
