package com.rain.zhixueproblem.mapper;

import com.rain.zhixueproblem.entity.ZhixueSubmission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ZhixueSubmissionMapper {
    int insert(ZhixueSubmission zhixueSubmission);

    int updateById(ZhixueSubmission zhixueSubmission);

    ZhixueSubmission selectById(@Param("id") Long id);

    List<ZhixueSubmission> selectList(Map<String, Object> params);

    int countList(Map<String, Object> params);

    int countByProblemId(@Param("problemId") Long problemId);

    int countAcceptedByProblemId(@Param("problemId") Long problemId);

    int countByUserId(@Param("userId") Long userId);

    int countAcceptedByUserId(@Param("userId") Long userId);
}
