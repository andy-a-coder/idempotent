package com.andy.idempotent.service;

import com.andy.idempotent.model.IdempotentContext;

/**
 * 幂等处理的接口
 * @author andy
 *
 */
public interface IdempotentService {

    /**
     * 幂等性处理程序（调用方的实际业务逻辑在idempotentCallback.execute方法中执行）
     * 
     * @param context
     * @param idempotentCallback
     * @return T 实际业务方法的返回值
     * @throws Throwable 
     */
    public <T> T handle(IdempotentCallback<T> idempotentCallback) throws Throwable;

    /**
     * 幂等性服务回调接口
     *
     */
    public interface IdempotentCallback<T> {
        // 初始化上下文信息
        public void initContext(IdempotentContext context);
        // 用于执行用户的业务逻辑
        public T execute() throws Throwable;
    }

}
