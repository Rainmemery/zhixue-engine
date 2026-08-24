package com.rain.zhixueuser.service;

import com.rain.zhixueuser.entity.UserAchievement;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface UserAchievementService {
    List<UserAchievement> getUserAchievements(Long userId);
}
