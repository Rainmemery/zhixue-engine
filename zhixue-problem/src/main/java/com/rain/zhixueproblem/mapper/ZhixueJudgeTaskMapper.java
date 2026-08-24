package com.rain.zhixueproblem.mapper;

import com.rain.zhixueproblem.entity.ZhixueJudgeTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ZhixueJudgeTaskMapper {
    int insert(ZhixueJudgeTask zhixueJudgeTask);

    int updateById(ZhixueJudgeTask zhixueJudgeTask);

    ZhixueJudgeTask selectById(@Param("id") Long id);

    List<ZhixueJudgeTask> selectBySubmissionId(@Param("submissionId") Long submissionId);
}
