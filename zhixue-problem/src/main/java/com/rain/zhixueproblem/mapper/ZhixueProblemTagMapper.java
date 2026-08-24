package com.rain.zhixueproblem.mapper;

import com.rain.zhixueproblem.entity.ZhixueProblemTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ZhixueProblemTagMapper {
    int insert(ZhixueProblemTag zhixueProblemTag);

    int updateById(ZhixueProblemTag zhixueProblemTag);

    int deleteById(@Param("id") Long id);

    ZhixueProblemTag selectById(@Param("id") Long id);

    List<ZhixueProblemTag> selectAll();

    int updateProblemCount(@Param("id") Long id, @Param("problemCount") Integer problemCount);
}
