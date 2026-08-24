package com.rain.zhixueai.service.rag.impl;

import com.rain.zhixueai.config.RagProperties;
import com.rain.zhixueai.dto.rag.ChunkUploadDTO;
import com.rain.zhixueai.dto.rag.ChunkUploadResultDTO;
import com.rain.zhixueai.dto.rag.DocumentDTO;
import com.rain.zhixueai.dto.rag.DocumentContentDTO;
import com.rain.zhixueai.entity.rag.RagChunkUpload;
import com.rain.zhixueai.entity.rag.RagDocument;
import com.rain.zhixueai.entity.rag.RagEmbeddingModel;
import com.rain.zhixueai.entity.rag.RagKnowledgeBase;
import com.rain.zhixueai.enums.rag.DocumentStatus;
import com.rain.zhixueai.mapper.rag.RagChunkUploadMapper;
import com.rain.zhixueai.mapper.rag.RagDocumentMapper;
import com.rain.zhixueai.mapper.rag.RagEmbeddingModelMapper;
import com.rain.zhixueai.mapper.rag.RagKnowledgeBaseMapper;
import com.rain.zhixueai.processor.DocumentProcessorFactory;
import com.rain.zhixueai.service.rag.DocumentService;
import com.rain.zhixueai.service.rag.EmbeddingService;
import com.rain.zhixueai.service.rag.RetrievalService;
import com.rain.zhixueai.util.TextChunker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {
    
    private static final long DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024;
    private static final int CONTENT_CACHE_MAX_SIZE = 100;
    private static final long CONTENT_CACHE_EXPIRE_MS = 600000;
    
    private final ConcurrentHashMap<Long, ContentCacheEntry> contentCache = new ConcurrentHashMap<>();
    
    private static class ContentCacheEntry {
        final String content;
        final long timestamp;
        
        ContentCacheEntry(String content) {
            this.content = content;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CONTENT_CACHE_EXPIRE_MS;
        }
    }
    
    @Autowired
    private RagDocumentMapper documentMapper;
    
    @Autowired
    private RagKnowledgeBaseMapper knowledgeBaseMapper;
    
    @Autowired
    private RagEmbeddingModelMapper embeddingModelMapper;
    
    @Autowired
    private DocumentProcessorFactory processorFactory;
    
    @Autowired
    private EmbeddingService embeddingService;
    
    @Autowired
    private RetrievalService retrievalService;
    
    @Autowired
    private RagProperties ragProperties;
    
    @Autowired
    private RagChunkUploadMapper chunkUploadMapper;
    
    @Override
    @Transactional
    public DocumentDTO upload(Long knowledgeBaseId, MultipartFile file, String title, Long userId) {
        long startTime = System.currentTimeMillis();
        
        RagKnowledgeBase kb = knowledgeBaseMapper.findById(knowledgeBaseId);
        if (kb == null) {
            throw new IllegalArgumentException("知识库不存在: " + knowledgeBaseId);
        }
        
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String fileType = extension.toUpperCase();
        
        if (!processorFactory.isSupported(fileType)) {
            throw new IllegalArgumentException("不支持的文件类型: " + fileType);
        }
        
        if (file.getSize() > ragProperties.getFile().getMaxSize()) {
            throw new IllegalArgumentException("文件大小超过限制: " + (ragProperties.getFile().getMaxSize() / 1024 / 1024) + "MB");
        }
        
        String storageFileName = UUID.randomUUID().toString() + "." + extension;
        Path uploadPath = Paths.get(ragProperties.getFile().getUploadPath()).toAbsolutePath().normalize();
        
        try {
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                log.info("Created upload directory: {}", uploadPath);
            }
        } catch (IOException e) {
            log.error("Failed to create upload directory: {}", uploadPath, e);
            throw new RuntimeException("创建上传目录失败: " + e.getMessage(), e);
        }
        
        if (!Files.isWritable(uploadPath)) {
            throw new RuntimeException("上传目录不可写: " + uploadPath);
        }
        
        Path filePath = uploadPath.resolve(storageFileName);
        
        try {
            Files.copy(file.getInputStream(), filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("Saved file to: {}", filePath);
        } catch (IOException e) {
            log.error("Failed to save file: {}", filePath, e);
            throw new RuntimeException("保存文件失败: " + e.getMessage(), e);
        }
        
        if (title == null || title.isEmpty()) {
            title = originalFilename;
        }
        
        RagDocument document = RagDocument.builder()
                .knowledgeBaseId(knowledgeBaseId)
                .title(title)
                .fileName(originalFilename)
                .fileType(fileType)
                .fileSize(file.getSize())
                .filePath(filePath.toString())
                .chunkCount(0)
                .status(DocumentStatus.PENDING.getCode())
                .createdBy(userId)
                .build();
        
        documentMapper.insert(document);
        knowledgeBaseMapper.incrementDocumentCount(knowledgeBaseId);
        
        log.info("Uploaded document: {} to knowledge base: {} in {}ms", 
                document.getTitle(), kb.getName(), System.currentTimeMillis() - startTime);
        
        processDocumentAsync(document.getId());
        
        return toDTO(document);
    }
    
    @Async("documentProcessingExecutor")
    protected void processDocumentAsync(Long documentId) {
        long startTime = System.currentTimeMillis();
        try {
            processDocument(documentId);
            log.info("Document processing completed in {}ms", System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Failed to process document: {} in {}ms", documentId, System.currentTimeMillis() - startTime, e);
        }
    }
    
    @Override
    @Transactional
    public void processDocument(Long documentId) {
        long startTime = System.currentTimeMillis();
        
        RagDocument document = documentMapper.findById(documentId);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在: " + documentId);
        }
        
        documentMapper.updateStatus(documentId, DocumentStatus.PROCESSING.getCode(), null);
        log.info("Processing document: {} ({})", document.getTitle(), document.getFileType());
        
        try {
            RagKnowledgeBase kb = knowledgeBaseMapper.findById(document.getKnowledgeBaseId());
            if (kb == null) {
                throw new IllegalStateException("知识库不存在: " + document.getKnowledgeBaseId());
            }
            
            Path filePath = Paths.get(document.getFilePath());
            if (!Files.exists(filePath)) {
                throw new IllegalStateException("文件不存在: " + document.getFilePath());
            }
            
            if (!Files.isReadable(filePath)) {
                throw new IllegalStateException("文件不可读，请检查文件权限: " + document.getFilePath());
            }
            
            long extractStart = System.currentTimeMillis();
            String text;
            try (InputStream inputStream = Files.newInputStream(filePath)) {
                text = processorFactory.extractText(document.getFileType(), inputStream);
            } catch (OutOfMemoryError e) {
                throw new IllegalStateException("文件过大，内存不足: " + e.getMessage());
            } catch (IOException e) {
                throw new IllegalStateException("文件读取失败: " + e.getMessage(), e);
            }
            log.info("Text extraction completed in {}ms, text length: {}", 
                    System.currentTimeMillis() - extractStart, text != null ? text.length() : 0);
            
            if (text == null || text.trim().isEmpty()) {
                throw new IllegalStateException("文档内容为空，可能文件格式不正确或文件已损坏");
            }
            
            long chunkStart = System.currentTimeMillis();
            TextChunker chunker = new TextChunker(
                    ragProperties.getChunk().getSize(),
                    ragProperties.getChunk().getOverlap());
            
            List<String> chunks = chunker.chunkWithMetadata(text, document.getTitle());
            log.info("Text chunking completed in {}ms, chunks: {}", 
                    System.currentTimeMillis() - chunkStart, chunks.size());
            
            if (chunks.isEmpty()) {
                throw new IllegalStateException("文档分块失败，未生成任何内容块");
            }
            
            String modelName = null;
            if (kb.getEmbeddingModelId() != null) {
                RagEmbeddingModel embeddingModel = embeddingModelMapper.findById(kb.getEmbeddingModelId());
                if (embeddingModel != null && embeddingModel.getStatus()) {
                    modelName = embeddingModel.getModelName();
                    log.info("Using embedding model: {} (dimension: {})", modelName, embeddingModel.getDimension());
                } else {
                    log.warn("Embedding model not found or disabled for knowledge base: {}, using default", kb.getId());
                }
            }
            
            long embedStart = System.currentTimeMillis();
            List<float[]> embeddings = embeddingService.batchEmbed(chunks, modelName);
            log.info("Embedding completed in {}ms for {} chunks", 
                    System.currentTimeMillis() - embedStart, chunks.size());
            
            long storeStart = System.currentTimeMillis();
            retrievalService.storeEmbeddings(kb.getId(), documentId, chunks, embeddings);
            log.info("Vector storage completed in {}ms", System.currentTimeMillis() - storeStart);
            
            documentMapper.updateStatus(documentId, DocumentStatus.COMPLETED.getCode(), null);
            documentMapper.incrementChunkCount(documentId, chunks.size());
            knowledgeBaseMapper.incrementChunkCount(kb.getId(), chunks.size());
            
            log.info("Document processed successfully: {} with {} chunks in {}ms", 
                    document.getTitle(), chunks.size(), System.currentTimeMillis() - startTime);
            
        } catch (Exception e) {
            log.error("Failed to process document: {} in {}ms", documentId, System.currentTimeMillis() - startTime, e);
            documentMapper.updateStatus(documentId, DocumentStatus.FAILED.getCode(), 
                    e.getMessage() != null && e.getMessage().length() > 500 ? 
                    e.getMessage().substring(0, 500) : e.getMessage());
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Failed to process document: " + e.getMessage(), e);
        }
    }
    
    @Override
    public DocumentDTO getById(Long id) {
        RagDocument document = documentMapper.findById(id);
        return document != null ? toDTO(document) : null;
    }
    
    @Override
    public DocumentContentDTO getContent(Long id, Integer page, Integer pageSize, String searchKeyword) {
        RagDocument document = documentMapper.findById(id);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在: " + id);
        }
        
        if (document.getStatus() != DocumentStatus.COMPLETED.getCode()) {
            throw new IllegalStateException("文档尚未处理完成");
        }
        
        Path filePath = Paths.get(document.getFilePath());
        if (!Files.exists(filePath)) {
            throw new IllegalStateException("文件不存在: " + document.getFilePath());
        }
        
        try {
            String fullText = getDocumentContentWithCache(id, document, filePath);
            
            if (fullText == null || fullText.trim().isEmpty()) {
                throw new IllegalStateException("文档内容为空");
            }
            
            if (page == null || page < 1) {
                page = 1;
            }
            if (pageSize == null || pageSize < 1) {
                pageSize = 1000;
            }
            
            List<DocumentContentDTO.ContentPage> pages = splitIntoPages(fullText, pageSize);
            int totalPages = pages.size();
            
            if (page > totalPages) {
                page = totalPages;
            }
            
            List<DocumentContentDTO.SearchMatch> searchMatches = null;
            int totalMatches = 0;
            
            if (searchKeyword != null && !searchKeyword.trim().isEmpty()) {
                searchMatches = findSearchMatches(fullText, searchKeyword.trim(), pages);
                totalMatches = searchMatches.size();
            }
            
            return DocumentContentDTO.builder()
                    .id(document.getId())
                    .title(document.getTitle())
                    .fileName(document.getFileName())
                    .fileType(document.getFileType())
                    .fileSize(document.getFileSize())
                    .fullContent(fullText.length() > 100000 ? null : fullText)
                    .pages(pages)
                    .totalPages(totalPages)
                    .currentPage(page)
                    .pageSize(pageSize)
                    .searchKeyword(searchKeyword)
                    .searchMatches(searchMatches)
                    .totalMatches(totalMatches)
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to get document content: {}", id, e);
            throw new RuntimeException("获取文档内容失败: " + e.getMessage(), e);
        }
    }
    
    private String getDocumentContentWithCache(Long documentId, RagDocument document, Path filePath) throws Exception {
        ContentCacheEntry cached = contentCache.get(documentId);
        if (cached != null && !cached.isExpired()) {
            log.debug("Content cache hit for document: {}", documentId);
            return cached.content;
        }
        
        log.debug("Content cache miss for document: {}, extracting from file", documentId);
        String fullText;
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            fullText = processorFactory.extractText(document.getFileType(), inputStream);
        }
        
        if (fullText != null && !fullText.trim().isEmpty()) {
            if (contentCache.size() < CONTENT_CACHE_MAX_SIZE) {
                contentCache.put(documentId, new ContentCacheEntry(fullText));
                log.debug("Cached content for document: {}", documentId);
            }
        }
        
        return fullText;
    }
    
    @Scheduled(fixedRate = 300000)
    public void cleanupContentCache() {
        int removedCount = 0;
        
        for (Map.Entry<Long, ContentCacheEntry> entry : contentCache.entrySet()) {
            if (entry.getValue().isExpired()) {
                contentCache.remove(entry.getKey());
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            log.debug("Cleaned up {} expired content cache entries", removedCount);
        }
    }
    
    public void clearContentCache(Long documentId) {
        if (documentId != null) {
            contentCache.remove(documentId);
            log.debug("Cleared content cache for document: {}", documentId);
        } else {
            contentCache.clear();
            log.info("Cleared all content cache");
        }
    }
    
    private List<DocumentContentDTO.ContentPage> splitIntoPages(String text, int pageSize) {
        List<DocumentContentDTO.ContentPage> pages = new ArrayList<>();
        
        String[] paragraphs = text.split("\n\n");
        StringBuilder currentPage = new StringBuilder();
        int currentPageNumber = 1;
        int startOffset = 0;
        int currentOffset = 0;
        
        for (String paragraph : paragraphs) {
            if (currentPage.length() + paragraph.length() > pageSize && currentPage.length() > 0) {
                pages.add(DocumentContentDTO.ContentPage.builder()
                        .pageNumber(currentPageNumber)
                        .content(currentPage.toString().trim())
                        .startOffset(startOffset)
                        .endOffset(currentOffset)
                        .build());
                
                currentPageNumber++;
                startOffset = currentOffset;
                currentPage = new StringBuilder();
            }
            
            if (currentPage.length() > 0) {
                currentPage.append("\n\n");
            }
            currentPage.append(paragraph);
            currentOffset += paragraph.length() + 2;
        }
        
        if (currentPage.length() > 0) {
            pages.add(DocumentContentDTO.ContentPage.builder()
                    .pageNumber(currentPageNumber)
                    .content(currentPage.toString().trim())
                    .startOffset(startOffset)
                    .endOffset(currentOffset)
                    .build());
        }
        
        return pages;
    }
    
    private List<DocumentContentDTO.SearchMatch> findSearchMatches(String text, String keyword, List<DocumentContentDTO.ContentPage> pages) {
        List<DocumentContentDTO.SearchMatch> matches = new ArrayList<>();
        String lowerText = text.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();
        
        int index = 0;
        while ((index = lowerText.indexOf(lowerKeyword, index)) != -1) {
            int pageNumber = 1;
            for (DocumentContentDTO.ContentPage p : pages) {
                if (index >= p.getStartOffset() && index < p.getEndOffset()) {
                    pageNumber = p.getPageNumber();
                    break;
                }
            }
            
            int contextStart = Math.max(0, index - 50);
            int contextEnd = Math.min(text.length(), index + keyword.length() + 50);
            String context = text.substring(contextStart, contextEnd);
            
            matches.add(DocumentContentDTO.SearchMatch.builder()
                    .pageNumber(pageNumber)
                    .startOffset(index)
                    .endOffset(index + keyword.length())
                    .context(context)
                    .build());
            
            index += keyword.length();
        }
        
        return matches;
    }
    
    @Override
    public List<DocumentDTO> list(Long knowledgeBaseId, int page, int size) {
        int offset = (page - 1) * size;
        return documentMapper.findByKnowledgeBaseIdWithPaging(knowledgeBaseId, offset, size).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public int count(Long knowledgeBaseId) {
        return documentMapper.countByKnowledgeBaseId(knowledgeBaseId);
    }
    
    @Override
    @Transactional
    public void delete(Long id) {
        RagDocument document = documentMapper.findById(id);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在: " + id);
        }
        
        retrievalService.deleteEmbeddingsByDocument(id);
        
        int chunkCount = document.getChunkCount() != null ? document.getChunkCount() : 0;
        knowledgeBaseMapper.decrementChunkCount(document.getKnowledgeBaseId(), chunkCount);
        knowledgeBaseMapper.decrementDocumentCount(document.getKnowledgeBaseId());
        
        if (document.getFilePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(document.getFilePath()));
            } catch (IOException e) {
                log.warn("Failed to delete file: {}", document.getFilePath(), e);
            }
        }
        
        documentMapper.delete(id);
        log.info("Deleted document: {}", document.getTitle());
    }
    
    @Override
    public void reprocess(Long id) {
        RagDocument document = documentMapper.findById(id);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在: " + id);
        }
        
        retrievalService.deleteEmbeddingsByDocument(id);
        
        document.setChunkCount(0);
        documentMapper.update(document);
        
        processDocumentAsync(id);
    }
    
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }
    
    private DocumentDTO toDTO(RagDocument document) {
        DocumentDTO dto = new DocumentDTO();
        BeanUtils.copyProperties(document, dto);
        
        dto.setStatusText(DocumentStatus.fromCode(document.getStatus()).getDescription());
        
        RagKnowledgeBase kb = knowledgeBaseMapper.findById(document.getKnowledgeBaseId());
        if (kb != null) {
            dto.setKnowledgeBaseName(kb.getName());
        }
        
        return dto;
    }
    
    @Override
    @Transactional
    public ChunkUploadResultDTO initChunkUpload(ChunkUploadDTO dto) {
        try {
            Long knowledgeBaseId = Long.parseLong(dto.getKnowledgeBaseId());
            RagKnowledgeBase kb = knowledgeBaseMapper.findById(knowledgeBaseId);
            if (kb == null) {
                return ChunkUploadResultDTO.error("知识库不存在: " + knowledgeBaseId);
            }
            
            String fileType = dto.getFileType().toUpperCase();
            if (!processorFactory.isSupported(fileType)) {
                return ChunkUploadResultDTO.error("不支持的文件类型: " + fileType);
            }
            
            if (dto.getFileSize() > ragProperties.getFile().getMaxSize()) {
                return ChunkUploadResultDTO.error("文件大小超过限制: " + (ragProperties.getFile().getMaxSize() / 1024 / 1024) + "MB");
            }
            
            String uploadId = UUID.randomUUID().toString().replace("-", "");
            Path tempDir = Paths.get(ragProperties.getFile().getUploadPath(), "temp", uploadId);
            Files.createDirectories(tempDir);
            
            String extension = getFileExtension(dto.getFileName());
            Path tempFile = tempDir.resolve("original." + extension);
            
            try (RandomAccessFile raf = new RandomAccessFile(tempFile.toFile(), "rw")) {
                raf.setLength(dto.getFileSize());
            }
            
            Long userId = dto.getUserId() != null ? Long.parseLong(dto.getUserId()) : 1L;
            
            RagChunkUpload upload = RagChunkUpload.builder()
                    .uploadId(uploadId)
                    .fileName(dto.getFileName())
                    .fileType(fileType)
                    .fileSize(dto.getFileSize())
                    .totalChunks(dto.getTotalChunks())
                    .tempPath(tempDir.toString())
                    .knowledgeBaseId(knowledgeBaseId)
                    .title(dto.getTitle())
                    .createdBy(userId)
                    .status(RagChunkUpload.STATUS_UPLOADING)
                    .build();
            
            chunkUploadMapper.insert(upload);
            
            log.info("Initialized chunk upload: uploadId={}, fileName={}, totalChunks={}", 
                    uploadId, dto.getFileName(), dto.getTotalChunks());
            
            return ChunkUploadResultDTO.builder()
                    .success(true)
                    .uploadId(uploadId)
                    .totalChunks(dto.getTotalChunks())
                    .message("分片上传初始化成功")
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to init chunk upload: {}", e.getMessage(), e);
            return ChunkUploadResultDTO.error("初始化失败: " + e.getMessage());
        }
    }
    
    @Override
    @Transactional
    public ChunkUploadResultDTO uploadChunk(String uploadId, Integer chunkIndex, MultipartFile chunk) {
        RagChunkUpload upload = chunkUploadMapper.findByUploadId(uploadId);
        if (upload == null) {
            return ChunkUploadResultDTO.error("上传任务不存在: " + uploadId);
        }
        
        if (upload.getStatus() == RagChunkUpload.STATUS_COMPLETED) {
            return ChunkUploadResultDTO.error("上传任务已完成");
        }
        
        if (upload.getStatus() == RagChunkUpload.STATUS_FAILED) {
            return ChunkUploadResultDTO.error("上传任务已失败，请重新开始");
        }
        
        try {
            Path tempDir = Paths.get(upload.getTempPath());
            String extension = getFileExtension(upload.getFileName());
            Path tempFile = tempDir.resolve("original." + extension);
            Path chunkFile = tempDir.resolve("chunk_" + chunkIndex);
            
            Files.copy(chunk.getInputStream(), chunkFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            
            long chunkSize = chunk.getSize();
            long startPosition = (long) chunkIndex * DEFAULT_CHUNK_SIZE;
            
            try (RandomAccessFile raf = new RandomAccessFile(tempFile.toFile(), "rw");
                 FileInputStream fis = new FileInputStream(chunkFile.toFile())) {
                raf.seek(startPosition);
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    raf.write(buffer, 0, bytesRead);
                }
            }
            
            Files.deleteIfExists(chunkFile);
            
            log.debug("Uploaded chunk {}/{} for uploadId={}", chunkIndex + 1, upload.getTotalChunks(), uploadId);
            
            return ChunkUploadResultDTO.success(uploadId, chunkIndex, upload.getTotalChunks());
            
        } catch (Exception e) {
            log.error("Failed to upload chunk {} for uploadId {}: {}", chunkIndex, uploadId, e.getMessage(), e);
            return ChunkUploadResultDTO.error("分片上传失败: " + e.getMessage());
        }
    }
    
    @Override
    @Transactional
    public ChunkUploadResultDTO completeChunkUpload(String uploadId) {
        RagChunkUpload upload = chunkUploadMapper.findByUploadId(uploadId);
        if (upload == null) {
            return ChunkUploadResultDTO.error("上传任务不存在: " + uploadId);
        }
        
        try {
            Path tempDir = Paths.get(upload.getTempPath());
            String extension = getFileExtension(upload.getFileName());
            Path tempFile = tempDir.resolve("original." + extension);
            
            if (!Files.exists(tempFile)) {
                return ChunkUploadResultDTO.error("临时文件不存在");
            }
            
            long actualSize = Files.size(tempFile);
            if (actualSize != upload.getFileSize()) {
                log.warn("File size mismatch: expected={}, actual={}", upload.getFileSize(), actualSize);
                return ChunkUploadResultDTO.error("文件大小不匹配，可能部分分片未上传");
            }
            
            String storageFileName = UUID.randomUUID().toString() + "." + extension;
            Path uploadPath = Paths.get(ragProperties.getFile().getUploadPath()).toAbsolutePath().normalize();
            Path finalPath = uploadPath.resolve(storageFileName);
            
            Files.move(tempFile, finalPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            
            RagDocument document = RagDocument.builder()
                    .knowledgeBaseId(upload.getKnowledgeBaseId())
                    .title(upload.getTitle() != null ? upload.getTitle() : upload.getFileName())
                    .fileName(upload.getFileName())
                    .fileType(upload.getFileType())
                    .fileSize(upload.getFileSize())
                    .filePath(finalPath.toString())
                    .chunkCount(0)
                    .status(DocumentStatus.PENDING.getCode())
                    .createdBy(upload.getCreatedBy())
                    .build();
            
            documentMapper.insert(document);
            knowledgeBaseMapper.incrementDocumentCount(upload.getKnowledgeBaseId());
            
            chunkUploadMapper.updateStatus(uploadId, RagChunkUpload.STATUS_COMPLETED);
            
            deleteDirectory(tempDir.toFile());
            
            log.info("Completed chunk upload: uploadId={}, documentId={}", uploadId, document.getId());
            
            processDocumentAsync(document.getId());
            
            return ChunkUploadResultDTO.completed(toDTO(document));
            
        } catch (Exception e) {
            log.error("Failed to complete chunk upload {}: {}", uploadId, e.getMessage(), e);
            chunkUploadMapper.updateStatus(uploadId, RagChunkUpload.STATUS_FAILED);
            return ChunkUploadResultDTO.error("完成上传失败: " + e.getMessage());
        }
    }
    
    @Override
    public ChunkUploadResultDTO getUploadProgress(String uploadId) {
        RagChunkUpload upload = chunkUploadMapper.findByUploadId(uploadId);
        if (upload == null) {
            return ChunkUploadResultDTO.error("上传任务不存在: " + uploadId);
        }
        
        try {
            Path tempDir = Paths.get(upload.getTempPath());
            Set<Integer> uploadedChunks = new HashSet<>();
            
            if (Files.exists(tempDir)) {
                Files.list(tempDir)
                    .filter(p -> p.getFileName().toString().startsWith("chunk_"))
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        try {
                            int idx = Integer.parseInt(name.substring(6));
                            uploadedChunks.add(idx);
                        } catch (NumberFormatException ignored) {}
                    });
            }
            
            List<Integer> missingChunks = new ArrayList<>();
            for (int i = 0; i < upload.getTotalChunks(); i++) {
                if (!uploadedChunks.contains(i)) {
                    missingChunks.add(i);
                }
            }
            
            int progress = (int) ((upload.getTotalChunks() - missingChunks.size()) * 100.0 / upload.getTotalChunks());
            
            return ChunkUploadResultDTO.builder()
                    .success(true)
                    .uploadId(uploadId)
                    .totalChunks(upload.getTotalChunks())
                    .uploadedChunks(upload.getTotalChunks() - missingChunks.size())
                    .missingChunks(missingChunks)
                    .progress(progress)
                    .completed(upload.getStatus() == RagChunkUpload.STATUS_COMPLETED)
                    .message("获取进度成功")
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to get upload progress for {}: {}", uploadId, e.getMessage(), e);
            return ChunkUploadResultDTO.error("获取进度失败: " + e.getMessage());
        }
    }
    
    @Override
    @Transactional
    public void cancelChunkUpload(String uploadId) {
        RagChunkUpload upload = chunkUploadMapper.findByUploadId(uploadId);
        if (upload == null) {
            return;
        }
        
        try {
            Path tempDir = Paths.get(upload.getTempPath());
            deleteDirectory(tempDir.toFile());
        } catch (Exception e) {
            log.warn("Failed to delete temp directory for uploadId {}: {}", uploadId, e.getMessage());
        }
        
        chunkUploadMapper.deleteByUploadId(uploadId);
        log.info("Cancelled chunk upload: {}", uploadId);
    }
    
    @Scheduled(fixedRate = 3600000)
    public void cleanupExpiredUploads() {
        try {
            int deleted = chunkUploadMapper.deleteExpiredUploads(24);
            if (deleted > 0) {
                log.info("Cleaned up {} expired chunk uploads", deleted);
            }
        } catch (Exception e) {
            log.error("Failed to cleanup expired uploads: {}", e.getMessage(), e);
        }
    }
    
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}
