package com.rain.zhixueproblem.service.impl;

import com.rain.zhixueproblem.entity.Problem;
import com.rain.zhixueproblem.entity.ProblemCategory;
import com.rain.zhixueproblem.mapper.CodeSubmissionMapper;
import com.rain.zhixueproblem.mapper.ProblemCategoryMapper;
import com.rain.zhixueproblem.mapper.ProblemMapper;
import com.rain.zhixueproblem.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProblemServiceImpl implements ProblemService {
    
    private final ProblemMapper problemMapper;
    private final ProblemCategoryMapper categoryMapper;
    private final CodeSubmissionMapper submissionMapper;

    @Override
    public Problem createProblem(Problem problem) {
        problem.setAcceptanceRate(0.0);
        problem.setViewCount(0);
        problem.setSubmitCount(0);
        problemMapper.insert(problem);
        return problem;
    }

    @Override
    public Problem updateProblem(Problem problem) {
        problemMapper.updateById(problem);
        return problemMapper.selectById(problem.getId());
    }

    @Override
    public void deleteProblem(Long id) {
        problemMapper.deleteById(id);
    }

    @Override
    public Problem getProblemById(Long id) {
        return problemMapper.selectById(id);
    }

    @Override
    public Map<String, Object> getProblemList(Integer page, Integer size, String difficulty, Long categoryId, String tags, String search) {
        Map<String, Object> params = new HashMap<>();
        int offset = (page - 1) * size;
        params.put("offset", offset);
        params.put("pageSize", size);
        params.put("difficulty", difficulty);
        params.put("categoryId", categoryId);
        params.put("tags", tags);
        params.put("search", search);
        
        List<Problem> items = problemMapper.selectList(params);
        int total = problemMapper.countList(params);
        
        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Override
    public List<ProblemCategory> getAllCategories() {
        return categoryMapper.selectAll();
    }

    @Override
    public ProblemCategory createCategory(ProblemCategory category) {
        if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }
        categoryMapper.insert(category);
        return category;
    }

    @Override
    public ProblemCategory updateCategory(ProblemCategory category) {
        categoryMapper.updateById(category);
        return categoryMapper.selectById(category.getId());
    }

    @Override
    public void deleteCategory(Long id) {
        categoryMapper.deleteById(id);
    }
}
