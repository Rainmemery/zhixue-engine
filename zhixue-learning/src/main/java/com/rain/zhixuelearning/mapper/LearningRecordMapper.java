package com.rain.zhixuelearning.mapper;

import com.alibaba.fastjson2.JSONObject;
import com.rain.zhixuelearning.entity.LearningRecords;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface LearningRecordMapper {
    List<LearningRecords> selectList(JSONObject data);
    LearningRecords selectById(Long id);
    Long count(JSONObject data);
    Long insert(LearningRecords learningRecords);
}
