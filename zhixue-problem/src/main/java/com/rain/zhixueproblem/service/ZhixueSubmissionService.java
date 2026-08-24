package com.rain.zhixueproblem.service;

import com.rain.zhixueproblem.entity.ZhixueSubmission;

import java.util.Map;

public interface ZhixueSubmissionService {
    Map<String, Object> getSubmissionList(Integer page, Integer size, Long problemId, Long userId, String language, String status);

    ZhixueSubmission getSubmissionById(Long id);

    ZhixueSubmission createSubmission(ZhixueSubmission submission);

    void updateSubmission(ZhixueSubmission submission);

    Map<String, Object> getUserSubmissionStats(Long userId);

    Map<String, Object> getProblemSubmissionStats(Long problemId);
}
