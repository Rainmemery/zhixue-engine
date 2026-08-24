package com.rain.zhixueadmin.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupRecord {
    private Long id;
    private String fileName;
    private Long fileSize;
    private String backupType;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String createdBy;
    private String downloadUrl;
}
