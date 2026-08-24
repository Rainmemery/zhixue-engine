package com.rain.zhixuelearning.controller;


import com.alibaba.fastjson2.JSONObject;
import com.rain.zhixuecommon.entity.Result;
import com.rain.zhixuelearning.entity.LearningRecords;
import com.rain.zhixuelearning.service.LearningRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/learning")
public class LearningRecordsController {

    @Autowired
    LearningRecordService learningRecordService;


    @GetMapping("/records")
    public Result getLearningRecords(@RequestHeader("Authorization") String authHeader,
                                     @RequestParam(required = false) Long userId,
                                     @RequestParam(required = false) Long problemId,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) String startDate,
                                     @RequestParam(required = false) String endDate,
                                     @RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "20") Integer size){
        String Token = authHeader.replaceFirst("^Bearer\\s+", "").trim();
        //用户服务验证token
        try {
            JSONObject map = new JSONObject();
            map.put("userId", userId);
            map.put("problemId", problemId);
            map.put("status", status);
            map.put("startDate", startDate);
            map.put("endDate", endDate);
            map.put("page", page);
            map.put("size", size);
            if(page==null)page=1;
            if(size==null)size=20;
            List<LearningRecords> learningRecords=learningRecordService.getLearningRecords(map);
            if(learningRecords==null){
                log.info("不存在学习记录");
                return Result.wrong("不存在学习记录");
            }
            Map<String,Object> data=new HashMap<>();
            Map<String,Object> pageInformation=new HashMap<>();
            Long total=learningRecordService.getCount(map);
            pageInformation.put("page",page);
            pageInformation.put("size",size);
            pageInformation.put("total",total);
            pageInformation.put("totalPages",(total-1)/size+1);
            data.put("items",learningRecords);
            data.put("pagination",pageInformation);
            return  Result.success(data);
        }catch (Exception e){
            log.info("出错了 + "+e.getMessage());
            return Result.wrong("出错了，请联系管理员");
        }
    }

    @PostMapping("/records")
    public Result addLearningRecords(@RequestHeader("Authorization") String authHeader,@RequestBody LearningRecords learningRecords){
        String Token = authHeader.replaceFirst("^Bearer\\s+", "").trim();
        //用户服务验证token
        try {
            Long id=learningRecordService.addLearningRecords(learningRecords);
            if(id<=0){
                return Result.wrong("添加失败，请联系管理员");
            }
            log.info("\nid: {}\n",id);
            return Result.success(201,learningRecordService.getLearningRecord(id),"加入成功",null);
        }catch (Exception e){
            log.info("controller出现异常 + "+e.getMessage());
            return Result.wrong("产生异常，创建失败");
        }
    }
}
