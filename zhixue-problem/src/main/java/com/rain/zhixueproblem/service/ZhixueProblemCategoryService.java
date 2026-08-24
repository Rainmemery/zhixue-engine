package com.rain.zhixueproblem.service;

import com.rain.zhixueproblem.entity.ZhixueProblemCategory;

import java.util.List;

public interface ZhixueProblemCategoryService {
    List<ZhixueProblemCategory> getAllCategories();

    ZhixueProblemCategory getCategoryById(Long id);

    ZhixueProblemCategory createCategory(ZhixueProblemCategory category);

    ZhixueProblemCategory updateCategory(Long id, ZhixueProblemCategory category);

    void deleteCategory(Long id);
}
