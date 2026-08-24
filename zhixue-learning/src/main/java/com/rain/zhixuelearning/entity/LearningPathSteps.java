package com.rain.zhixuelearning.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LearningPathSteps {

  private long id;
  private long learningPathId;
  private long stepNumber;
  private String stepType;
  private long problemId;
  private long knowledgePointId;
  private String title;
  private String description;
  private String prerequisites;
  private long estimatedTimeMinutes;
  private long actualDurationMinutes;
  private String status;
  private LocalDateTime completedAt;
  private double score;
  private String feedback;
  private String resources;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

}
