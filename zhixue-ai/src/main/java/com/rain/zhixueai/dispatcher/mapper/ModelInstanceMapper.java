package com.rain.zhixueai.dispatcher.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rain.zhixueai.dispatcher.entity.ModelInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ModelInstanceMapper extends BaseMapper<ModelInstance> {
    
    @Select("SELECT * FROM model_instance WHERE group_id = #{groupId} ORDER BY id ASC")
    List<ModelInstance> findByGroupId(@Param("groupId") Long groupId);
    
    @Select("SELECT * FROM model_instance WHERE group_id = #{groupId} AND LOWER(status) = 'healthy'")
    List<ModelInstance> findHealthyByGroupId(@Param("groupId") Long groupId);
    
    @Select("SELECT * FROM model_instance WHERE LOWER(status) = 'healthy'")
    List<ModelInstance> findAllHealthy();
    
    @Update("UPDATE model_instance SET current_connections = current_connections + 1 WHERE id = #{id}")
    int incrementConnections(@Param("id") Long id);
    
    @Update("UPDATE model_instance SET current_connections = GREATEST(0, current_connections - 1) WHERE id = #{id}")
    int decrementConnections(@Param("id") Long id);

    @Select("SELECT COALESCE(SUM(current_connections), 0) FROM model_instance")
    int sumAllCurrentConnections();
}
