package com.rain.zhixueproblem.mapper;

import com.rain.zhixueproblem.entity.ZhixueTestcaseResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ZhixueTestcaseResultMapper {
    int insert(ZhixueTestcaseResult zhixueTestcaseResult);

    List<ZhixueTestcaseResult> selectBySubmissionId(@Param("submissionId") Long submissionId);

    int batchInsert(@Param("list") List<ZhixueTestcaseResult> list);
}
