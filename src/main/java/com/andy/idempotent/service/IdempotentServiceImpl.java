package com.andy.idempotent.service;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import com.alibaba.fastjson.JSON;
import com.andy.idempotent.error.CommonErrorEnum;
import com.andy.idempotent.error.IdempotentException;
import com.andy.idempotent.lock.DistributedLockService;
import com.andy.idempotent.lock.DistributedLockService.LockCallback;
import com.andy.idempotent.mapper.IdempotentRequestMapper;
import com.andy.idempotent.model.IdempotentContext;
import com.andy.idempotent.model.IdempotentRequest;

/**
 * 幂等处理的实现
 * @author andy
 *
 */
@Service("idempotentService")
public class IdempotentServiceImpl implements IdempotentService {

    public static final Logger log = LoggerFactory.getLogger(IdempotentServiceImpl.class);

    @Resource(name = "stringRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    @Resource(name = "stringRedisTemplate")
    private ValueOperations<String, String> redisStringOps;

    // 幂等锁的key定义
    public static final String IDEMPOTENT_LOCK = "idempotent:prjName:%s:sign:%s";

    // redis的幂等记录key
    public static final String IDEMPOTENT_REDIS_KEY = "idempotent:sign:%s";

    // redis的幂等默认缓存有效期1天（如果业务设置的幂等有效期比这个小，使用业务的）
    public static final Integer DEFAULT_IDEMPOTENT_MINUTES = 60 * 24;

    @Autowired
    private IdempotentRequestMapper idempotentRequestMapper;

    @Autowired
    private DistributedLockService distributedLockService;

    private IdempotentRequest getIdempotentRequestFromRedis(String sign) {
        String redisIdemptObj = redisStringOps.get(sign);
        if (StringUtils.isNotBlank(redisIdemptObj))
            return JSON.parseObject(redisIdemptObj, IdempotentRequest.class);
        return null;
    }

    @Override
    public <T> T handle(IdempotentCallback<T> idempotentCallback) {

        IdempotentContext context = new IdempotentContext();
        idempotentCallback.initContext(context);
        if (StringUtils.isBlank(context.getPrjName()))
            throw new IllegalArgumentException("prjName can not be null when calls method[IdempotentServiceImpl.handle]");
        if (StringUtils.isBlank(context.getInterfaceName()))
            throw new IllegalArgumentException("interfaceName can not be null when calls method[IdempotentServiceImpl.handle]");
        String sign = getSign(context);
        // 使用分布式锁做控制，保证幂等签名一致的请求只有一个在执行
        return distributedLockService.doWithLock(String.format(IDEMPOTENT_LOCK, context.getPrjName(), sign), new LockCallback<T>() {

            @Override
            public T doBiz() throws Throwable {
                /**
                 * 先从redis查询幂等记录，如果没有再从数据库查 正常情况查出来的status只有两种情况： 
                 * 1-成功：直接返回结果或提示； 
                 * 2-失败：重新调用业务方法； 
                 * 如果出现了“0-新建”的，说明分布式锁出现了问题或者上一次的请求更新结果异常了(如：发布应用时服务器被强制杀掉重启可以导致该问题)，也重新调用业务方法，并更新幂等性记录。
                 */
                IdempotentRequest idempotentRequest = getIdempotentRequestFromRedis(String.format(IDEMPOTENT_REDIS_KEY, sign));
                if (idempotentRequest == null) {
                    idempotentRequest = idempotentRequestMapper.getRequestBefore(sign);
                    if (idempotentRequest != null)
                        redisStringOps.set(String.format(IDEMPOTENT_REDIS_KEY, sign), JSON.toJSONString(idempotentRequest), getRedisIdempotentSeconds(context.getIdempotentMinutes()), TimeUnit.SECONDS);
                }
                if (idempotentRequest != null
                        && IdempotentRequest.STATUS_SUCCESS.equals(idempotentRequest.getStatus())
                        && (idempotentRequest.getValidEndTime() == null || idempotentRequest.getValidEndTime().compareTo(new Date()) > 0)) {
                    // 根据策略，提示重复请求或返回上次的成功结果
                    if (IdempotentContext.RESPONSESTRATEGY_REPEAT_NOTICY == context.getResponseStrategy())
                        throw new IdempotentException(CommonErrorEnum.IDEMPOTENT_REQUEST_EXIST.code(), CommonErrorEnum.IDEMPOTENT_REQUEST_EXIST.message());
                    log.info("####### exist valid idempotent result, no need to call biz method, return directly, idempotentRequest={}", JSON.toJSONString(idempotentRequest));
                    Type type = ((ParameterizedType) idempotentCallback.getClass().getGenericInterfaces()[0]).getActualTypeArguments()[0];
                    if (Void.class.getName().equals(type.getTypeName()))
                        return null;
                    return JSON.parseObject(idempotentRequest.getResponse(), type);
                } else {
                    if (idempotentRequest == null || IdempotentRequest.STATUS_FAIL.equals(idempotentRequest.getStatus())) {
                        // 不存在或者上次请求失败，就直接插入请求记录，并调用业务方法
                        try {
                            idempotentRequest = new IdempotentRequest();
                            idempotentRequest.setBizColumnValues(context.getBizColumnValues());
                            idempotentRequest.setPrjName(context.getPrjName());
                            idempotentRequest.setInterfaceName(context.getInterfaceName());
                            idempotentRequest.setRequestParam(context.getRequestParam());
                            idempotentRequest.setSign(sign);
                            idempotentRequest.setStatus(IdempotentRequest.STATUS_NEW);
                            if (context.getIdempotentMinutes() != null && context.getIdempotentMinutes() > 0)
                                idempotentRequest.setValidEndTime(DateUtils.addMinutes(new Date(), context.getIdempotentMinutes()));
                            idempotentRequestMapper.insert(idempotentRequest);
                        } catch (Exception e) {
                            log.error("####### fail when add idempotentRequest, idempotentRequest={}", JSON.toJSONString(idempotentRequest), e);
                            // 创建幂等记录时还没有调用业务逻辑，如果出现异常则抛出
                            throw new RuntimeException(e);
                        }
                    } else {
                        // 状态为0-新建，这种情况，如果出现，记录日志，下边再次调用业务方法，更新原请求记录即可
                        log.warn("####### abnormal idempotent record {}", JSON.toJSONString(idempotentRequest));
                    }
                    T result = null;
                    try {
                        // 调用业务方法
                        result = idempotentCallback.execute();
                    } catch (Throwable e) {
                        // 更新请求状态为“失败”
                        log.warn("####### fail when execute biz method, idempotentRequest={}", JSON.toJSONString(idempotentRequest));
                        if (idempotentRequest.getId() != null)
                            idempotentRequestMapper.updateStatusByPrimaryKey(idempotentRequest.getId(), idempotentRequest.getStatus(), IdempotentRequest.STATUS_FAIL);
                        if (e instanceof RuntimeException)
                            throw e;
                        else
                            throw new RuntimeException(e);
                    }
                    try {
                        // 更新请求状态为“成功”
                        if (idempotentRequest.getId() != null) {
                            idempotentRequest.setResponse(JSON.toJSONString(result));
                            idempotentRequestMapper.updateRequestResult(idempotentRequest.getId(), idempotentRequest.getStatus(), IdempotentRequest.STATUS_SUCCESS, idempotentRequest.getResponse());
                            idempotentRequest.setStatus(IdempotentRequest.STATUS_SUCCESS);
                            // 将成功的请求记录放入redis
                            redisStringOps.set(String.format(IDEMPOTENT_REDIS_KEY, sign), JSON.toJSONString(idempotentRequest), getRedisIdempotentSeconds(context.getIdempotentMinutes()), TimeUnit.SECONDS);
                        }
                    } catch (Throwable e) {
                        // 更新幂等记录的时候，已经调用完了正常业务逻辑，如果出现异常只打印log，不能影响正常业务逻辑
                        log.error("####### fail when update idempotentRequest, idempotentRequest={}", idempotentRequest, e);
                    }
                    return result;
                }
            }
        });
    }

    private Integer getRedisIdempotentSeconds(Integer idempotentMinutes) {
        if (idempotentMinutes > 0 && idempotentMinutes < DEFAULT_IDEMPOTENT_MINUTES)
            return idempotentMinutes * 60;
        else
            return DEFAULT_IDEMPOTENT_MINUTES * 60;
    }

    private String getSign(IdempotentContext context) {
        try {
            return DigestUtils.md5DigestAsHex(String.format("prjName:%s:interfaceName:%s:bizColumnValues:%s", context.getPrjName(), context.getInterfaceName(), context.getBizColumnValues()).getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            log.error("####### get idempotent sign error");
            throw new RuntimeException("####### get idempotent sign error", e);
        }
    }
}
