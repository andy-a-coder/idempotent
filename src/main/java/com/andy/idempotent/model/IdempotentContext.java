package com.andy.idempotent.model;

import java.util.Map;
import java.util.TreeMap;

/**
 * 幂等性服务上下文
 * @author huangxiaohui
 *
 */
public class IdempotentContext {

    // 响应策略(0-返回上次的请求结果（默认）；1-重复请求提醒)
    public static final int RESPONSESTRATEGY_RETURN_LAST = 0;
    public static final int RESPONSESTRATEGY_REPEAT_NOTICY = 1;

    // 项目名称
    private String prjName;
    // 项目下的接口唯一标志
    private String interfaceName;
    // 响应策略(0-重复请求提醒；1-返回上次的请求结果（默认）)
    private Integer responseStrategy = RESPONSESTRATEGY_REPEAT_NOTICY;
    // 幂等有效时间（默认不设置，一直保持幂等）
    private Integer idempotentMinutes = 0;
    // 请求参数
    private Map<String, Object> requestParam;
    // 唯一确定一次请求的字段值串
    private String bizColumnValues;

    public String getPrjName() {
        return prjName;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public Integer getResponseStrategy() {
        return responseStrategy;
    }

    public Integer getIdempotentMinutes() {
        return idempotentMinutes;
    }

    public Map<String, Object> getRequestParam() {
        if (this.requestParam == null)
            this.requestParam = new TreeMap<>();
        return requestParam;
    }

    public void setRequestParam(Map<String, Object> requestParam) {
        this.requestParam = requestParam;
    }

    public void addParam(String key, Object value) {
        this.getRequestParam().put(key, value);
    }

    public void setPrjName(String prjName) {
        this.prjName = prjName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public void setResponseStrategy(Integer responseStrategy) {
        this.responseStrategy = responseStrategy;
    }

    public void setIdempotentMinutes(Integer idempotentMinutes) {
        this.idempotentMinutes = idempotentMinutes;
    }

    public String getBizColumnValues() {
        return bizColumnValues;
    }

    public void setBizColumnValues(String bizColumnValues) {
        this.bizColumnValues = bizColumnValues;
    }

}
