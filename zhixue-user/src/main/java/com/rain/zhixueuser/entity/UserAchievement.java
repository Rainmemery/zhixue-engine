package com.rain.zhixueuser.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserAchievement {
    private Long id;
    private Long userId;
    private Integer achievementId;
    private Integer progressCurrent;
    private Integer progressTarget;
    private LocalDateTime unlockedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Achievements achievement;
}
