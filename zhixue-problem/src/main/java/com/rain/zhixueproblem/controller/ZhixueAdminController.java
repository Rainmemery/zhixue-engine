package com.rain.zhixueproblem.controller;

import com.alibaba.fastjson2.JSON;
import com.rain.zhixuecommon.entity.Result;
import com.rain.zhixueproblem.entity.ZhixueProblem;
import com.rain.zhixueproblem.entity.ZhixueProblemCategory;
import com.rain.zhixueproblem.entity.ZhixueProblemSample;
import com.rain.zhixueproblem.entity.ZhixueProblemTag;
import com.rain.zhixueproblem.entity.ZhixueProblemTestcase;
import com.rain.zhixueproblem.service.ZhixueProblemService;
import com.rain.zhixueproblem.service.ZhixueProblemCategoryService;
import com.rain.zhixueproblem.service.ZhixueProblemTagService;
import com.rain.zhixueproblem.service.TestcaseService;
import com.rain.zhixueproblem.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/problems")
@RequiredArgsConstructor
public class ZhixueAdminController {

    private final ZhixueProblemService zhixueProblemService;
    private final ZhixueProblemCategoryService zhixueProblemCategoryService;
    private final ZhixueProblemTagService zhixueProblemTagService;
    private final TestcaseService testcaseService;
    private final FileUploadService fileUploadService;

