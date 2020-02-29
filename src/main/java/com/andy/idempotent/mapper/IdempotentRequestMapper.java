package com.andy.idempotent.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.andy.idempotent.model.IdempotentRequest;

public interface IdempotentRequestMapper {
    
    @Insert({
        "insert into idempotent_request(prj_name, interface_name, request_param, response, biz_column_values, sign, status, valid_end_time, create_time, update_time)",
        "values(",
        "#{prjName},",
        "#{interfaceName},",
        "#{requestParam,typeHandler=com.andy.idempotent.mybatis.JsonTypeHandler},",
        "#{response},",
        "#{bizColumnValues},",
        "#{sign},",
        "#{status},",
        "#{validEndTime},",
        "now(),now()",
        ")"
    })
    @Options(useGeneratedKeys=true, keyProperty="id")
    public Integer insert(IdempotentRequest idempotentRequest);

    @Select({
        "select id,status,response,valid_end_time",
        "from idempotent_request",
        "where sign=#{sign}",
        "order by id desc limit 1"
    })
    public IdempotentRequest getRequestBefore(@Param("sign") String sign);

    @Update({
        "update idempotent_request",
        "set status = #{newStatus}, update_time=now()",
        "where id=#{id} and status=#{oldStatus}"
    })
    public Integer updateStatusByPrimaryKey(@Param("id")Long id, @Param("oldStatus") Integer oldStatus, @Param("newStatus")Integer newStatus);

    @Update({
        "update idempotent_request",
        "set status = #{newStatus}, response=#{response}, update_time=now()",
        "where id=#{id} and status=#{oldStatus}"
    })
    public Integer updateRequestResult(@Param("id")Long id, @Param("oldStatus") Integer oldStatus, @Param("newStatus")Integer newStatus, @Param("response")String response);
    
}
