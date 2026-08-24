package com.rain.zhixueuser.service.impl;

import com.rain.zhixueuser.entity.UserAchievement;
import com.rain.zhixueuser.mapper.UserAchievementMapper;
import com.rain.zhixueuser.service.UserAchievementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class UserAchievementServiceImpl implements UserAchievementService {

    @Autowired
    UserAchievementMapper userAchievementMapper;

    @Override
    public List<UserAchievement> getUserAchievements(Long userId) {
        log.info("获取用户成就列表，userId: {}", userId);
        return userAchievementMapper.selectByUserId(userId);
    }
}
