package com.rain.zhixueproblem.service.impl;

import com.rain.zhixueproblem.entity.ZhixueProblemTestcase;
import com.rain.zhixueproblem.mapper.ZhixueProblemTestcaseMapper;
import com.rain.zhixueproblem.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadServiceImpl implements FileUploadService {

    private final ZhixueProblemTestcaseMapper zhixueProblemTestcaseMapper;

    @Value("${zhixue.testcase.path:/data/testcases}")
    private String basePath;

    @Override
    public List<ZhixueProblemTestcase> uploadTestcaseFiles(Long problemId, List<MultipartFile> files, Integer defaultTimeLimitMs, Integer defaultMemoryLimitMb) {
        Path problemDir = Paths.get(basePath, String.valueOf(problemId));
        try {
            Files.createDirectories(problemDir);
        } catch (IOException e) {
            throw new RuntimeException("创建测试用例目录失败", e);
        }

        Map<String, MultipartFile> inFileMap = new HashMap<>();
        Map<String, MultipartFile> outFileMap = new HashMap<>();

        for (MultipartFile file : files) {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                continue;
            }
            if (originalFilename.endsWith(".in")) {
                String name = originalFilename.substring(0, originalFilename.length() - 3);
                inFileMap.put(name, file);
            } else if (originalFilename.endsWith(".out")) {
                String name = originalFilename.substring(0, originalFilename.length() - 4);
                outFileMap.put(name, file);
            }
        }

        List<ZhixueProblemTestcase> createdTestcases = new ArrayList<>();
        int sortOrder = (int) zhixueProblemTestcaseMapper.countByProblemId(problemId);

        for (String name : inFileMap.keySet()) {
            MultipartFile inFile = inFileMap.get(name);
            MultipartFile outFile = outFileMap.get(name);

            if (outFile == null) {
                continue;
            }

            String inputFileName = name + ".in";
            String outputFileName = name + ".out";
            Path inputPath = problemDir.resolve(inputFileName);
            Path outputPath = problemDir.resolve(outputFileName);

            try {
                inFile.transferTo(inputPath.toFile());
                outFile.transferTo(outputPath.toFile());
            } catch (IOException e) {
                throw new RuntimeException("保存测试用例文件失败: " + name, e);
            }

            ZhixueProblemTestcase testcase = new ZhixueProblemTestcase();
            testcase.setProblemId(problemId);
            testcase.setTestcaseName(name);
            testcase.setInputFilePath(inputPath.toString());
            testcase.setOutputFilePath(outputPath.toString());
            testcase.setInputFileSize(inputPath.toFile().length());
            testcase.setOutputFileSize(outputPath.toFile().length());
            testcase.setTimeLimitMs(defaultTimeLimitMs);
            testcase.setMemoryLimitMb(defaultMemoryLimitMb);
            testcase.setIsSample(false);
            testcase.setSortOrder(sortOrder++);

            zhixueProblemTestcaseMapper.insert(testcase);
            createdTestcases.add(testcase);
        }

        return createdTestcases;
    }
}
