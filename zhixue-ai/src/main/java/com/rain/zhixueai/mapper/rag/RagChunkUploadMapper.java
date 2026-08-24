package com.rain.zhixueai.mapper.rag;

import com.rain.zhixueai.entity.rag.RagChunkUpload;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RagChunkUploadMapper {
    
    int insert(RagChunkUpload upload);
    
    RagChunkUpload findByUploadId(@Param("uploadId") String uploadId);
    
    int updateStatus(@Param("uploadId") String uploadId, @Param("status") Integer status);
    
    int deleteByUploadId(@Param("uploadId") String uploadId);
    
    int deleteExpiredUploads(@Param("expireHours") int expireHours);
    
    List<RagChunkUpload> findPendingUploads();
}
