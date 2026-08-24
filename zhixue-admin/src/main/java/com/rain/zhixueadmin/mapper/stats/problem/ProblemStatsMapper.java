package com.rain.zhixueadmin.mapper.stats.problem;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface ProblemStatsMapper {

    Long countTotalProblems();

    Long countProblemsByDifficulty(@Param("difficulty") String difficulty);

    Long countTotalSubmissions();

    Map<String, Long> getProblemStats();
}
