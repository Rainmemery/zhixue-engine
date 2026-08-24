package com.rain.zhixueuser.mapper;

import com.rain.zhixueuser.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuthMapper {
    int insertUser(User user);
    User selectByUsername(String username);
    int insertUserProfile(@Param("userId") Long userId);
    int initUserAchievements(@Param("userId") Long userId, @Param("achievementIds") List<Integer> achievementIds);
    List<Integer> selectActiveAchievementIds();
}
