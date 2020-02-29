package com.andy.idempotent.model;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import lombok.Data;

/**
 * 幂等请求信息
 * @author andy
 *
 */
@Data
public class IdempotentRequest {

    // 请求状态(0-新建;1-成功;2-失败)
    public static final Integer STATUS_NEW = 0;
    public static final Integer STATUS_SUCCESS = 1;
    public static final Integer STATUS_FAIL = 2;

    private Long id;
    // 项目名称
    private String prjName;
    // 项目下的接口唯一标志
    private String interfaceName;
    // 请求参数
    private Map<String, Object> requestParam;
    // 请求结果
    private String response;
    // 唯一确定一次请求的字段值串
    private String bizColumnValues;
    // 唯一业务字段串签名
    private String sign;
    // 请求状态(0-新建;1-成功;2-失败)
    private Integer status;
    // 有效截止时间（如果有值，超过这个时间，允许非幂等）
    private Date validEndTime;
    // 创建时间
    private Date createTime;
    // 修改时间
    private Date updateTime;

    public Map<String, Object> getRequestParam() {
        if(requestParam == null)
            requestParam = new HashMap<>();
        return requestParam;
    }
}
