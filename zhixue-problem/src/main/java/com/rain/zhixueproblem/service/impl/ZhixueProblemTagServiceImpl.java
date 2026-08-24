package com.rain.zhixueproblem.service.impl;

import com.rain.zhixueproblem.entity.ZhixueProblemTag;
import com.rain.zhixueproblem.entity.ZhixueProblemTagRel;
import com.rain.zhixueproblem.mapper.ZhixueProblemTagMapper;
import com.rain.zhixueproblem.mapper.ZhixueProblemTagRelMapper;
import com.rain.zhixueproblem.service.ZhixueProblemTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZhixueProblemTagServiceImpl implements ZhixueProblemTagService {

    private final ZhixueProblemTagMapper zhixueProblemTagMapper;
    private final ZhixueProblemTagRelMapper zhixueProblemTagRelMapper;

    @Override
    public List<ZhixueProblemTag> getAllTags() {
        return zhixueProblemTagMapper.selectAll();
    }

    @Override
    public ZhixueProblemTag getTagById(Long id) {
        return zhixueProblemTagMapper.selectById(id);
    }

    @Override
    public ZhixueProblemTag createTag(ZhixueProblemTag tag) {
        if (tag.getProblemCount() == null) {
            tag.setProblemCount(0);
        }
        zhixueProblemTagMapper.insert(tag);
        return tag;
    }

    @Override
    public ZhixueProblemTag updateTag(Long id, ZhixueProblemTag tag) {
        tag.setId(id);
        zhixueProblemTagMapper.updateById(tag);
        return zhixueProblemTagMapper.selectById(id);
    }

    @Override
    @Transactional
    public void deleteTag(Long id) {
        List<ZhixueProblemTagRel> rels = zhixueProblemTagRelMapper.selectByTagId(id);
        for (ZhixueProblemTagRel rel : rels) {
            zhixueProblemTagRelMapper.deleteByProblemIdAndTagId(rel.getProblemId(), rel.getTagId());
        }
        zhixueProblemTagMapper.deleteById(id);
    }
}
