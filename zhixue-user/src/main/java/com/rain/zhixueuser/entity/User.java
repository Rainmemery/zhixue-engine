package com.rain.zhixueuser.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class User {
    private Long id;
    private String username;
    private String email;
    private String passwordHash;//密码哈希值
    private String realName;
    private String avatarUrl;//头像url
    private String phone;
    private String school;
    private String major;
    private String grade;
    private Integer learningLevel;
    private Integer experiencePoints;
    private Integer coins;
    private Integer achievementScore;
    private Integer dailyStreak;
    private LocalDate lastActiveDate;//最后活跃时间
    private short isActive;//是否启用
    private short emailVerfied;
    private short phoneVerfied;
    private LocalDateTime createdAt;//创建时间
    private LocalDateTime updatedAt;//更新时间
    private LocalDateTime deletedAt;//删除时间
}
