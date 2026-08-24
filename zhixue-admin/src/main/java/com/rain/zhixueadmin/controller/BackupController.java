package com.rain.zhixueadmin.controller;

import com.rain.zhixueadmin.dto.PageResult;
import com.rain.zhixueadmin.entity.BackupRecord;
import com.rain.zhixueadmin.service.BackupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/backups")
@RequiredArgsConstructor
public class BackupController {
    
    private final BackupService backupService;

    @GetMapping
    public Map<String, Object> getBackupList(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        PageResult<BackupRecord> result = backupService.getBackupList(type, status, page, size);
        return Map.of(
            "code", 200,
            "message", "success",
            "data", Map.of(
                "items", result.getItems(),
                "total", result.getTotal(),
                "page", result.getPage(),
                "size", result.getSize()
            )
        );
    }

    @GetMapping("/{id}")
    public Map<String, Object> getBackupById(@PathVariable Long id) {
        BackupRecord backup = backupService.getBackupById(id);
        if (backup == null) {
            return Map.of("code", 404, "message", "备份不存在");
        }
        return Map.of("code", 200, "message", "success", "data", backup);
    }

    @PostMapping
    public Map<String, Object> createBackup(@RequestBody Map<String, String> request) {
        String type = request.getOrDefault("type", "manual");
        String createdBy = request.getOrDefault("createdBy", "admin");
        BackupRecord backup = backupService.createBackup(type, createdBy);
        return Map.of("code", 200, "message", "备份创建成功", "data", backup);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteBackup(@PathVariable Long id) {
        backupService.deleteBackup(id);
        return Map.of("code", 200, "message", "删除成功");
    }

    @GetMapping("/{id}/download")
    public Map<String, Object> getDownloadUrl(@PathVariable Long id) {
        String url = backupService.getDownloadUrl(id);
        if (url == null) {
            return Map.of("code", 404, "message", "备份文件不可下载");
        }
        return Map.of("code", 200, "message", "success", "data", Map.of("downloadUrl", url));
    }
}
