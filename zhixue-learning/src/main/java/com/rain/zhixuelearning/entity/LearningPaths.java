package com.rain.zhixuelearning.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class LearningPaths {

  private Long id;
  private Long userId;
  private String name;
  private String description;
  private String goal;
  private Long totalSteps;
  private Long currentStep;
  private String status;
  private Double progressPercentage;
  private Double estimatedCompletionHours;
  private Double actualCompletionHours;
  private LocalDate startDate;
  private LocalDate targetCompletionDate;
  private LocalDate actualCompletionDate;
  private Long satisfactionRating;
  private String feedbackText;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private Integer targetDays;
  private Integer dailyMinutes;
  private String focusAreas;
  private Integer progress;

}