    @GetMapping
    public Result getAdminProblemList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String problemType,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isPublic) {
        return Result.success(zhixueProblemService.getAdminProblemList(page, size, difficulty, categoryId, problemType, search, isPublic));
    }

    @GetMapping("/{id}")
    public Result getAdminProblemDetail(@PathVariable Long id) {
        return Result.success(zhixueProblemService.getProblemDetail(id, null));
    }

    @PostMapping
    public Result createProblem(@RequestBody Map<String, Object> body) {
        ZhixueProblem problem = parseProblemFromBody(body);
        List<ZhixueProblemSample> samples = parseSamplesFromBody(body);
        @SuppressWarnings("unchecked")
        List<Number> tagIdNumbers = (List<Number>) body.get("tagIds");
        List<Long> tagIds = tagIdNumbers != null ? new ArrayList<>(tagIdNumbers.stream().map(Number::longValue).toList()) : new ArrayList<>();
        return Result.success(zhixueProblemService.createProblem(problem, samples, tagIds));
    }

    @PutMapping("/{id}")
    public Result updateProblem(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ZhixueProblem problem = parseProblemFromBody(body);
        List<ZhixueProblemSample> samples = parseSamplesFromBody(body);
        @SuppressWarnings("unchecked")
        List<Number> tagIdNumbers = (List<Number>) body.get("tagIds");
        List<Long> tagIds = tagIdNumbers != null ? new ArrayList<>(tagIdNumbers.stream().map(Number::longValue).toList()) : new ArrayList<>();
        return Result.success(zhixueProblemService.updateProblem(id, problem, samples, tagIds));
    }

    @DeleteMapping("/{id}")
    public Result deleteProblem(@PathVariable Long id) {
        zhixueProblemService.deleteProblem(id);
        return Result.success(null);
    }

    @PostMapping("/batch-delete")
    public Result batchDeleteProblems(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.get("ids");
        zhixueProblemService.batchDeleteProblems(ids);
        return Result.success(null);
    }

    @PostMapping("/{problemId}/testcases/upload")
    public Result uploadTestcases(
            @PathVariable Long problemId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(required = false) Integer defaultTimeLimitMs,
            @RequestParam(required = false) Integer defaultMemoryLimitMb) {
        return Result.success(fileUploadService.uploadTestcaseFiles(problemId, files, defaultTimeLimitMs, defaultMemoryLimitMb));
    }

    @GetMapping("/{problemId}/testcases")
    public Result getTestcases(@PathVariable Long problemId) {
        return Result.success(testcaseService.getTestcasesByProblemId(problemId));
    }

    @PutMapping("/{problemId}/testcases/{testcaseId}")
    public Result updateTestcase(
            @PathVariable Long problemId,
            @PathVariable Long testcaseId,
            @RequestBody Map<String, Integer> body) {
        ZhixueProblemTestcase testcase = new ZhixueProblemTestcase();
        testcase.setId(testcaseId);
        testcase.setProblemId(problemId);
        if (body.containsKey("timeLimitMs")) {
            testcase.setTimeLimitMs(body.get("timeLimitMs"));
        }
        if (body.containsKey("memoryLimitMb")) {
            testcase.setMemoryLimitMb(body.get("memoryLimitMb"));
        }
        testcaseService.updateTestcase(testcase);
        return Result.success(testcase);
    }

    @DeleteMapping("/{problemId}/testcases/{testcaseId}")
    public Result deleteTestcase(@PathVariable Long problemId, @PathVariable Long testcaseId) {
        testcaseService.deleteTestcase(testcaseId);
        return Result.success(null);
    }

    @PostMapping("/{problemId}/testcases/batch-delete")
    public Result batchDeleteTestcases(@PathVariable Long problemId, @RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.get("ids");
        testcaseService.batchDeleteTestcases(ids);
        return Result.success(null);
    }

    @GetMapping("/categories")
    public Result getAllCategories() {
        return Result.success(zhixueProblemCategoryService.getAllCategories());
    }

    @PostMapping("/categories")
    public Result createCategory(@RequestBody ZhixueProblemCategory category) {
        return Result.success(zhixueProblemCategoryService.createCategory(category));
    }

    @PutMapping("/categories/{id}")
    public Result updateCategory(@PathVariable Long id, @RequestBody ZhixueProblemCategory category) {
        return Result.success(zhixueProblemCategoryService.updateCategory(id, category));
    }

    @DeleteMapping("/categories/{id}")
    public Result deleteCategory(@PathVariable Long id) {
        zhixueProblemCategoryService.deleteCategory(id);
        return Result.success(null);
    }

    @GetMapping("/tags")
    public Result getAllTags() {
        return Result.success(zhixueProblemTagService.getAllTags());
    }

    @PostMapping("/tags")
    public Result createTag(@RequestBody ZhixueProblemTag tag) {
        return Result.success(zhixueProblemTagService.createTag(tag));
    }

    @PutMapping("/tags/{id}")
    public Result updateTag(@PathVariable Long id, @RequestBody ZhixueProblemTag tag) {
        return Result.success(zhixueProblemTagService.updateTag(id, tag));
    }

    @DeleteMapping("/tags/{id}")
    public Result deleteTag(@PathVariable Long id) {
        zhixueProblemTagService.deleteTag(id);
        return Result.success(null);
    }

    @SuppressWarnings("unchecked")
    private List<ZhixueProblemSample> parseSamplesFromBody(Map<String, Object> body) {
        List<Map<String, Object>> sampleMaps = (List<Map<String, Object>>) body.get("samples");
        if (sampleMaps == null) return new ArrayList<>();
        List<ZhixueProblemSample> samples = new ArrayList<>();
        for (int i = 0; i < sampleMaps.size(); i++) {
            Map<String, Object> sm = sampleMaps.get(i);
            ZhixueProblemSample sample = new ZhixueProblemSample();
            sample.setSampleInput((String) sm.get("input"));
            sample.setSampleOutput((String) sm.get("output"));
            sample.setExplanation((String) sm.get("explanation"));
            if (sm.containsKey("sortOrder")) {
                sample.setSortOrder(toInteger(sm.get("sortOrder")));
            } else {
                sample.setSortOrder(i);
            }
            samples.add(sample);
        }
        return samples;
    }

    private ZhixueProblem parseProblemFromBody(Map<String, Object> body) {
        ZhixueProblem problem = new ZhixueProblem();
        if (body.containsKey("title")) problem.setTitle((String) body.get("title"));
        if (body.containsKey("titleEn")) problem.setTitleEn((String) body.get("titleEn"));
        if (body.containsKey("description")) problem.setDescription((String) body.get("description"));
        if (body.containsKey("inputDescription")) problem.setInputDescription((String) body.get("inputDescription"));
        if (body.containsKey("outputDescription")) problem.setOutputDescription((String) body.get("outputDescription"));
        if (body.containsKey("hint")) problem.setHint((String) body.get("hint"));
        if (body.containsKey("source")) problem.setSource((String) body.get("source"));
        if (body.containsKey("difficulty")) problem.setDifficulty((String) body.get("difficulty"));
        if (body.containsKey("problemType")) problem.setProblemType((String) body.get("problemType"));
        if (body.containsKey("categoryId")) problem.setCategoryId(toLong(body.get("categoryId")));
        if (body.containsKey("timeLimitMs")) problem.setTimeLimitMs(toInteger(body.get("timeLimitMs")));
        if (body.containsKey("memoryLimitMb")) problem.setMemoryLimitMb(toInteger(body.get("memoryLimitMb")));
        if (body.containsKey("templateCode")) problem.setTemplateCode(toJsonString(body.get("templateCode")));
        if (body.containsKey("solutionCode")) problem.setSolutionCode(toJsonString(body.get("solutionCode")));
        if (body.containsKey("isPublic")) problem.setIsPublic((Boolean) body.get("isPublic"));
        if (body.containsKey("sortOrder")) problem.setSortOrder(toInteger(body.get("sortOrder")));
        if (body.containsKey("createdBy")) problem.setCreatedBy(toLong(body.get("createdBy")));
        return problem;
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString());
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    private String toJsonString(Object value) {
        if (value == null) return null;
        if (value instanceof String) return (String) value;
        try {
            return JSON.toJSONString(value);
        } catch (Exception e) {
            return value.toString();
        }
    }
}
