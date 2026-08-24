package com.rain.zhixueproblem.service.impl;

import com.rain.zhixueproblem.entity.ZhixueSubmission;
import com.rain.zhixueproblem.mapper.ZhixueSubmissionMapper;
import com.rain.zhixueproblem.service.ZhixueSubmissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZhixueSubmissionServiceImpl implements ZhixueSubmissionService {

    private final ZhixueSubmissionMapper zhixueSubmissionMapper;

    @Override
    public Map<String, Object> getSubmissionList(Integer page, Integer size, Long problemId, Long userId, String language, String status) {
        Map<String, Object> params = new HashMap<>();
        int offset = (page - 1) * size;
        params.put("offset", offset);
        params.put("pageSize", size);
        params.put("problemId", problemId);
        params.put("userId", userId);
        params.put("language", language);
        params.put("status", status);

        List<ZhixueSubmission> items = zhixueSubmissionMapper.selectList(params);
        int total = zhixueSubmissionMapper.countList(params);

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Override
    public ZhixueSubmission getSubmissionById(Long id) {
        return zhixueSubmissionMapper.selectById(id);
    }

    @Override
    public ZhixueSubmission createSubmission(ZhixueSubmission submission) {
        zhixueSubmissionMapper.insert(submission);
        return submission;
    }

    @Override
    public void updateSubmission(ZhixueSubmission submission) {
        zhixueSubmissionMapper.updateById(submission);
    }

    @Override
    public Map<String, Object> getUserSubmissionStats(Long userId) {
        int totalSubmissions = zhixueSubmissionMapper.countByUserId(userId);
        int acceptedSubmissions = zhixueSubmissionMapper.countAcceptedByUserId(userId);
        double acceptanceRate = totalSubmissions > 0 ? (acceptedSubmissions * 100.0 / totalSubmissions) : 0.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSubmissions", totalSubmissions);
        stats.put("acceptedSubmissions", acceptedSubmissions);
        stats.put("acceptanceRate", Math.round(acceptanceRate * 100.0) / 100.0);
        return stats;
    }

    @Override
    public Map<String, Object> getProblemSubmissionStats(Long problemId) {
        int totalSubmissions = zhixueSubmissionMapper.countByProblemId(problemId);
        int acceptedSubmissions = zhixueSubmissionMapper.countAcceptedByProblemId(problemId);
        double acceptanceRate = totalSubmissions > 0 ? (acceptedSubmissions * 100.0 / totalSubmissions) : 0.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSubmissions", totalSubmissions);
        stats.put("acceptedSubmissions", acceptedSubmissions);
        stats.put("acceptanceRate", Math.round(acceptanceRate * 100.0) / 100.0);
        return stats;
    }
}
