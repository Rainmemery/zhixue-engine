package com.rain.zhixuelearning.service.impl;

import com.rain.zhixuelearning.entity.LearningPathSteps;
import com.rain.zhixuelearning.entity.LearningPaths;
import com.rain.zhixuelearning.mapper.LearningPathMapper;
import com.rain.zhixuelearning.mapper.LearningPathStepMapper;
import com.rain.zhixuelearning.service.LearningPathService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LearningPathServiceImpl implements LearningPathService {
    
    private final LearningPathMapper learningPathMapper;
    private final LearningPathStepMapper stepMapper;

    @Override
    @Transactional
    public LearningPaths generateLearningPath(LearningPaths learningPaths) {
        if (learningPaths.getUserId() == null) {
            throw new RuntimeException("用户ID不能为空");
        }
        if (learningPaths.getName() == null || learningPaths.getName().trim().isEmpty()) {
            throw new RuntimeException("学习路径名称不能为空");
        }
        
        learningPaths.setStatus("active");
        learningPaths.setCurrentStep(1L);
        learningPaths.setProgressPercentage(0.0);
        learningPaths.setStartDate(LocalDate.now());
        
        if (learningPaths.getTotalSteps() == null) {
            learningPaths.setTotalSteps(0L);
        }
        if (learningPaths.getEstimatedCompletionHours() == null) {
            learningPaths.setEstimatedCompletionHours(0.0);
        }
        if (learningPaths.getActualCompletionHours() == null) {
            learningPaths.setActualCompletionHours(0.0);
        }
        if (learningPaths.getTargetDays() == null) {
            learningPaths.setTargetDays(30);
        }
        if (learningPaths.getDailyMinutes() == null) {
            learningPaths.setDailyMinutes(60);
        }
        if (learningPaths.getProgress() == null) {
            learningPaths.setProgress(0);
        }
        
        learningPathMapper.insert(learningPaths);
        
        if (learningPaths.getTotalSteps() != null && learningPaths.getTotalSteps() > 0) {
            for (int i = 1; i <= learningPaths.getTotalSteps(); i++) {
                LearningPathSteps step = new LearningPathSteps();
                step.setLearningPathId(learningPaths.getId());
                step.setStepNumber(i);
                step.setTitle("步骤 " + i);
                step.setDescription("学习路径第 " + i + " 步");
                step.setEstimatedTimeMinutes(30);
                step.setStatus("pending");
                stepMapper.insert(step);
            }
        }
        
        return learningPaths;
    }

    @Override
    public List<LearningPaths> getRecommendedPaths() {
        return learningPathMapper.selectRecommended();
    }

    @Override
    public List<LearningPaths> getUserPaths(Long userId) {
        return learningPathMapper.selectByUserId(userId);
    }

    @Override
    public LearningPaths getPathById(Long id) {
        return learningPathMapper.selectById(id);
    }

    @Override
    @Transactional
    public LearningPaths startPath(Long id) {
        LearningPaths path = learningPathMapper.selectById(id);
        if (path == null) {
            throw new RuntimeException("学习路径不存在");
        }
        
        path.setStatus("active");
        path.setStartDate(LocalDate.now());
        learningPathMapper.updateById(path);
        
        return learningPathMapper.selectById(id);
    }

    @Override
    @Transactional
    public LearningPaths pausePath(Long id) {
        LearningPaths path = learningPathMapper.selectById(id);
        if (path == null) {
            throw new RuntimeException("学习路径不存在");
        }
        
        path.setStatus("paused");
        learningPathMapper.updateById(path);
        
        return learningPathMapper.selectById(id);
    }

    @Override
    @Transactional
    public LearningPaths continuePath(Long id) {
        LearningPaths path = learningPathMapper.selectById(id);
        if (path == null) {
            throw new RuntimeException("学习路径不存在");
        }
        
        path.setStatus("active");
        learningPathMapper.updateById(path);
        
        return learningPathMapper.selectById(id);
    }

    @Override
    @Transactional
    public LearningPaths completePath(Long id) {
        LearningPaths path = learningPathMapper.selectById(id);
        if (path == null) {
            throw new RuntimeException("学习路径不存在");
        }
        
        path.setStatus("completed");
        path.setProgressPercentage(100.0);
        path.setActualCompletionDate(LocalDate.now());
        learningPathMapper.updateById(path);
        
        return learningPathMapper.selectById(id);
    }

    @Override
    @Transactional
    public LearningPathSteps updateProgress(Long pathId, Integer stepNumber, String status, Integer score, Integer timeSpentMinutes) {
        LearningPaths path = learningPathMapper.selectById(pathId);
        if (path == null) {
            throw new RuntimeException("学习路径不存在");
        }
        
        List<LearningPathSteps> steps = stepMapper.selectByPathId(pathId);
        LearningPathSteps targetStep = null;
        
        for (LearningPathSteps step : steps) {
            if (step.getStepNumber() == stepNumber) {
                targetStep = step;
                break;
            }
        }
        
        if (targetStep == null) {
            throw new RuntimeException("步骤不存在");
        }
        
        targetStep.setStatus(status);
        if (score != null) {
            targetStep.setScore(score);
        }
        if (timeSpentMinutes != null) {
            targetStep.setActualDurationMinutes(timeSpentMinutes);
        }
        stepMapper.updateById(targetStep);
        
        int completedSteps = 0;
        for (LearningPathSteps step : steps) {
            if ("completed".equals(step.getStatus())) {
                completedSteps++;
            }
        }
        
        if ("completed".equals(status)) {
            completedSteps++;
        }
        
        double progress = steps.size() > 0 ? (completedSteps * 100.0 / steps.size()) : 0;
        path.setProgressPercentage(progress);
        path.setCurrentStep((long) Math.min(stepNumber + 1, steps.size()));
        learningPathMapper.updateById(path);
        
        return stepMapper.selectById(targetStep.getId());
    }
}
