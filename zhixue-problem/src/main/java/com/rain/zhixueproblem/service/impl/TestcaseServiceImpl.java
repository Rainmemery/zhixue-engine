package com.rain.zhixueproblem.service.impl;

import com.rain.zhixueproblem.entity.ZhixueProblemTestcase;
import com.rain.zhixueproblem.mapper.ZhixueProblemTestcaseMapper;
import com.rain.zhixueproblem.service.TestcaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TestcaseServiceImpl implements TestcaseService {

    private final ZhixueProblemTestcaseMapper zhixueProblemTestcaseMapper;

    @Override
    public List<ZhixueProblemTestcase> getTestcasesByProblemId(Long problemId) {
        return zhixueProblemTestcaseMapper.selectByProblemId(problemId);
    }

    @Override
    public ZhixueProblemTestcase getTestcaseById(Long id) {
        return zhixueProblemTestcaseMapper.selectById(id);
    }

    @Override
    public ZhixueProblemTestcase createTestcase(ZhixueProblemTestcase testcase) {
        zhixueProblemTestcaseMapper.insert(testcase);
        return testcase;
    }

    @Override
    public void updateTestcase(ZhixueProblemTestcase testcase) {
        zhixueProblemTestcaseMapper.updateById(testcase);
    }

    @Override
    public void deleteTestcase(Long id) {
        zhixueProblemTestcaseMapper.deleteById(id);
    }

    @Override
    public void batchDeleteTestcases(List<Long> ids) {
        for (Long id : ids) {
            zhixueProblemTestcaseMapper.deleteById(id);
        }
    }

    @Override
    public void deleteTestcasesByProblemId(Long problemId) {
        zhixueProblemTestcaseMapper.deleteByProblemId(problemId);
    }
}
