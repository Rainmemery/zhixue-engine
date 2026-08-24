package com.rain.zhixueproblem.mapper;

import com.rain.zhixueproblem.entity.ProblemCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProblemCategoryMapper {
    int insert(ProblemCategory category);
    
    int updateById(ProblemCategory category);
    
    int deleteById(@Param("id") Long id);
    
    ProblemCategory selectById(@Param("id") Long id);
    
    List<ProblemCategory> selectAll();
}
