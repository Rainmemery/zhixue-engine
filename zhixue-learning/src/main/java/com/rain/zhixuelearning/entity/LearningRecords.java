package com.rain.zhixuelearning.entity;


import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class LearningRecords {

  private Long id;
  private Long userId;
  private Long problemId;
  private String learningType;
  private Long contentId;
  private Long durationSeconds;
  private Double score;
  private String feedback;
  private LocalDate learningDate;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private String submissionId;
  private Integer timeSpentMs;
  private Integer debugCount;
  private Integer attempts;
  private LocalDateTime firstSolvedAt;
  private LocalDateTime lastAttemptAt;
  private String status;
}
