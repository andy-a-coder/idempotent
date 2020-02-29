package com.andy.idempotent.annotation;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSON;
import com.andy.idempotent.model.IdempotentContext;
import com.andy.idempotent.service.IdempotentService;
import com.andy.idempotent.service.IdempotentService.IdempotentCallback;

import lombok.extern.slf4j.Slf4j;

/**
 * @author andy
 *
 */
@Aspect
@Component
@Order(-1)
@Slf4j
public class IdempotentInterceptor {

    @Autowired
    private IdempotentService idempotentService;

    // 默认项目名称
    @Value("${spring.application.name:}")
    public String defaultPrjName;

    @Around("@annotation(idempotent)")
    @SuppressWarnings("unchecked")
    public Object proceed(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        // 所有的参数值的数组
        Object[] args = joinPoint.getArgs();
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        // 所有参数名称的字符串数组
        String[] paramNames = methodSignature.getParameterNames();
        String[] idempotentColumns = idempotent.idempotentColumns();
        // 幂等增强
        Object retObj = idempotentService.handle(new IdempotentCallback<Object>() {

            @Override
            public void initContext(IdempotentContext context) {
                // 项目名称
                context.setPrjName(StringUtils.isBlank(idempotent.prjName()) ? defaultPrjName : idempotent.prjName());
                // 接口项目中接口的唯一标志
                context.setInterfaceName(StringUtils.isBlank(idempotent.interfaceName()) ? getMethodName(methodSignature) : idempotent.interfaceName());
                // 幂等有效期
                context.setIdempotentMinutes(idempotent.idempotentMinutes());
                // 响应策略
                context.setResponseStrategy(idempotent.responseStrategy());
                Map<String, Object> idempotentParamMap = new TreeMap<String, Object>();
                if (isIdempotentColumnsEmpty(idempotentColumns))
                    idempotentParamMap = generateAllParamJson(args, paramNames);
                else
                    idempotentParamMap = generateParamJson(args, paramNames, idempotentColumns);
                // 决定交易唯一性的字段值串
                context.setBizColumnValues(JSON.toJSONString(idempotentParamMap));
                // 请求参数
                if (idempotent.idempotentParamOnly())
                    context.setRequestParam(idempotentParamMap);
                else
                    context.setRequestParam(generateAllParamJson(args, paramNames));
            }

            @Override
            public Object execute() throws Throwable {
                return joinPoint.proceed();
            }
        });
        return JSON.parseObject(JSON.toJSONString(retObj), methodSignature.getReturnType());
    }

    /**
     * 判断IdempotentColumns是不是空的（如果里边的字符串都是空的，也认为是空的）
     */
    private boolean isIdempotentColumnsEmpty(String[] idempotentColumns) {
        return idempotentColumns == null || idempotentColumns.length == 0 || Arrays.asList(idempotentColumns).stream().allMatch(s -> StringUtils.isBlank(s));
    }

    /**
     * 获取业务方法名
     */
    private String getMethodName(MethodSignature methodSignature) {
        // 业务类名
        String className = methodSignature.getDeclaringTypeName();
        if (className.indexOf(".") > -1)
            className = className.substring(className.lastIndexOf(".") + 1);
        return String.format("%s:%s", className, methodSignature.getName());
    }

    /**
     * 获取所有的参数信息
     */
    private Map<String, Object> generateAllParamJson(Object[] args, String[] paramNames) {
        Map<String, Object> idempotentParamMap = new TreeMap<>();
        if (paramNames != null && paramNames.length > 0) {
            for (int i = 0; i < paramNames.length; i++) {
                idempotentParamMap.put(paramNames[i], args[i]);
            }
        }
        return idempotentParamMap;
    }

    /**
     * 获取指定的幂等参数信息
     */
    private Map<String, Object> generateParamJson(Object[] args, String[] paramNames, String[] idempotentColumns) {
        Map<String, Object> idempotentParamMap = new TreeMap<>();
        for (String idempotentColumn : idempotentColumns) {
            if (StringUtils.isNotBlank(idempotentColumn))
                idempotentParamMap.put(idempotentColumn, getParamAttributeValue(args, paramNames, idempotentColumn));
        }
        return idempotentParamMap;
    }

    /**
     * 根据expression从指定的参数中获取值 如果expression直接是参数名，则直接返回参数值，否则根据表达式调用相应参数名对应的对象中的值
     */
    private Object getParamAttributeValue(Object[] args, String[] parameterNames, String expression) {
        if (expression.indexOf(".") < 0)
            return args[ArrayUtils.indexOf(parameterNames, expression)];
        else {
            try {
                Object arg = args[ArrayUtils.indexOf(parameterNames, expression.substring(0, expression.indexOf(".")))];
                String fieldName = expression.substring(expression.indexOf(".") + 1);
                String typeName = getFieldTypeName(arg.getClass().getDeclaredFields(), fieldName);
                if ("boolean".equals(typeName) || "java.lang.Boolean".equals(typeName)) {
                    return getBooleanFiledValueTyName(fieldName, arg);
                } else {
                    return getFiledValueByName(fieldName, arg, "get");
                }
            } catch (Throwable e) {
                log.error("####### Parameters cannot be obtained. Check idempotent configuration,", e);
                throw new RuntimeException("Parameters cannot be obtained. Check idempotent configuration");
            }
        }
    }

    /**
     * 获取boolean field的值
     */
    private Object getBooleanFiledValueTyName(String fieldName, Object arg) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Object retObj = null;
        try {
            retObj = getFiledValueByName(fieldName, arg, "get");
        } catch (Exception e) {
            log.warn("####### get boolean value of field '{}' fail by 'get' Method, try to use 'is' method", fieldName);
        }
        if (retObj == null) {
            retObj = getFiledValueByName(fieldName, arg, "is");
        }
        return retObj;
    }

    /**
     * 获取指定field的值
     */
    private Object getFiledValueByName(String fieldName, Object arg, String prefix) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method method = arg.getClass().getMethod(prefix + firstUpcase(fieldName));
        method.setAccessible(true);
        return method.invoke(arg);
    }

    /**
     * 获取field类型名称
     */
    private String getFieldTypeName(Field[] fields, String fieldName) {
        for (Field field : fields) {
            if (field.getName().equals(fieldName))
                return field.getType().getName();
        }
        return null;
    }

    /**
     * 生成获取属性的方法名
     */
    private String firstUpcase(String property) {
        char[] chars = property.toCharArray();
        if (chars[0] >= 'a' && chars[0] <= 'z') {
            chars[0] = (char) (chars[0] - 32);
        }
        return new String(chars);
    }

}
