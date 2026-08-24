package com.rain.zhixueadmin.service;

import com.rain.zhixueadmin.dto.PageResult;
import com.rain.zhixueadmin.entity.BackupRecord;

import java.util.List;

public interface BackupService {
    BackupRecord createBackup(String backupType, String createdBy);
    
    BackupRecord updateBackup(BackupRecord record);
    
    void deleteBackup(Long id);
    
    BackupRecord getBackupById(Long id);
    
    List<BackupRecord> getAllBackups();
    
    String getDownloadUrl(Long id);
    
    PageResult<BackupRecord> getBackupList(String type, String status, Integer page, Integer size);
}
