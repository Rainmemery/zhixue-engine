package com.rain.zhixueuser.controller;

import com.rain.zhixuecommon.entity.Result;
import com.rain.zhixuecommon.utils.JwtUtil;
import com.rain.zhixueuser.entity.UserAchievement;
import com.rain.zhixueuser.service.UserAchievementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/users/{userId}")
public class UserAchievementController {

    @Autowired
    private UserAchievementService userAchievementService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/achievements")
    public Result getUserAchievements(@PathVariable("userId") Long userId, @RequestHeader("Authorization") String authHeader) {
        try {
            String jwtToken = authHeader.replaceFirst("^Bearer\\s+", "").trim();
            if (!jwtUtil.getUserIdFromToken(jwtToken).equals(userId.toString())) {
                return Result.wrong("认证失败，无权限");
            }
            List<UserAchievement> achievements = userAchievementService.getUserAchievements(userId);
            return Result.success(achievements, "success");
        } catch (Exception e) {
            log.error("获取用户成就失败，错误信息: {}", e.getMessage());
            return Result.wrong("获取成就信息失败");
        }
    }
}
