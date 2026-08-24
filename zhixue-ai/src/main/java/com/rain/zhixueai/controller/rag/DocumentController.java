package com.rain.zhixueai.controller.rag;

import com.rain.zhixueai.dto.rag.ChunkUploadDTO;
import com.rain.zhixueai.dto.rag.ChunkUploadResultDTO;
import com.rain.zhixueai.dto.rag.DocumentDTO;
import com.rain.zhixueai.dto.rag.DocumentContentDTO;
import com.rain.zhixueai.service.rag.DocumentService;
import com.rain.zhixuecommon.entity.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/rag/document")
public class DocumentController {
    
    @Autowired
    private DocumentService documentService;
    
    @PostMapping("/upload")
    public Result upload(
            @RequestParam Long knowledgeBaseId,
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long userId) {
        try {
            if (userId == null) {
                userId = 1L;
            }
            
            DocumentDTO dto = documentService.upload(knowledgeBaseId, file, title, userId);
            log.info("上传文档成功: {}", dto.getTitle());
            return Result.success(dto);
        } catch (Exception e) {
            log.error("上传文档失败", e);
            return Result.wrong("上传文档失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/chunk/init")
    public Result initChunkUpload(@RequestBody ChunkUploadDTO dto) {
        try {
            ChunkUploadResultDTO result = documentService.initChunkUpload(dto);
            if (result.isSuccess()) {
                return Result.success(result);
            } else {
                return Result.wrong(result.getMessage());
            }
        } catch (Exception e) {
            log.error("初始化分片上传失败", e);
            return Result.wrong("初始化分片上传失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/chunk/upload")
    public Result uploadChunk(
            @RequestParam String uploadId,
            @RequestParam Integer chunkIndex,
            @RequestParam MultipartFile chunk) {
        try {
            ChunkUploadResultDTO result = documentService.uploadChunk(uploadId, chunkIndex, chunk);
            if (result.isSuccess()) {
                return Result.success(result);
            } else {
                return Result.wrong(result.getMessage());
            }
        } catch (Exception e) {
            log.error("分片上传失败", e);
            return Result.wrong("分片上传失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/chunk/complete")
    public Result completeChunkUpload(@RequestParam String uploadId) {
        try {
            ChunkUploadResultDTO result = documentService.completeChunkUpload(uploadId);
            if (result.isSuccess()) {
                return Result.success(result);
            } else {
                return Result.wrong(result.getMessage());
            }
        } catch (Exception e) {
            log.error("完成分片上传失败", e);
            return Result.wrong("完成分片上传失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/chunk/progress")
    public Result getUploadProgress(@RequestParam String uploadId) {
        try {
            ChunkUploadResultDTO result = documentService.getUploadProgress(uploadId);
            if (result.isSuccess()) {
                return Result.success(result);
            } else {
                return Result.wrong(result.getMessage());
            }
        } catch (Exception e) {
            log.error("获取上传进度失败", e);
            return Result.wrong("获取上传进度失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/chunk/cancel")
    public Result cancelChunkUpload(@RequestParam String uploadId) {
        try {
            documentService.cancelChunkUpload(uploadId);
            return Result.success(null, "已取消上传");
        } catch (Exception e) {
            log.error("取消分片上传失败", e);
            return Result.wrong("取消分片上传失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        try {
            documentService.delete(id);
            log.info("删除文档成功: {}", id);
            return Result.success(null, "删除成功");
        } catch (Exception e) {
            log.error("删除文档失败", e);
            return Result.wrong("删除文档失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/{id}")
    public Result getById(@PathVariable Long id) {
        try {
            DocumentDTO dto = documentService.getById(id);
            if (dto == null) {
                return Result.wrong("文档不存在");
            }
            return Result.success(dto);
        } catch (Exception e) {
            log.error("获取文档失败", e);
            return Result.wrong("获取文档失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/{id}/content")
    public Result getContent(
            @PathVariable Long id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String search) {
        try {
            DocumentContentDTO content = documentService.getContent(id, page, pageSize, search);
            return Result.success(content);
        } catch (Exception e) {
            log.error("获取文档内容失败", e);
            return Result.wrong("获取文档内容失败: " + e.getMessage());
        }
    }
    
    @GetMapping
    public Result list(
            @RequestParam Long knowledgeBaseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            List<DocumentDTO> list = documentService.list(knowledgeBaseId, page, size);
            int total = documentService.count(knowledgeBaseId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("items", list);
            result.put("total", total);
            result.put("page", page);
            result.put("size", size);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取文档列表失败", e);
            return Result.wrong("获取文档列表失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/{id}/reprocess")
    public Result reprocess(@PathVariable Long id) {
        try {
            documentService.reprocess(id);
            log.info("重新处理文档: {}", id);
            return Result.success(null, "已开始重新处理文档");
        } catch (Exception e) {
            log.error("重新处理文档失败", e);
            return Result.wrong("重新处理文档失败: " + e.getMessage());
        }
    }
}
