package com.rain.zhixueproblem.service;

import com.rain.zhixueproblem.entity.CodeSubmission;

import java.util.Map;

public interface CodeExecutionService {
    Map<String, Object> submitCode(Long problemId, Long userId, String code, String language);
    
    CodeSubmission getSubmissionById(Long id);
    
    Map<String, Object> getSubmissionList(Long problemId, Long userId, Integer page, Integer size);
}
