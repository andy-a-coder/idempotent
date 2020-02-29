package com.andy.idempotent.lock;


import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;

/**
 * 基于redis的简便分布式锁实现
 * @author andy
 *
 */
@Service
public class RedisDistributedLockServiceImpl implements DistributedLockService{
    public static final Logger LOGGER = LoggerFactory.getLogger(RedisDistributedLockServiceImpl.class);
    
    public static final Integer REDISKEY_EXPIRE = 120;  // 默认过期时间2分钟

    @Resource(name = "stringRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    @Resource(name = "stringRedisTemplate")
    private ValueOperations<String, String> redisStringOps;

    @Override
    public <T> T doWithLock(String bizLockName,LockCallback<T> lockCallback){
        String redisKey = String.format("SimpleLock:lockName:%s", bizLockName);
        String redisValue = UUID.randomUUID().toString();
        try {
            // 通过设置redis中的key来抢锁（添加超时时间 避免死锁）
            while (!setNX(redisKey, redisValue ,REDISKEY_EXPIRE)) {
                LOGGER.info("have not got redis lock, redisKey={},sleep 100 milliseconds...", redisKey);
                TimeUnit.MILLISECONDS.sleep(100);
            }
            return lockCallback.doBiz();
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        } finally {
            try {
                // 执行完之后删除锁（比较value值，避免锁过期导致的其他线程持有锁被误删除）
                if(redisValue.equals(redisStringOps.get(redisKey)))
                    redisTemplate.delete(redisKey);
            } catch (Throwable e) {
                LOGGER.error("delete redis lock error, redisKey={}", redisKey, e);
            }
        }
    }
    private boolean setNX(String key, String value, Integer seconds) {
        return redisTemplate.execute((RedisCallback<Boolean>)connection-> {
                RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
                Boolean result = connection.set(serializer.serialize(key), serializer.serialize(value), Expiration.seconds(seconds), SetOption.SET_IF_ABSENT);
                return result==null?false:result;
        });
    }
}
