package com.rain.zhixueai.dispatcher.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rain.zhixueai.dispatcher.entity.InstanceHealthStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InstanceHealthStatusMapper extends BaseMapper<InstanceHealthStatus> {
    
    @Select("SELECT * FROM model_instance_health WHERE instance_id = #{instanceId}")
    InstanceHealthStatus findByInstanceId(@Param("instanceId") Long instanceId);
    
    @Update("UPDATE model_instance_health SET consecutive_failures = 0, consecutive_successes = consecutive_successes + 1, last_success_time = NOW(), updated_at = NOW() WHERE instance_id = #{instanceId}")
    int recordSuccess(@Param("instanceId") Long instanceId);
    
    @Update("UPDATE model_instance_health SET consecutive_failures = consecutive_failures + 1, consecutive_successes = 0, last_failure_time = NOW(), last_error_message = #{errorMessage}, updated_at = NOW() WHERE instance_id = #{instanceId}")
    int recordFailure(@Param("instanceId") Long instanceId, @Param("errorMessage") String errorMessage);
}
