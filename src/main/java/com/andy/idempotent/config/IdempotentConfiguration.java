package com.andy.idempotent.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.integration.redis.util.RedisLockRegistry;

@Configuration
@MapperScan(basePackages = { "com.andy.idempotent.mapper" })
@ComponentScan(basePackages = { 
        "com.andy.idempotent.service" ,
        "com.andy.idempotent.annotation"})
public class IdempotentConfiguration {

    @Bean("idempotentRedisLockRegistry")
    @ConditionalOnProperty("spring.redis.host")
    public RedisLockRegistry redisLockRegistry(RedisConnectionFactory redisConnectionFactory) {
        return new RedisLockRegistry(redisConnectionFactory, "idempotent-lock");
    }
}
