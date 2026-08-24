package com.rain.zhixueuser.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Achievements {
    private Integer id;
    private String code;
    private String name;
    private String description;
    private String iconUrl;
    private String category;
    private String rarity;
    private Integer pointsAwarded;
    private Integer coinsAwarded;
    private String badgeUrl;
    private String conditionType;
    private String conditionConfig;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
