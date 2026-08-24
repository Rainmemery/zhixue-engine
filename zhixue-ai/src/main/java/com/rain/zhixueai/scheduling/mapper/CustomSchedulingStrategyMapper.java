package com.rain.zhixueai.scheduling.mapper;

import com.rain.zhixueai.scheduling.entity.CustomSchedulingStrategy;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CustomSchedulingStrategyMapper {
    
    @Select("SELECT * FROM custom_scheduling_strategy ORDER BY priority DESC, created_at DESC")
    List<CustomSchedulingStrategy> findAll();
    
    @Select("SELECT * FROM custom_scheduling_strategy WHERE enabled = true ORDER BY priority DESC")
    List<CustomSchedulingStrategy> findAllEnabled();
    
    @Select("SELECT * FROM custom_scheduling_strategy WHERE id = #{id}")
    CustomSchedulingStrategy findById(@Param("id") Long id);
    
    @Select("SELECT * FROM custom_scheduling_strategy WHERE strategy_name = #{strategyName}")
    CustomSchedulingStrategy findByName(@Param("strategyName") String strategyName);
    
    @Insert("INSERT INTO custom_scheduling_strategy (strategy_name, strategy_description, preferred_model, " +
            "fallback_model, condition_json, priority, enabled, is_system, created_at, updated_at) " +
            "VALUES (#{strategyName}, #{strategyDescription}, #{preferredModel, typeHandler=com.rain.zhixueai.mapper.handler.AiModelTypeHandler}, " +
            "#{fallbackModel, typeHandler=com.rain.zhixueai.mapper.handler.AiModelTypeHandler}, " +
            "#{condition, typeHandler=com.rain.zhixueai.scheduling.mapper.handler.StrategyConditionHandler}, " +
            "#{priority}, #{enabled}, #{isSystem}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CustomSchedulingStrategy strategy);
    
    @Update("UPDATE custom_scheduling_strategy SET strategy_name = #{strategyName}, " +
            "strategy_description = #{strategyDescription}, " +
            "preferred_model = #{preferredModel, typeHandler=com.rain.zhixueai.mapper.handler.AiModelTypeHandler}, " +
            "fallback_model = #{fallbackModel, typeHandler=com.rain.zhixueai.mapper.handler.AiModelTypeHandler}, " +
            "condition_json = #{condition, typeHandler=com.rain.zhixueai.scheduling.mapper.handler.StrategyConditionHandler}, " +
            "priority = #{priority}, enabled = #{enabled}, is_system = #{isSystem}, updated_at = #{updatedAt} " +
            "WHERE id = #{id}")
    int update(CustomSchedulingStrategy strategy);
    
    @Delete("DELETE FROM custom_scheduling_strategy WHERE id = #{id} AND is_system = false")
    int delete(@Param("id") Long id);
    
    @Update("UPDATE custom_scheduling_strategy SET enabled = #{enabled} WHERE id = #{id}")
    int updateEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled);
}
