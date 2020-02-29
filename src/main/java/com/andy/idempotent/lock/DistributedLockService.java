package com.andy.idempotent.lock;


/**
 * 分布式锁接口
 * @author andy
 *
 */
public interface DistributedLockService {

    public <T>T doWithLock(String bizLockName,LockCallback<T> lockCallback);
    
    public interface LockCallback<T>{
        /**
         * 锁定期间用户要执行的业务逻辑
         * @throws Throwable 
         */
        public T doBiz() throws Throwable;
    }
}
