package com.rain.zhixueai.dto.rag;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChunkUploadResultDTO {
    
    private boolean success;
    
    private String message;
    
    private String uploadId;
    
    private Integer chunkIndex;
    
    private Integer totalChunks;
    
    private Integer uploadedChunks;
    
    private boolean completed;
    
    private DocumentDTO document;
    
    private List<Integer> missingChunks;
    
    private Integer progress;
    
    public static ChunkUploadResultDTO success(String uploadId, int chunkIndex, int totalChunks) {
        return ChunkUploadResultDTO.builder()
                .success(true)
                .uploadId(uploadId)
                .chunkIndex(chunkIndex)
                .totalChunks(totalChunks)
                .uploadedChunks(chunkIndex + 1)
                .progress((int) ((chunkIndex + 1) * 100.0 / totalChunks))
                .completed(false)
                .message("分片上传成功")
                .build();
    }
    
    public static ChunkUploadResultDTO completed(DocumentDTO document) {
        return ChunkUploadResultDTO.builder()
                .success(true)
                .completed(true)
                .document(document)
                .progress(100)
                .message("文件上传完成")
                .build();
    }
    
    public static ChunkUploadResultDTO error(String message) {
        return ChunkUploadResultDTO.builder()
                .success(false)
                .message(message)
                .build();
    }
}
