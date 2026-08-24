package com.rain.zhixueproblem.mapper;

import com.rain.zhixueproblem.entity.Problem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProblemMapper {
    int insert(Problem problem);
    
    int updateById(Problem problem);
    
    int deleteById(@Param("id") Long id);
    
    Problem selectById(@Param("id") Long id);
    
    List<Problem> selectList(Map<String, Object> params);
    
    int countList(Map<String, Object> params);
    
    int incrementSolvedCount(@Param("id") Long id);
    
    int updateAcceptanceRate(@Param("id") Long id, @Param("rate") Double rate);
}
