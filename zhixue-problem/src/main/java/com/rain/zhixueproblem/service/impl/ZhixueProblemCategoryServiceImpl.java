package com.rain.zhixueproblem.service.impl;

import com.rain.zhixueproblem.entity.ZhixueProblemCategory;
import com.rain.zhixueproblem.mapper.ZhixueProblemCategoryMapper;
import com.rain.zhixueproblem.service.ZhixueProblemCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZhixueProblemCategoryServiceImpl implements ZhixueProblemCategoryService {

    private final ZhixueProblemCategoryMapper zhixueProblemCategoryMapper;

    @Override
    public List<ZhixueProblemCategory> getAllCategories() {
        return zhixueProblemCategoryMapper.selectAll();
    }

    @Override
    public ZhixueProblemCategory getCategoryById(Long id) {
        return zhixueProblemCategoryMapper.selectById(id);
    }

    @Override
    public ZhixueProblemCategory createCategory(ZhixueProblemCategory category) {
        if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }
        zhixueProblemCategoryMapper.insert(category);
        return category;
    }

    @Override
    public ZhixueProblemCategory updateCategory(Long id, ZhixueProblemCategory category) {
        category.setId(id);
        zhixueProblemCategoryMapper.updateById(category);
        return zhixueProblemCategoryMapper.selectById(id);
    }

    @Override
    public void deleteCategory(Long id) {
        zhixueProblemCategoryMapper.deleteById(id);
    }
}
