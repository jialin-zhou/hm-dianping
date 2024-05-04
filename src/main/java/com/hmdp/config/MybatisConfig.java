package com.hmdp.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisConfig {
    /**
     * 创建并配置MybatisPlusInterceptor Bean。
     * 这个方法初始化一个MybatisPlusInterceptor实例，并为其添加一个PaginationInnerInterceptor，
     * 专门用于处理MySQL数据库的分页请求。
     *
     * @return MybatisPlusInterceptor 返回配置好的MybatisPlusInterceptor实例。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加MySQL分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }



}
