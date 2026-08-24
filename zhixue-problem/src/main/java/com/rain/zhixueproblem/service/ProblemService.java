package com.rain.zhixueproblem.service;

import com.rain.zhixueproblem.entity.Problem;
import com.rain.zhixueproblem.entity.ProblemCategory;

import java.util.List;
import java.util.Map;

public interface ProblemService {
    Problem createProblem(Problem problem);
    
    Problem updateProblem(Problem problem);
    
    void deleteProblem(Long id);
    
    Problem getProblemById(Long id);
    
    Map<String, Object> getProblemList(Integer page, Integer size, String difficulty, Long categoryId, String tags, String search);
    
    List<ProblemCategory> getAllCategories();
    
    ProblemCategory createCategory(ProblemCategory category);
    
    ProblemCategory updateCategory(ProblemCategory category);
    
    void deleteCategory(Long id);
}
