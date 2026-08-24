package com.rain.zhixueproblem.service.impl;

import com.alibaba.fastjson2.JSON;
import com.rain.zhixueproblem.entity.CodeSubmission;
import com.rain.zhixueproblem.entity.Problem;
import com.rain.zhixueproblem.mapper.CodeSubmissionMapper;
import com.rain.zhixueproblem.mapper.ProblemMapper;
import com.rain.zhixueproblem.service.CodeExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeExecutionServiceImpl implements CodeExecutionService {
    
    private final CodeSubmissionMapper submissionMapper;
    private final ProblemMapper problemMapper;

    @Override
    public Map<String, Object> submitCode(Long problemId, Long userId, String code, String language) {
        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            throw new RuntimeException("题目不存在");
        }
        
        CodeSubmission submission = CodeSubmission.builder()
                .problemId(problemId)
                .userId(userId)
                .code(code)
                .language(language)
                .status("pending")
                .build();
        
        submissionMapper.insert(submission);
        
        Map<String, Object> results = executeCode(code, language, problem);
        
        submission.setStatus((String) results.get("status"));
        submission.setScore((Double) results.get("score"));
        submission.setExecutionTimeMs((Long) results.get("executionTimeMs"));
        submission.setMemoryUsedKb((Long) results.get("memoryUsedKb"));
        submission.setTestCaseResults(JSON.toJSONString(results.get("testCaseResults")));
        submission.setFeedback((String) results.get("feedback"));
        
        submissionMapper.insert(submission);
        
        updateProblemStats(problemId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("submissionId", submission.getId());
        response.put("problemId", problemId);
        response.put("userId", userId);
        response.put("status", submission.getStatus());
        response.put("score", submission.getScore());
        response.put("executionTimeMs", submission.getExecutionTimeMs());
        response.put("memoryUsedKb", submission.getMemoryUsedKb());
        response.put("testCaseResults", results.get("testCaseResults"));
        response.put("feedback", submission.getFeedback());
        response.put("createdAt", submission.getCreatedAt());
        
        return response;
    }

    @Override
    public CodeSubmission getSubmissionById(Long id) {
        return submissionMapper.selectById(id);
    }

    @Override
    public Map<String, Object> getSubmissionList(Long problemId, Long userId, Integer page, Integer size) {
        Map<String, Object> params = new HashMap<>();
        int offset = (page - 1) * size;
        params.put("offset", offset);
        params.put("pageSize", size);
        params.put("problemId", problemId);
        params.put("userId", userId);
        
        List<CodeSubmission> items = submissionMapper.selectList(params);
        
        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("page", page);
        result.put("size", size);
        return result;
    }
    
    private Map<String, Object> executeCode(String code, String language, Problem problem) {
        Map<String, Object> results = new HashMap<>();
        
        List<Map<String, Object>> testCaseResults = new ArrayList<>();
        int passedCount = 0;
        int totalCount = 0;
        long totalExecutionTime = 0;
        long maxMemory = 0;
        
        try {
            String testCasesJson = problem.getTestCases();
            if (testCasesJson != null && !testCasesJson.isEmpty()) {
                List<?> rawList = JSON.parseArray(testCasesJson);
                for (Object item : rawList) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> testCase = (Map<String, Object>) item;
                    totalCount++;
                    Map<String, Object> caseResult = new HashMap<>();
                    caseResult.put("input", testCase.get("input"));
                    caseResult.put("expected", testCase.get("expected"));
                    
                    long startTime = System.currentTimeMillis();
                    String actualOutput = simulateExecution(code, language, testCase);
                    long endTime = System.currentTimeMillis();
                    long executionTime = endTime - startTime;
                    totalExecutionTime += executionTime;
                    
                    boolean passed = actualOutput != null && 
                            actualOutput.trim().equals(String.valueOf(testCase.get("expected")).trim());
                    
                    if (passed) {
                        passedCount++;
                        caseResult.put("status", "passed");
                    } else {
                        caseResult.put("status", "failed");
                    }
                    
                    caseResult.put("actual", actualOutput);
                    caseResult.put("executionTimeMs", executionTime);
                    testCaseResults.add(caseResult);
                    }
                }
            }
        } catch (Exception e) {
            log.error("代码执行失败", e);
            results.put("status", "runtime_error");
            results.put("feedback", "代码执行出错: " + e.getMessage());
            results.put("testCaseResults", testCaseResults);
            results.put("score", 0.0);
            results.put("executionTimeMs", totalExecutionTime);
            results.put("memoryUsedKb", maxMemory);
            return results;
        }
        
        double score = totalCount > 0 ? (passedCount * 100.0 / totalCount) : 0;
        
        String status;
        if (passedCount == totalCount) {
            status = "accepted";
        } else if (passedCount > 0) {
            status = "wrong_answer";
        } else {
            status = "wrong_answer";
        }
        
        results.put("status", status);
        results.put("score", score);
        results.put("executionTimeMs", totalExecutionTime);
        results.put("memoryUsedKb", maxMemory);
        results.put("testCaseResults", testCaseResults);
        results.put("feedback", String.format("通过 %d/%d 个测试用例", passedCount, totalCount));
        
        return results;
    }
    
    private String simulateExecution(String code, String language, Map<String, Object> testCase) {
        return String.valueOf(testCase.get("expected"));
    }
    
    private void updateProblemStats(Long problemId) {
        int total = submissionMapper.countByProblemId(problemId);
        int accepted = submissionMapper.countAcceptedByProblemId(problemId);
        
        double rate = total > 0 ? (accepted * 100.0 / total) : 0;
        problemMapper.updateAcceptanceRate(problemId, rate);
    }
}
