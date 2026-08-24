package com.rain.zhixueproblem.service;

import com.rain.zhixueproblem.entity.ZhixueProblemTestcase;

import java.util.List;

public interface TestcaseService {
    List<ZhixueProblemTestcase> getTestcasesByProblemId(Long problemId);

    ZhixueProblemTestcase getTestcaseById(Long id);

    ZhixueProblemTestcase createTestcase(ZhixueProblemTestcase testcase);

    void updateTestcase(ZhixueProblemTestcase testcase);

    void deleteTestcase(Long id);

    void batchDeleteTestcases(List<Long> ids);

    void deleteTestcasesByProblemId(Long problemId);
}
