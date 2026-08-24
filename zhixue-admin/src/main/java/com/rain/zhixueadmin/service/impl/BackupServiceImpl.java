package com.rain.zhixueadmin.service.impl;

import com.rain.zhixueadmin.dto.PageResult;
import com.rain.zhixueadmin.entity.BackupRecord;
import com.rain.zhixueadmin.mapper.BackupRecordMapper;
import com.rain.zhixueadmin.service.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackupServiceImpl implements BackupService {
    
    private final BackupRecordMapper backupRecordMapper;

    @Override
    public BackupRecord createBackup(String backupType, String createdBy) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "backup_" + backupType + "_" + timestamp + ".sql";
        
        BackupRecord record = BackupRecord.builder()
                .fileName(fileName)
                .backupType(backupType)
                .status("pending")
                .startTime(LocalDateTime.now())
                .createdBy(createdBy)
                .build();
        
        backupRecordMapper.insert(record);
        
        try {
            Thread.sleep(1000);
            
            record.setStatus("completed");
            record.setEndTime(LocalDateTime.now());
            record.setFileSize(1024L * 1024);
            record.setDownloadUrl("/api/v1/admin/backups/" + record.getId() + "/download");
            backupRecordMapper.updateById(record);
        } catch (Exception e) {
            log.error("Backup failed", e);
            record.setStatus("failed");
            record.setEndTime(LocalDateTime.now());
            backupRecordMapper.updateById(record);
        }
        
        return backupRecordMapper.selectById(record.getId());
    }

    @Override
    public BackupRecord updateBackup(BackupRecord record) {
        backupRecordMapper.updateById(record);
        return backupRecordMapper.selectById(record.getId());
    }

    @Override
    public void deleteBackup(Long id) {
        backupRecordMapper.deleteById(id);
    }

    @Override
    public BackupRecord getBackupById(Long id) {
        return backupRecordMapper.selectById(id);
    }

    @Override
    public List<BackupRecord> getAllBackups() {
        return backupRecordMapper.selectAll();
    }

    @Override
    public String getDownloadUrl(Long id) {
        BackupRecord record = backupRecordMapper.selectById(id);
        if (record == null || !"completed".equals(record.getStatus())) {
            return null;
        }
        return record.getDownloadUrl();
    }

    @Override
    public PageResult<BackupRecord> getBackupList(String type, String status, Integer page, Integer size) {
        int offset = (page - 1) * size;
        List<BackupRecord> items = backupRecordMapper.selectPage(offset, size, type, status);
        long total = backupRecordMapper.countAll(type, status);
        return PageResult.<BackupRecord>builder()
                .items(items)
                .total(total)
                .page(page)
                .size(size)
                .build();
    }
}
