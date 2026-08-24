package com.rain.zhixueproblem.service;

import com.rain.zhixueproblem.entity.ZhixueProblem;
import com.rain.zhixueproblem.entity.ZhixueProblemSample;

import java.util.List;
import java.util.Map;

public interface ZhixueProblemService {
    Map<String, Object> getProblemList(Integer page, Integer size, String difficulty, Long categoryId, Long tagId, String problemType, String search, String sort, Long userId);

    Map<String, Object> getAdminProblemList(Integer page, Integer size, String difficulty, Long categoryId, String problemType, String search, Boolean isPublic);

    ZhixueProblem getProblemDetail(Long id, Long userId);

    ZhixueProblem createProblem(ZhixueProblem problem, List<ZhixueProblemSample> samples, List<Long> tagIds);

    ZhixueProblem updateProblem(Long id, ZhixueProblem problem, List<ZhixueProblemSample> samples, List<Long> tagIds);

    void deleteProblem(Long id);

    void batchDeleteProblems(List<Long> ids);

    List<ZhixueProblem> findProblemsForRecommend(Long userId, String difficulty, String knowledgePoint, String strategy, boolean excludeSolved, int limit, Long categoryId);

    Map<String, Object> getUserProblemStatus(Long userId);

    Map<String, Object> getUserWrongQuestions(Long userId);
}
