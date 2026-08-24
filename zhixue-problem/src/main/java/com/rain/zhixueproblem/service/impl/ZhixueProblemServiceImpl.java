package com.rain.zhixueproblem.service.impl;

import com.rain.zhixueproblem.entity.ZhixueProblem;
import com.rain.zhixueproblem.entity.ZhixueProblemCategory;
import com.rain.zhixueproblem.entity.ZhixueProblemSample;
import com.rain.zhixueproblem.entity.ZhixueProblemTag;
import com.rain.zhixueproblem.entity.ZhixueProblemTagRel;
import com.rain.zhixueproblem.entity.ZhixueUserProblemStatus;
import com.rain.zhixueproblem.mapper.ZhixueProblemCategoryMapper;
import com.rain.zhixueproblem.mapper.ZhixueProblemMapper;
import com.rain.zhixueproblem.mapper.ZhixueProblemSampleMapper;
import com.rain.zhixueproblem.mapper.ZhixueProblemTagMapper;
import com.rain.zhixueproblem.mapper.ZhixueProblemTagRelMapper;
import com.rain.zhixueproblem.mapper.ZhixueProblemTestcaseMapper;
import com.rain.zhixueproblem.mapper.ZhixueUserProblemStatusMapper;
import com.rain.zhixueproblem.service.ZhixueProblemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZhixueProblemServiceImpl implements ZhixueProblemService {

    private final ZhixueProblemMapper zhixueProblemMapper;
    private final ZhixueProblemSampleMapper zhixueProblemSampleMapper;
    private final ZhixueProblemTestcaseMapper zhixueProblemTestcaseMapper;
    private final ZhixueProblemTagRelMapper zhixueProblemTagRelMapper;
    private final ZhixueProblemTagMapper zhixueProblemTagMapper;
    private final ZhixueProblemCategoryMapper zhixueProblemCategoryMapper;
    private final ZhixueUserProblemStatusMapper zhixueUserProblemStatusMapper;

    @Override
    public Map<String, Object> getProblemList(Integer page, Integer size, String difficulty, Long categoryId, Long tagId, String problemType, String search, String sort, Long userId) {
        Map<String, Object> params = new HashMap<>();
        int offset = (page - 1) * size;
        params.put("offset", offset);
        params.put("pageSize", size);
        params.put("difficulty", difficulty);
        params.put("categoryId", categoryId);
        params.put("tagId", tagId);
        params.put("problemType", problemType);
        params.put("search", search);
        params.put("sort", sort);
        params.put("isPublic", true);

        List<ZhixueProblem> items = zhixueProblemMapper.selectList(params);
        int total = zhixueProblemMapper.countList(params);

        for (ZhixueProblem problem : items) {
            List<ZhixueProblemTagRel> tagRels = zhixueProblemTagRelMapper.selectByProblemId(problem.getId());
            List<ZhixueProblemTag> tags = new ArrayList<>();
            for (ZhixueProblemTagRel rel : tagRels) {
                ZhixueProblemTag tag = zhixueProblemTagMapper.selectById(rel.getTagId());
                if (tag != null) {
                    tags.add(tag);
                }
            }
            problem.setTags(tags);
            problem.setTagNames(tags.stream().map(ZhixueProblemTag::getName).collect(Collectors.toList()));
            problem.setTagIds(tags.stream().map(ZhixueProblemTag::getId).collect(Collectors.toList()));

            if (problem.getCategoryId() != null) {
                ZhixueProblemCategory category = zhixueProblemCategoryMapper.selectById(problem.getCategoryId());
                if (category != null) {
                    problem.setCategoryName(category.getName());
                }
            }

            if (userId != null) {
                ZhixueUserProblemStatus userStatus = zhixueUserProblemStatusMapper.selectByUserIdAndProblemId(userId, problem.getId());
                problem.setUserStatus(userStatus);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Override
    public Map<String, Object> getAdminProblemList(Integer page, Integer size, String difficulty, Long categoryId, String problemType, String search, Boolean isPublic) {
        Map<String, Object> params = new HashMap<>();
        int offset = (page - 1) * size;
        params.put("offset", offset);
        params.put("pageSize", size);
        params.put("difficulty", difficulty);
        params.put("categoryId", categoryId);
        params.put("problemType", problemType);
        params.put("search", search);
        params.put("isPublic", isPublic);

        List<ZhixueProblem> items = zhixueProblemMapper.selectAdminList(params);
        int total = zhixueProblemMapper.countAdminList(params);

        for (ZhixueProblem problem : items) {
            List<ZhixueProblemTagRel> tagRels = zhixueProblemTagRelMapper.selectByProblemId(problem.getId());
            List<ZhixueProblemTag> tags = new ArrayList<>();
            for (ZhixueProblemTagRel rel : tagRels) {
                ZhixueProblemTag tag = zhixueProblemTagMapper.selectById(rel.getTagId());
                if (tag != null) {
                    tags.add(tag);
                }
            }
            problem.setTags(tags);
            problem.setTagNames(tags.stream().map(ZhixueProblemTag::getName).collect(Collectors.toList()));
            problem.setTagIds(tags.stream().map(ZhixueProblemTag::getId).collect(Collectors.toList()));

            if (problem.getCategoryId() != null) {
                ZhixueProblemCategory category = zhixueProblemCategoryMapper.selectById(problem.getCategoryId());
                if (category != null) {
                    problem.setCategoryName(category.getName());
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Override
    public ZhixueProblem getProblemDetail(Long id, Long userId) {
        ZhixueProblem problem = zhixueProblemMapper.selectById(id);
        if (problem == null) {
            throw new RuntimeException("题目不存在");
        }

        List<ZhixueProblemSample> samples = zhixueProblemSampleMapper.selectByProblemId(id);
        problem.setSamples(samples);

        List<ZhixueProblemTagRel> tagRels = zhixueProblemTagRelMapper.selectByProblemId(id);
        List<ZhixueProblemTag> tags = new ArrayList<>();
        for (ZhixueProblemTagRel rel : tagRels) {
            ZhixueProblemTag tag = zhixueProblemTagMapper.selectById(rel.getTagId());
            if (tag != null) {
                tags.add(tag);
            }
        }
        problem.setTags(tags);
        problem.setTagNames(tags.stream().map(ZhixueProblemTag::getName).collect(Collectors.toList()));
        problem.setTagIds(tags.stream().map(ZhixueProblemTag::getId).collect(Collectors.toList()));

        if (problem.getCategoryId() != null) {
            ZhixueProblemCategory category = zhixueProblemCategoryMapper.selectById(problem.getCategoryId());
            if (category != null) {
                problem.setCategoryName(category.getName());
            }
        }

        if (userId != null) {
            ZhixueUserProblemStatus userStatus = zhixueUserProblemStatusMapper.selectByUserIdAndProblemId(userId, id);
            problem.setUserStatus(userStatus);
        }

        return problem;
    }

    @Override
    @Transactional
    public ZhixueProblem createProblem(ZhixueProblem problem, List<ZhixueProblemSample> samples, List<Long> tagIds) {
        problem.setViewCount(0);
        problem.setSubmitCount(0);
        problem.setAcceptedCount(0);
        problem.setAcceptanceRate(0.0);
        if (problem.getIsPublic() == null) {
            problem.setIsPublic(true);
        }
        zhixueProblemMapper.insert(problem);

        if (samples != null && !samples.isEmpty()) {
            for (int i = 0; i < samples.size(); i++) {
                ZhixueProblemSample sample = samples.get(i);
                sample.setProblemId(problem.getId());
                sample.setSortOrder(i);
                zhixueProblemSampleMapper.insert(sample);
            }
        }

        if (tagIds != null && !tagIds.isEmpty()) {
            List<ZhixueProblemTagRel> tagRels = new ArrayList<>();
            for (Long tagId : tagIds) {
                ZhixueProblemTagRel rel = new ZhixueProblemTagRel();
                rel.setProblemId(problem.getId());
                rel.setTagId(tagId);
                tagRels.add(rel);
            }
            zhixueProblemTagRelMapper.batchInsert(tagRels);
        }

        return problem;
    }

    @Override
    @Transactional
    public ZhixueProblem updateProblem(Long id, ZhixueProblem problem, List<ZhixueProblemSample> samples, List<Long> tagIds) {
        problem.setId(id);
        zhixueProblemMapper.updateById(problem);

        zhixueProblemSampleMapper.deleteByProblemId(id);
        if (samples != null && !samples.isEmpty()) {
            for (int i = 0; i < samples.size(); i++) {
                ZhixueProblemSample sample = samples.get(i);
                sample.setProblemId(id);
                sample.setSortOrder(i);
                zhixueProblemSampleMapper.insert(sample);
            }
        }

        zhixueProblemTagRelMapper.deleteByProblemId(id);
        if (tagIds != null && !tagIds.isEmpty()) {
            List<ZhixueProblemTagRel> tagRels = new ArrayList<>();
            for (Long tagId : tagIds) {
                ZhixueProblemTagRel rel = new ZhixueProblemTagRel();
                rel.setProblemId(id);
                rel.setTagId(tagId);
                tagRels.add(rel);
            }
            zhixueProblemTagRelMapper.batchInsert(tagRels);
        }

        return zhixueProblemMapper.selectById(id);
    }

    @Override
    public void deleteProblem(Long id) {
        ZhixueProblem problem = new ZhixueProblem();
        problem.setId(id);
        problem.setDeletedAt(LocalDateTime.now());
        zhixueProblemMapper.updateById(problem);
    }

    @Override
    public void batchDeleteProblems(List<Long> ids) {
        for (Long id : ids) {
            deleteProblem(id);
        }
    }

    @Override
    public List<ZhixueProblem> findProblemsForRecommend(Long userId, String difficulty, String knowledgePoint, String strategy, boolean excludeSolved, int limit, Long categoryId) {
        Map<String, Object> params = new HashMap<>();
        params.put("difficulty", difficulty);
        params.put("isPublic", true);
        if (categoryId != null) {
            params.put("categoryId", categoryId);
        }
        List<ZhixueProblem> candidates = zhixueProblemMapper.selectList(params);
        if (candidates == null || candidates.isEmpty()) return Collections.emptyList();

        for (ZhixueProblem p : candidates) {
            List<ZhixueProblemTagRel> tagRels = zhixueProblemTagRelMapper.selectByProblemId(p.getId());
            if (tagRels != null && !tagRels.isEmpty()) {
                List<ZhixueProblemTag> tags = new ArrayList<>();
                for (ZhixueProblemTagRel rel : tagRels) {
                    ZhixueProblemTag tag = zhixueProblemTagMapper.selectById(rel.getTagId());
                    if (tag != null) {
                        tags.add(tag);
                    }
                }
                p.setTags(tags);
            }
        }

        if (excludeSolved && userId != null) {
            Set<Long> solvedIds = zhixueUserProblemStatusMapper.selectByUserId(userId).stream()
                .filter(s -> "accepted".equals(s.getStatus()))
                .map(ZhixueUserProblemStatus::getProblemId)
                .collect(Collectors.toSet());
            candidates = candidates.stream().filter(p -> !solvedIds.contains(p.getId())).collect(Collectors.toList());
        }

        if (knowledgePoint != null && !knowledgePoint.isEmpty()) {
            candidates = candidates.stream().filter(p -> {
                if (p.getTags() == null) return false;
                return p.getTags().stream().anyMatch(t -> t.getName().contains(knowledgePoint));
            }).collect(Collectors.toList());
        }

        if (strategy != null && !strategy.isEmpty()) {
            Map<String, double[]> strategyWeights = Map.of(
                "weakness", new double[]{0.40, 0.20, 0.15, 0.15, 0.10},
                "progressive", new double[]{0.20, 0.35, 0.10, 0.25, 0.10},
                "review", new double[]{0.15, 0.20, 0.30, 0.20, 0.15},
                "explore", new double[]{0.10, 0.20, 0.15, 0.20, 0.35}
            );
            double[] weights = strategyWeights.getOrDefault(strategy, strategyWeights.get("weakness"));

            int maxSubmitCount = candidates.stream()
                .mapToInt(p -> p.getSubmitCount() != null ? p.getSubmitCount() : 0)
                .max().orElse(1);

            candidates.sort((a, b) -> {
                double scoreA = calculateRecommendScore(a, difficulty, knowledgePoint, weights, maxSubmitCount, strategy);
                double scoreB = calculateRecommendScore(b, difficulty, knowledgePoint, weights, maxSubmitCount, strategy);
                return Double.compare(scoreB, scoreA);
            });
        }

        return candidates.stream().limit(limit).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getUserProblemStatus(Long userId) {
        Map<String, Object> status = new LinkedHashMap<>();
        List<ZhixueUserProblemStatus> userStatuses = zhixueUserProblemStatusMapper.selectByUserId(userId);

        int total = userStatuses.size();
        long accepted = userStatuses.stream().filter(s -> "accepted".equals(s.getStatus())).count();
        double passRate = total > 0 ? (double) accepted / total : 0.0;
        double avgAttempts = userStatuses.stream().mapToInt(s -> s.getAttemptCount() != null ? s.getAttemptCount() : 0).average().orElse(0.0);

        status.put("passRate", passRate);
        status.put("averageAttempts", avgAttempts);
        status.put("totalSubmissions", total);
        status.put("trend", "stable");
        status.put("consecutivePasses", 0);
        status.put("consecutiveFails", 0);

        return status;
    }

    @Override
    public Map<String, Object> getUserWrongQuestions(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<ZhixueUserProblemStatus> wrongStatuses = zhixueUserProblemStatusMapper.selectByUserId(userId).stream()
            .filter(s -> "wrong_answer".equals(s.getStatus()) || "attempted".equals(s.getStatus()))
            .collect(Collectors.toList());

        result.put("totalCount", wrongStatuses.size());
        result.put("wrongQuestions", Collections.emptyList());
        result.put("categoryStats", Collections.emptyList());

        return result;
    }

    private double calculateRecommendScore(ZhixueProblem problem, String targetDifficulty, String knowledgePoint, double[] weights, int maxSubmitCount, String strategy) {
        double d1 = 0.5;
        if (targetDifficulty != null && !targetDifficulty.isEmpty()) {
            String pDiff = problem.getDifficulty();
            if (targetDifficulty.equals(pDiff)) {
                d1 = 1.0;
            } else if (isAdjacentDifficulty(targetDifficulty, pDiff)) {
                d1 = 0.5;
            } else {
                d1 = 0.2;
            }
        }

        double acceptanceRate = problem.getAcceptanceRate() != null ? problem.getAcceptanceRate() : 50.0;
        if (acceptanceRate > 1.0) acceptanceRate = acceptanceRate / 100.0;
        double d2;
        if ("weakness".equals(strategy)) {
            d2 = 1.0 - acceptanceRate;
        } else if ("progressive".equals(strategy)) {
            d2 = 1.0 - Math.abs(acceptanceRate - 0.6);
        } else {
            d2 = acceptanceRate;
        }

        int submitCount = problem.getSubmitCount() != null ? problem.getSubmitCount() : 0;
        double d3 = maxSubmitCount > 0 ? (double) submitCount / maxSubmitCount : 0.5;

        double d4 = 0.5;
        if (knowledgePoint != null && !knowledgePoint.isEmpty()) {
            if (problem.getTags() != null) {
                d4 = problem.getTags().stream().anyMatch(t -> t.getName().contains(knowledgePoint)) ? 1.0 : 0.0;
            } else {
                d4 = 0.0;
            }
        }

        double d5 = Math.random() * 0.3 + 0.7;

        return weights[0] * d1 + weights[1] * d2 + weights[2] * d3 + weights[3] * d4 + weights[4] * d5;
    }

    private boolean isAdjacentDifficulty(String d1, String d2) {
        if (d1 == null || d2 == null) return false;
        Set<String> easy = Set.of("easy");
        Set<String> medium = Set.of("easy", "medium", "hard");
        Set<String> hard = Set.of("medium", "hard");
        if ("easy".equals(d1)) return medium.contains(d2) && !easy.contains(d2);
        if ("medium".equals(d1)) return true;
        if ("hard".equals(d1)) return medium.contains(d2) && !hard.contains(d2);
        return false;
    }
}
