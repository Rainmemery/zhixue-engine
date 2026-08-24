package com.rain.zhixueadmin.mapper;

import com.rain.zhixueadmin.entity.BackupRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BackupRecordMapper {
    int insert(BackupRecord record);
    
    int updateById(BackupRecord record);
    
    int deleteById(@Param("id") Long id);
    
    BackupRecord selectById(@Param("id") Long id);
    
    List<BackupRecord> selectAll();
    
    List<BackupRecord> selectPage(@Param("offset") int offset, @Param("size") int size, @Param("type") String type, @Param("status") String status);
    
    long countAll(@Param("type") String type, @Param("status") String status);
}
