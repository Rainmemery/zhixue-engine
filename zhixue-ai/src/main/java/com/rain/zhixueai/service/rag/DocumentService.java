package com.rain.zhixueai.service.rag;

import com.rain.zhixueai.dto.rag.ChunkUploadDTO;
import com.rain.zhixueai.dto.rag.ChunkUploadResultDTO;
import com.rain.zhixueai.dto.rag.DocumentDTO;
import com.rain.zhixueai.dto.rag.DocumentContentDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {
    
    DocumentDTO upload(Long knowledgeBaseId, MultipartFile file, String title, Long userId);
    
    ChunkUploadResultDTO initChunkUpload(ChunkUploadDTO dto);
    
    ChunkUploadResultDTO uploadChunk(String uploadId, Integer chunkIndex, MultipartFile chunk);
    
    ChunkUploadResultDTO completeChunkUpload(String uploadId);
    
    ChunkUploadResultDTO getUploadProgress(String uploadId);
    
    void cancelChunkUpload(String uploadId);
    
    DocumentDTO getById(Long id);
    
    DocumentContentDTO getContent(Long id, Integer page, Integer pageSize, String searchKeyword);
    
    List<DocumentDTO> list(Long knowledgeBaseId, int page, int size);
    
    int count(Long knowledgeBaseId);
    
    void delete(Long id);
    
    void reprocess(Long id);
    
    void processDocument(Long documentId);
}
