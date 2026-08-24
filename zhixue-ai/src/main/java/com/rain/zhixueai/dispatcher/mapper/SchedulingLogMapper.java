package com.rain.zhixueai.dispatcher.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rain.zhixueai.dispatcher.entity.SchedulingLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SchedulingLogMapper extends BaseMapper<SchedulingLog> {
    
    @Select("SELECT * FROM scheduling_log WHERE group_id = #{groupId} ORDER BY created_at DESC LIMIT #{limit}")
    List<SchedulingLog> findByGroupId(@Param("groupId") Long groupId, @Param("limit") int limit);
    
    @Select("SELECT * FROM scheduling_log WHERE instance_id = #{instanceId} ORDER BY created_at DESC LIMIT #{limit}")
    List<SchedulingLog> findByInstanceId(@Param("instanceId") Long instanceId, @Param("limit") int limit);
    
    @Select("SELECT * FROM scheduling_log WHERE request_id = #{requestId}")
    SchedulingLog findByRequestId(@Param("requestId") String requestId);
    
    @Select("SELECT COUNT(*) FROM scheduling_log WHERE group_id = #{groupId} AND status = 'SUCCESS' AND created_at >= #{startTime}")
    int countSuccessByGroupIdSince(@Param("groupId") Long groupId, @Param("startTime") LocalDateTime startTime);
    
    @Select("SELECT COUNT(*) FROM scheduling_log WHERE group_id = #{groupId} AND status = 'FAILURE' AND created_at >= #{startTime}")
    int countFailureByGroupIdSince(@Param("groupId") Long groupId, @Param("startTime") LocalDateTime startTime);
    
    @Select("SELECT AVG(duration_ms) FROM scheduling_log WHERE instance_id = #{instanceId} AND status = 'SUCCESS' AND created_at >= #{startTime}")
    Double getAverageDurationByInstanceIdSince(@Param("instanceId") Long instanceId, @Param("startTime") LocalDateTime startTime);
}
