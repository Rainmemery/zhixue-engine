package com.rain.zhixuelearning.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.github.pagehelper.PageHelper;
import com.rain.zhixuelearning.entity.LearningRecords;
import com.rain.zhixuelearning.mapper.LearningRecordMapper;
import com.rain.zhixuelearning.service.LearningRecordService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class LearningRecordServiceImpl implements LearningRecordService {

    @Autowired
    LearningRecordMapper learningRecordMapper;

    @Override
    public List<LearningRecords> getLearningRecords(JSONObject data){
        try {
            PageHelper.startPage(data.getInteger("page"), data.getInteger("size"));
            List<LearningRecords> learningRecords=learningRecordMapper.selectList(data);
            return learningRecords;
        }catch (Exception e){
            log.info("服务层出错了 + "+e.getMessage());
            return  null;
        }
    }

    @Override
    public LearningRecords getLearningRecord(Long id){
        return learningRecordMapper.selectById(id);
    }

    @Override
    public Long getCount(JSONObject data) {
        return learningRecordMapper.count(data);
    }

    @Override
    public Long addLearningRecords(LearningRecords learningRecords) {
        learningRecordMapper.insert(learningRecords);
        Long id=learningRecords.getId();
        log.info("id : {}",id);
        return id;
    }
}
