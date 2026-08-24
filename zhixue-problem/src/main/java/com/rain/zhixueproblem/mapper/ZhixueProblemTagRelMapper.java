package com.rain.zhixueproblem.mapper;

import com.rain.zhixueproblem.entity.ZhixueProblemTagRel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ZhixueProblemTagRelMapper {
    int insert(ZhixueProblemTagRel zhixueProblemTagRel);

    int deleteByProblemId(@Param("problemId") Long problemId);

    int deleteByProblemIdAndTagId(@Param("problemId") Long problemId, @Param("tagId") Long tagId);

    List<ZhixueProblemTagRel> selectByProblemId(@Param("problemId") Long problemId);

    List<ZhixueProblemTagRel> selectByTagId(@Param("tagId") Long tagId);

    int batchInsert(@Param("list") List<ZhixueProblemTagRel> list);
}
