package com.rain.zhixueproblem.mapper;

import com.rain.zhixueproblem.entity.ZhixueProblemTestcase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ZhixueProblemTestcaseMapper {
    int insert(ZhixueProblemTestcase zhixueProblemTestcase);

    int deleteById(@Param("id") Long id);

    int deleteByProblemId(@Param("problemId") Long problemId);

    List<ZhixueProblemTestcase> selectByProblemId(@Param("problemId") Long problemId);

    ZhixueProblemTestcase selectById(@Param("id") Long id);

    int updateById(ZhixueProblemTestcase zhixueProblemTestcase);

    int batchInsert(@Param("list") List<ZhixueProblemTestcase> list);

    int countByProblemId(@Param("problemId") Long problemId);
}
