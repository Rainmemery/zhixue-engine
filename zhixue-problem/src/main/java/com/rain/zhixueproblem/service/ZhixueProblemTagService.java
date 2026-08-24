package com.rain.zhixueproblem.service;

import com.rain.zhixueproblem.entity.ZhixueProblemTag;

import java.util.List;

public interface ZhixueProblemTagService {
    List<ZhixueProblemTag> getAllTags();

    ZhixueProblemTag getTagById(Long id);

    ZhixueProblemTag createTag(ZhixueProblemTag tag);

    ZhixueProblemTag updateTag(Long id, ZhixueProblemTag tag);

    void deleteTag(Long id);
}
