package com.rain.zhixueai.dispatcher.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rain.zhixueai.dispatcher.entity.ModelGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 模型组数据访问接口
 * 
 * @author rain
 * @since 2026-04-19
 */
@Mapper
public interface ModelGroupMapper extends BaseMapper<ModelGroup> {
    
    @Select("SELECT * FROM model_group WHERE enabled = true ORDER BY priority DESC, id ASC")
    List<ModelGroup> findAllEnabled();
    
    @Select("SELECT * FROM model_group ORDER BY priority DESC, id ASC")
    List<ModelGroup> findAllOrderByPriority();
    
    @Select("SELECT * FROM model_group WHERE name = #{name}")
    ModelGroup findByName(@Param("name") String name);
    
    @Select("SELECT * FROM model_group WHERE is_default = true AND enabled = true ORDER BY priority DESC LIMIT 1")
    ModelGroup findDefaultGroup();
    
    @Select("SELECT COUNT(*) FROM model_instance WHERE group_id = #{groupId}")
    int countInstancesByGroupId(@Param("groupId") Long groupId);
    
    @Update("UPDATE model_group SET is_default = false WHERE is_default = true")
    void clearDefaultFlag();
}