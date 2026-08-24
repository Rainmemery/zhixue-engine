package com.rain.zhixueproblem.service;

import com.rain.zhixueproblem.entity.ZhixueProblemTestcase;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileUploadService {
    List<ZhixueProblemTestcase> uploadTestcaseFiles(Long problemId, List<MultipartFile> files, Integer defaultTimeLimitMs, Integer defaultMemoryLimitMb);
}
