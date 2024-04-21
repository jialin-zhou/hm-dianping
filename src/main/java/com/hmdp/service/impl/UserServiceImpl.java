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
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

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
        return Result.ok(token);
    }

    /**
     * 用户签到方法。
     * 无参数。
     * 返回值：操作结果，成功返回ok。
     */
    @Override
    public Result sign() {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2. 获取当前日期时间
        LocalDateTime now = LocalDateTime.now();
        // 3. 拼接签到key，格式为：前缀:用户ID_年月
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4. 获取当前是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5. 在Redis中为当天的签到位置设置为1，表示已签到
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }


    /**
     * 计算当前用户本月的签到天数。
     * 无需参数。
     * @return Result对象，其中包含本月的签到天数。如果用户未签到，则返回0。
     */
    @Override
    public Result signCount() {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2. 获取当前日期
        LocalDateTime now = LocalDateTime.now();
        // 3. 拼接用于Redis存储的key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4. 获取当前日期在本月的天数
        int dayOfMonth = now.getDayOfMonth();
        // 5. 从Redis获取截止到今天的签到记录
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            // 没有签到记录，返回0
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            // 没有签到，返回0
            return Result.ok(0);
        }
        // 6. 计算签到天数
        int count = 0;
        while (true) {
            // 6.1. 判断最后一个bit位是否为1，代表已签到
            if ((num & 1) == 0) {
                // 为0，未签到，结束循环
                break;
            } else {
                // 为1，已签到，计数器加1
                count++;
            }
            // 移动到下一个bit位
            num >>>= 1;
        }
        // 返回签到天数
        return Result.ok(count);
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
