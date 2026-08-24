package com.rain.zhixueuser.controller;

import com.rain.zhixuecommon.entity.Result;
import com.rain.zhixuecommon.utils.JwtUtil;
import com.rain.zhixueuser.entity.UserProfiles;
import com.rain.zhixueuser.service.UserProfilesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/api/v1/users/{userId}")
public class UserProfilesController {
    @Autowired
    private UserProfilesService userProfilesService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/profile")
    public Result getUserProfiles(@PathVariable("userId") Long userId,@RequestHeader("Authorization") String authHeader){
        try {
            String jwtToken = authHeader.replaceFirst("^Bearer\\s+", "").trim();
            if(!jwtUtil.getUserIdFromToken(jwtToken).equals(userId.toString())){
                return Result.wrong("认证失败，无权限");
            }
            UserProfiles userProfiles = userProfilesService.getUserProfiles(userId);
            //Map<String,Object> data = new HashMap<>();
            return Result.success(userProfiles,"success");
        }catch (Exception e){
            return Result.wrong("获取信息失败");
        }
    }

    @PutMapping("/profile")
    public Result updateUserProfiles(@PathVariable("userId") Long userId,@RequestBody UserProfiles userProfiles,@RequestHeader("Authorization") String authHeader){
        try {
            String jwtToken = authHeader.replaceFirst("^Bearer\\s+", "").trim();
            if(!jwtUtil.getUserIdFromToken(jwtToken).equals(userId.toString())){
                return Result.wrong("认证失败，无权限");
            }
            //更新方法
            int result = userProfilesService.updateUserProfiles(userProfiles);
            if(result == 0){
                return Result.wrong("更新失败,请联系管理员");
            }
            userProfiles = userProfilesService.getUserProfiles(userId);
            return Result.success(userProfiles,"success");
        }catch (Exception e){
            log.error("更新UserProfiles失败，错误信息 "+e.getMessage());
            return Result.wrong("更新信息失败，请联系管理员");
        }
    }

    @GetMapping("/agent-summary")
    public Result getAgentSummary(@PathVariable("userId") Long userId) {
        try {
            Map<String, Object> summary = new LinkedHashMap<>();

            com.rain.zhixueuser.entity.User user = userProfilesService.getUserById(userId);
            if (user == null) {
                return Result.wrong("用户不存在");
            }

            Map<String, Object> basic = new LinkedHashMap<>();
            basic.put("learningLevel", user.getLearningLevel());
            basic.put("experiencePoints", user.getExperiencePoints());
            basic.put("dailyStreak", user.getDailyStreak());
            summary.put("basic", basic);

            com.rain.zhixueuser.entity.UserProfiles profile = userProfilesService.getByUserId(userId);
            if (profile != null) {
                Map<String, Object> preference = new LinkedHashMap<>();
                preference.put("difficultyPreference", profile.getDifficultyPreference());
                preference.put("programmingLanguage", profile.getProgrammingLanguage());
                preference.put("learningStyle", profile.getLearningStyle());
                preference.put("weakAreas", profile.getWeakAreas());
                preference.put("learningGoals", profile.getLearningGoals());
                summary.put("preference", preference);
            }

            return Result.success(summary);
        } catch (Exception e) {
            return Result.wrong("获取用户画像摘要失败: " + e.getMessage());
        }
    }
}
