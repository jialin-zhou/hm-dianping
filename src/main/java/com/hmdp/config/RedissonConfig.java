package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration // 标识为配置类
public class RedissonConfig {

    /**
     * 创建并返回一个Redisson客户端实例。
     * 这个方法没有参数。
     *
     * @return RedissonClient - Redisson客户端对象，用于与Redis服务器进行交互。
     */
    @Bean
    public RedissonClient redissonClient(){
        // 初始化Redisson配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://172.27.212.83:6379").setPassword("123321");
        // 根据配置创建Redisson客户端
        return Redisson.create(config);
    }
}

