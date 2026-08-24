package com.rain.zhixuelearning.mapper;

import com.rain.zhixuelearning.entity.LearningPathSteps;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LearningPathStepMapper {
    int insert(LearningPathSteps step);
    
    int updateById(LearningPathSteps step);
    
    LearningPathSteps selectById(@Param("id") Long id);
    
    List<LearningPathSteps> selectByPathId(@Param("pathId") Long pathId);
}
