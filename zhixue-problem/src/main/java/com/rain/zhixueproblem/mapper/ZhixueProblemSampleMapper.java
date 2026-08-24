package com.rain.zhixueproblem.mapper;

import com.rain.zhixueproblem.entity.ZhixueProblemSample;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ZhixueProblemSampleMapper {
    int insert(ZhixueProblemSample zhixueProblemSample);

    int deleteByProblemId(@Param("problemId") Long problemId);

    List<ZhixueProblemSample> selectByProblemId(@Param("problemId") Long problemId);
}
