package com.rain.zhixueuser.mapper;

import com.rain.zhixueuser.entity.UserAchievement;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserAchievementMapper {
    List<UserAchievement> selectByUserId(Long userId);
}
