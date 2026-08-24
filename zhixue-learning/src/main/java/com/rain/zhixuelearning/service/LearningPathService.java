package com.rain.zhixuelearning.service;

import com.rain.zhixuelearning.entity.LearningPathSteps;
import com.rain.zhixuelearning.entity.LearningPaths;

import java.util.List;

public interface LearningPathService {
    LearningPaths generateLearningPath(LearningPaths learningPaths);
    
    List<LearningPaths> getRecommendedPaths();
    
    List<LearningPaths> getUserPaths(Long userId);
    
    LearningPaths getPathById(Long id);
    
    LearningPaths startPath(Long id);
    
    LearningPaths pausePath(Long id);
    
    LearningPaths continuePath(Long id);
    
    LearningPaths completePath(Long id);
    
    LearningPathSteps updateProgress(Long pathId, Integer stepNumber, String status, Integer score, Integer timeSpentMinutes);
}
