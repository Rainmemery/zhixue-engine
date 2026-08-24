package com.rain.zhixueuser.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserProfiles {
    private Long id;
    private Long userId;
    String programmingLanguage;
    String difficultyPreference;
    Integer dailyGoalMinutes;
    String learningStyle;
    Integer weeklyLearningDays;
    String preferredTopics;
    String weakAreas;
    String learningGoals;
    String notificationPreferences;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}


