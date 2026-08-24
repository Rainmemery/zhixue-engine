package com.rain.zhixueproblem.mapper;

import com.rain.zhixueproblem.entity.ZhixueProblemCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ZhixueProblemCategoryMapper {
    int insert(ZhixueProblemCategory zhixueProblemCategory);

    int updateById(ZhixueProblemCategory zhixueProblemCategory);

    int deleteById(@Param("id") Long id);

    ZhixueProblemCategory selectById(@Param("id") Long id);

    List<ZhixueProblemCategory> selectAll();

    int updateProblemCount(@Param("id") Long id, @Param("problemCount") Integer problemCount);
}
