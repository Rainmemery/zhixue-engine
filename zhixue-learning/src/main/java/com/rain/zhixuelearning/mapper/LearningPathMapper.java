package com.rain.zhixuelearning.mapper;

import com.rain.zhixuelearning.entity.LearningPathSteps;
import com.rain.zhixuelearning.entity.LearningPaths;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface LearningPathMapper {
    int insert(LearningPaths path);
    
    int updateById(LearningPaths path);
    
    LearningPaths selectById(@Param("id") Long id);
    
    List<LearningPaths> selectByUserId(@Param("userId") Long userId);
    
    List<LearningPaths> selectByStatus(@Param("status") String status);
    
    List<LearningPaths> selectRecommended();
    
    int updateStatus(@Param("id") Long id, @Param("status") String status);
    
    int updateProgress(@Param("id") Long id, @Param("currentStep") Long currentStep, @Param("progressPercentage") Double progressPercentage);
}
