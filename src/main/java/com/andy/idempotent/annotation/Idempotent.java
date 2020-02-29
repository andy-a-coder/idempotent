package com.andy.idempotent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 供幂等配置使用的注解
 * @author andy
 *
 */
@Target({ElementType.METHOD,ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    /**
     * 服务名称（默认取spring.application.name定义的名称）
     */
    String prjName() default "";
    
    /**
     * 接口名称（默认取'类名.方法名'）
     */
    String interfaceName() default "";
    
    /**
     * 唯一确定一次请求的参数集合
     */
    String[] idempotentColumns() default "";
    
    /**
     * 请求参数是否只记录幂等字段（有些参数如果记录下来比较大，比如：文件对象，可以选择只记录幂等字段）
     */
    boolean idempotentParamOnly() default false;
    
    /**
     * 幂等有效期时间（单位分钟，默认0-长期有效）
     */
    int idempotentMinutes() default 0;
    
    /**
     * 响应策略（0-返回上次的请求结果（默认）；1-重复请求提醒）
     */
    int responseStrategy() default 0;
}