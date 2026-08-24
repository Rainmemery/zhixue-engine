package com.rain.zhixueadmin.mapper.stats.learning;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface LearningStatsMapper {

    Long countActiveLearningPaths();

    Long countCompletedLearningPaths();

    Long countTotalLearningSteps();

    Double calculateAverageProgress();

    Long countTotalLearningRecords();

    Map<String, Long> getLearningStats();
}
