package com.rain.zhixuelearning.service;

import com.alibaba.fastjson2.JSONObject;
import com.rain.zhixuelearning.entity.LearningRecords;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface LearningRecordService {
    List<LearningRecords> getLearningRecords(JSONObject data);
    LearningRecords getLearningRecord(Long id);
    Long getCount(JSONObject data);
    Long addLearningRecords(LearningRecords learningRecords);
}
