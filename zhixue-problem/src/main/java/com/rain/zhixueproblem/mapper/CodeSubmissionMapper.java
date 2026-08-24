package com.rain.zhixueproblem.mapper;

import com.rain.zhixueproblem.entity.CodeSubmission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface CodeSubmissionMapper {
    int insert(CodeSubmission submission);
    
    CodeSubmission selectById(@Param("id") Long id);
    
    List<CodeSubmission> selectByProblemId(@Param("problemId") Long problemId);
    
    List<CodeSubmission> selectByUserId(@Param("userId") Long userId);
    
    List<CodeSubmission> selectList(Map<String, Object> params);
    
    int countByProblemId(@Param("problemId") Long problemId);
    
    int countAcceptedByProblemId(@Param("problemId") Long problemId);
}
