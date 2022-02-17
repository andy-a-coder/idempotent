package com.andy.idempotent.service;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.andy.idempotent.error.CommonErrorEnum;
import com.andy.idempotent.error.IdempotentException;
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
    private ValueOperations<String, String> redisStringOps;
    
    @Value("${idempontent.db-enabled:false}")
    private boolean dbEnabled;

    // 幂等锁的key定义
    public static final String IDEMPOTENT_LOCK = "idempotent:prjName:%s:sign:%s";

    // redis的幂等记录key
    public static final String IDEMPOTENT_REDIS_KEY = "idempotent:sign:%s";

    // redis的幂等默认缓存有效期1天（如果业务设置的幂等有效期比这个小，使用业务的）
    public static final Integer DEFAULT_IDEMPOTENT_MINUTES = 60 * 24;
    
    // BizColumn列的长度阀值，超过这个长度丢弃一部分
    public static final Integer BIZ_COLUMN_LENGTH_THRESHOLD  =  512;
    
    // requestParam列的长度阀值，超过这个长度丢弃一部分
    public static final Integer REQUEST_PARAM_LENGTH_THRESHOLD  = 1024;

    @Autowired
    private IdempotentRequestMapper idempotentRequestMapper;
    
    @Resource(name = "idempotentRedisLockRegistry")
    private RedisLockRegistry redisLockRegistry;

    @Override
    public <T> T handle(IdempotentCallback<T> idempotentCallback) throws Throwable {
        IdempotentContext context = new IdempotentContext();
        idempotentCallback.initContext(context);
        if (StringUtils.isBlank(context.getPrjName()))
            throw new IllegalArgumentException("prjName can not be null when calls method[IdempotentServiceImpl.handle]");
        if (StringUtils.isBlank(context.getInterfaceName()))
            throw new IllegalArgumentException("interfaceName can not be null when calls method[IdempotentServiceImpl.handle]");
        return doBizWithLock(idempotentCallback, context, getSign(context));
    }

    /**
     * 分布式锁控制及业务处理
     * 先从redis查询幂等记录，如果没有再从数据库查 正常情况查出来的status只有两种情况： 
     * 1-成功：直接返回结果或提示； 
     * 2-失败：重新调用业务方法； 
     * 如果出现了“0-新建”的，说明分布式锁出现了问题或者上一次的请求更新结果异常了(如：发布应用时服务器被强制杀掉重启可以导致该问题)，迫不得已，也重新调用业务方法，并更新幂等性记录。
     */
    private <T> T doBizWithLock(IdempotentCallback<T> idempotentCallback, IdempotentContext context, String sign) throws Throwable {
        if(redisLockRegistry == null)
            throw new RuntimeException("please confirm spring.redis configed");
        Lock lock = redisLockRegistry.obtain(String.format(IDEMPOTENT_LOCK, context.getPrjName(), sign));
        lock.lock();
        try {
            IdempotentRequest idempotentRequest = getIdempotentRequest(context, sign);
            if (idempotentRequest != null
                    && IdempotentRequest.STATUS_SUCCESS.equals(idempotentRequest.getStatus())
                    && (idempotentRequest.getValidEndTime() == null || idempotentRequest.getValidEndTime().compareTo(new Date()) > 0)) {
                return existValid(idempotentCallback, context, idempotentRequest);
            }
            return notExistValid(idempotentCallback, context, sign, idempotentRequest);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 不存在有效幂等记录的处理
     */
    private <T> T notExistValid(IdempotentCallback<T> idempotentCallback, IdempotentContext context, String sign, IdempotentRequest idempotentRequest) throws Throwable {
        idempotentRequest = createIdempotentRequest(context, sign, idempotentRequest);
        T result = callBizMethod(idempotentCallback, idempotentRequest);
        updateSuccessResult(context, sign, idempotentRequest, result);
        return result;
    }

    /**
     * 存在有效幂等记录的处理
     */
    private <T> T existValid(IdempotentCallback<T> idempotentCallback, IdempotentContext context, IdempotentRequest idempotentRequest) {
        if (IdempotentContext.RESPONSESTRATEGY_REPEAT_NOTICY == context.getResponseStrategy())
            throw new IdempotentException(CommonErrorEnum.IDEMPOTENT_REQUEST_EXIST);
        log.info("####### exist valid idempotent result, no need to call biz method, return directly, idempotentRequest={}", idempotentRequest);
        Type type = ((ParameterizedType) idempotentCallback.getClass().getGenericInterfaces()[0]).getActualTypeArguments()[0];
        if (Void.class.getName().equals(type.getTypeName()))
            return null;
        return JSON.parseObject(idempotentRequest.getResponse(), type);
    }

    /**
     * 获取已有的幂等请求记录
     */
    private IdempotentRequest getIdempotentRequest(IdempotentContext context, String sign) {
        IdempotentRequest idempotentRequest = getIdempotentRequestFromRedis(String.format(IDEMPOTENT_REDIS_KEY, sign));
        if (idempotentRequest == null && dbEnabled) {
            idempotentRequest = idempotentRequestMapper.getRequestBefore(sign);
            if (idempotentRequest != null)
                redisStringOps.set(String.format(IDEMPOTENT_REDIS_KEY, sign), JSON.toJSONString(idempotentRequest), getRedisIdempotentSeconds(context.getIdempotentMinutes()), TimeUnit.SECONDS);
        }
        return idempotentRequest;
    }

    /**
     * 创建幂等请求记录
     */
    private IdempotentRequest createIdempotentRequest(IdempotentContext context, String sign, IdempotentRequest idempotentRequest) {
        if (idempotentRequest == null 
                || IdempotentRequest.STATUS_FAIL.equals(idempotentRequest.getStatus())
                || idempotentRequest.getValidEndTime().compareTo(new Date()) < 0) {
            // 不存在或者上次请求失败，就直接插入请求记录，并调用业务方法
            try {
                idempotentRequest = new IdempotentRequest();
                idempotentRequest.setBizColumnValues(getValidBizColumnsValues(context.getBizColumnValues()));
                idempotentRequest.setPrjName(context.getPrjName());
                idempotentRequest.setInterfaceName(context.getInterfaceName());
                idempotentRequest.setRequestParam(getValidRequestParam(context.getRequestParam()));
                idempotentRequest.setSign(sign);
                idempotentRequest.setStatus(IdempotentRequest.STATUS_NEW);
                if (context.getIdempotentMinutes() != null && context.getIdempotentMinutes() > 0)
                    idempotentRequest.setValidEndTime(DateUtils.addMinutes(new Date(), context.getIdempotentMinutes()));
                if (dbEnabled)
                    idempotentRequestMapper.insert(idempotentRequest);
            } catch (Throwable e) {
                log.error("####### fail when add idempotentRequest, idempotentRequest={}", idempotentRequest, e);
                // 创建幂等记录时还没有调用业务逻辑，如果出现异常则抛出
                throw new IdempotentException(CommonErrorEnum.SAVE_IDEMPONTENT_REQUEST_FAIL ,e);
            }
        } else {
            // 状态为0-新建，这种情况，如果出现，记录日志，下边再次调用业务方法，更新原请求记录即可
            log.warn("####### abnormal idempotent record {}", idempotentRequest);
        }
        return idempotentRequest;
    }

    /**
     * 调用实际的业务方法
     */
    private <T> T callBizMethod(IdempotentCallback<T> idempotentCallback, IdempotentRequest idempotentRequest) throws Throwable {
        T result = null;
        try {
            result = idempotentCallback.execute();
        } catch (Throwable e) {
            // 更新请求状态为“失败”
            log.warn("####### fail when execute biz method, idempotentRequest={}", idempotentRequest);
            if (dbEnabled && idempotentRequest.getId() != null)
                idempotentRequestMapper.updateStatusByPrimaryKey(idempotentRequest.getId(), idempotentRequest.getStatus(), IdempotentRequest.STATUS_FAIL);
            throw e;
        }
        return result;
    }

    /**
     * 更新成功的请求结果
     */
    private <T> void updateSuccessResult(IdempotentContext context, String sign, IdempotentRequest idempotentRequest, T result) {
        try {
            // 更新请求状态为“成功”
            idempotentRequest.setResponse(JSON.toJSONString(result));
            if (dbEnabled && idempotentRequest.getId() != null) {
                idempotentRequestMapper.updateRequestResult(idempotentRequest.getId(), idempotentRequest.getStatus(), IdempotentRequest.STATUS_SUCCESS, idempotentRequest.getResponse());
            }
            idempotentRequest.setStatus(IdempotentRequest.STATUS_SUCCESS);
            // 将成功的请求记录放入redis
            redisStringOps.set(String.format(IDEMPOTENT_REDIS_KEY, sign), JSON.toJSONString(idempotentRequest), getRedisIdempotentSeconds(context.getIdempotentMinutes()), TimeUnit.SECONDS);
        } catch (Throwable e) {
            // 更新幂等记录的时候，已经调用完了正常业务逻辑，如果出现异常只打印log，不能影响正常业务逻辑
            log.error("####### fail when update idempotentRequest, idempotentRequest={}", idempotentRequest, e);
        }
    }

    /**
     * 获取redis中的幂等记录
     */
    private IdempotentRequest getIdempotentRequestFromRedis(String sign) {
        String redisIdemptObj = redisStringOps.get(sign);
        if (StringUtils.isNotBlank(redisIdemptObj))
            return JSON.parseObject(redisIdemptObj, IdempotentRequest.class);
        return null;
    }

    /**
     * 处理幂等参数请求，如果太长，在存库的时候做截取
     */
    protected Map<String, Object> getValidRequestParam(Map<String, Object> requestParam) {
        if(requestParam == null)
            return null;
        String reqeustParamJson = JSON.toJSONString(requestParam);
        if(reqeustParamJson.length() > REQUEST_PARAM_LENGTH_THRESHOLD) {
            Map<String,Object> trimedParams  = new HashMap<String, Object>();
            trimedParams.put("trimmedValue", reqeustParamJson.substring(0, 64));
            return trimedParams;
        }
        return requestParam;
    }

    /**
     * 处理幂等参数列，如果太长，在存库的时候做截取
     */
    protected String getValidBizColumnsValues(String bizColumnValues) {
        if(!StringUtils.isBlank(bizColumnValues) && bizColumnValues.length() > BIZ_COLUMN_LENGTH_THRESHOLD) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("trimmedValue", bizColumnValues.substring(0, 64));
            return jsonObject.toJSONString();
        }
        return bizColumnValues;
    }

    /**
     * 将有效期有分钟转化为秒(如果没启用数据库，则在redis里的时间使用业务的幂等有效期设置；否则在redis里的时间最大是DEFAULT_IDEMPOTENT_MINUTES * 60)
     */
    private Integer getRedisIdempotentSeconds(Integer idempotentMinutes) {
        if ((idempotentMinutes > 0 && !dbEnabled) || (idempotentMinutes > 0 && idempotentMinutes < DEFAULT_IDEMPOTENT_MINUTES))
            return idempotentMinutes * 60;
        else
            return DEFAULT_IDEMPOTENT_MINUTES * 60;
    }

    /**
     * 获取请求参数指纹
     */
    private String getSign(IdempotentContext context) {
        try {
            return DigestUtils.md5DigestAsHex(String.format("prjName:%s:interfaceName:%s:bizColumnValues:%s", context.getPrjName(), context.getInterfaceName(), context.getBizColumnValues()).getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            log.error("####### get idempotent sign error");
            throw new RuntimeException("####### get idempotent sign error", e);
        }
    }
}
