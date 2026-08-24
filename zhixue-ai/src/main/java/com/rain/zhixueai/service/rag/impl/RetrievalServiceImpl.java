package com.rain.zhixueai.service.rag.impl;

import com.rain.zhixueai.config.MilvusClientProvider;
import com.rain.zhixueai.config.MilvusProperties;
import com.rain.zhixueai.config.RagProperties;
import com.rain.zhixueai.dto.rag.RagContext;
import com.rain.zhixueai.dto.rag.RetrievalRequest;
import com.rain.zhixueai.dto.rag.RetrievalResult;
import com.rain.zhixueai.entity.rag.RagChunk;
import com.rain.zhixueai.entity.rag.RagEmbeddingModel;
import com.rain.zhixueai.entity.rag.RagKnowledgeBase;
import com.rain.zhixueai.mapper.rag.RagChunkMapper;
import com.rain.zhixueai.mapper.rag.RagEmbeddingModelMapper;
import com.rain.zhixueai.mapper.rag.RagKnowledgeBaseMapper;
import com.rain.zhixueai.service.rag.EmbeddingService;
import com.rain.zhixueai.service.rag.RetrievalService;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import io.milvus.v2.service.index.response.DescribeIndexResp;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RetrievalServiceImpl implements RetrievalService {
    
    private static final int CACHE_MAX_SIZE = 1000;
    private static final long CACHE_EXPIRE_MS = 300000;
    
    @Autowired
    private MilvusClientProvider milvusClientProvider;
    
    @Autowired
    private MilvusProperties milvusProperties;
    
    @Autowired
    private RagProperties ragProperties;
    
    @Autowired
    private EmbeddingService embeddingService;
    
    @Autowired
    private RagChunkMapper chunkMapper;
    
    @Autowired
    private RagKnowledgeBaseMapper knowledgeBaseMapper;
    
    @Autowired
    private RagEmbeddingModelMapper embeddingModelMapper;
    
    @Autowired
    private BM25Scorer bm25Scorer;
    
    private final ConcurrentHashMap<String, CacheEntry> queryCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, RagKnowledgeBase> knowledgeBaseCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, RagEmbeddingModel> embeddingModelCache = new ConcurrentHashMap<>();
    
    private static class CacheEntry {
        final List<RetrievedChunk> chunks;
        final long timestamp;
        
        CacheEntry(List<RetrievedChunk> chunks) {
            this.chunks = chunks;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRE_MS;
        }
    }
    
    private MilvusClientV2 getMilvusClient() {
        return milvusClientProvider.getClient();
    }
    
    private boolean isMilvusAvailable() {
        if (!milvusClientProvider.isAvailable()) {
            log.warn("Milvus客户端未初始化，RAG检索功能不可用");
            return false;
        }
        return true;
    }
    
    @Override
    public RetrievalResult search(RetrievalRequest request) {
        if (!isMilvusAvailable()) {
            return RetrievalResult.builder()
                    .query(request.getQuery())
                    .items(new ArrayList<>())
                    .totalResults(0)
                    .searchTimeMs(0L)
                    .build();
        }
        
        long startTime = System.currentTimeMillis();
        
        int topK = request.getTopK() != null ? request.getTopK() : ragProperties.getRetrieval().getDefaultTopK();
        float threshold = request.getSimilarityThreshold() != null ? request.getSimilarityThreshold() 
                : ragProperties.getRetrieval().getDefaultThreshold();
        
        List<RetrievedChunk> retrievedChunks = hybridRetrieve(request.getQuery(), request.getKnowledgeBaseIds(), topK * 2, threshold);
        
        List<RetrievedChunk> rerankedChunks = rerank(request.getQuery(), retrievedChunks, topK);
        
        List<RetrievalResult.RetrievalItem> items = rerankedChunks.stream()
                .map(rc -> {
                    RagKnowledgeBase kb = knowledgeBaseMapper.findById(rc.chunk.getKnowledgeBaseId());
                    return RetrievalResult.RetrievalItem.builder()
                            .chunkId(rc.chunk.getId())
                            .documentId(rc.chunk.getDocumentId())
                            .knowledgeBaseId(rc.chunk.getKnowledgeBaseId())
                            .content(request.getIncludeContent() == null || request.getIncludeContent() ? rc.chunk.getContent() : null)
                            .chunkIndex(rc.chunk.getChunkIndex())
                            .knowledgeBaseName(kb != null ? kb.getName() : null)
                            .similarity(rc.score)
                            .build();
                })
                .collect(Collectors.toList());
        
        long searchTime = System.currentTimeMillis() - startTime;
        
        log.info("Search completed: {} results in {}ms (threshold={})", items.size(), searchTime, threshold);
        
        return RetrievalResult.builder()
                .query(request.getQuery())
                .items(items)
                .totalResults(items.size())
                .searchTimeMs(searchTime)
                .build();
    }
    
    private static class RetrievedChunk {
        RagChunk chunk;
        float score;
        float vectorScore;
        float bm25Score;
        
        RetrievedChunk(RagChunk chunk, float score) {
            this.chunk = chunk;
            this.score = score;
        }
        
        RetrievedChunk(RagChunk chunk, float score, float vectorScore, float bm25Score) {
            this.chunk = chunk;
            this.score = score;
            this.vectorScore = vectorScore;
            this.bm25Score = bm25Score;
        }
    }
    
    private List<RetrievedChunk> hybridRetrieve(String query, List<Long> knowledgeBaseIds, int topK, float threshold) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        String cacheKey = buildCacheKey(query, knowledgeBaseIds, topK);
        CacheEntry cached = queryCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Cache hit for query: {}", query.substring(0, Math.min(50, query.length())));
            return cached.chunks.stream()
                    .filter(rc -> rc.score >= threshold)
                    .collect(Collectors.toList());
        }
        
        List<RagKnowledgeBase> knowledgeBases = getKnowledgeBasesWithCache(knowledgeBaseIds);
        if (knowledgeBases.isEmpty()) {
            return new ArrayList<>();
        }
        
        RagKnowledgeBase firstKb = knowledgeBases.get(0);
        RagEmbeddingModel embeddingModel = getEmbeddingModelWithCache(firstKb.getEmbeddingModelId());
        
        String modelName = embeddingModel != null ? embeddingModel.getModelName() : null;
        float[] queryEmbedding = embeddingService.embed(query, modelName);
        
        String collectionName = getCollectionName(embeddingService.getDimension(modelName));
        
        ensureCollectionLoaded(collectionName);
        
        List<RetrievedChunk> vectorResults = vectorSearch(queryEmbedding, collectionName, knowledgeBaseIds, topK, modelName);
        
        List<RetrievedChunk> hybridResults = combineWithBM25(query, vectorResults, knowledgeBaseIds);
        
        if (queryCache.size() < CACHE_MAX_SIZE) {
            queryCache.put(cacheKey, new CacheEntry(new ArrayList<>(hybridResults)));
        }
        
        log.debug("Hybrid retrieved {} chunks for query (threshold={})", hybridResults.size(), threshold);
        return hybridResults;
    }
    
    private List<RetrievedChunk> vectorSearch(float[] queryEmbedding, String collectionName, 
            List<Long> knowledgeBaseIds, int topK, String modelName) {
        
        try {
            String filter = "knowledge_base_id in " + knowledgeBaseIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(",", "[", "]"));
            
            log.debug("Executing vector search: collection={}, topK={}, filter={}", collectionName, topK, filter);
            
            SearchReq searchReq = SearchReq.builder()
                    .collectionName(collectionName)
                    .data(Arrays.asList(new FloatVec(queryEmbedding)))
                    .filter(filter)
                    .topK(topK)
                    .outputFields(Arrays.asList("knowledge_base_id", "document_id", "chunk_id", "content"))
                    .build();
            
            long searchStartTime = System.currentTimeMillis();
            SearchResp searchResp = getMilvusClient().search(searchReq);
            long searchDuration = System.currentTimeMillis() - searchStartTime;
            
            log.debug("Milvus search completed in {}ms", searchDuration);
            
            List<RetrievedChunk> results = new ArrayList<>();
            List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();
            
            if (searchResults != null && !searchResults.isEmpty()) {
                List<SearchResp.SearchResult> resultList = searchResults.get(0);
                log.debug("Received {} raw search results", resultList.size());
                
                float minScore = Float.MAX_VALUE;
                float maxScore = Float.MIN_VALUE;
                float minSimilarity = Float.MAX_VALUE;
                float maxSimilarity = Float.MIN_VALUE;
                
                for (SearchResp.SearchResult result : resultList) {
                    float rawScore = result.getScore();
                    
                    minScore = Math.min(minScore, rawScore);
                    maxScore = Math.max(maxScore, rawScore);
                    
                    float similarity = 1.0f - rawScore;
                    
                    minSimilarity = Math.min(minSimilarity, similarity);
                    maxSimilarity = Math.max(maxSimilarity, similarity);
                    
                    if (similarity < 0.0f || similarity > 1.0f) {
                        log.warn("Similarity score out of expected range [0,1]: rawScore={}, similarity={}", 
                                rawScore, similarity);
                        similarity = Math.max(0.0f, Math.min(1.0f, similarity));
                        log.debug("Normalized similarity to: {}", similarity);
                    }
                    
                    Map<String, Object> entity = result.getEntity();
                    
                    RagChunk chunk = new RagChunk();
                    chunk.setVectorId(result.getId().toString());
                    chunk.setKnowledgeBaseId(((Number) entity.get("knowledge_base_id")).longValue());
                    chunk.setDocumentId(((Number) entity.get("document_id")).longValue());
                    chunk.setChunkIndex(((Number) entity.get("chunk_id")).intValue());
                    chunk.setContent((String) entity.get("content"));
                    
                    results.add(new RetrievedChunk(chunk, similarity, similarity, 0));
                }
                
                log.info("Vector search completed: {} results, score range [{:.4f}, {:.4f}], similarity range [{:.4f}, {:.4f}], {}ms",
                        results.size(), minScore, maxScore, minSimilarity, maxSimilarity, searchDuration);
                
                if (!results.isEmpty()) {
                    log.debug("Top 3 results:");
                    for (int i = 0; i < Math.min(3, results.size()); i++) {
                        RetrievedChunk rc = results.get(i);
                        log.debug("  #{} similarity={:.4f}, documentId={}, chunkIndex={}", 
                                i + 1, rc.vectorScore, rc.chunk.getDocumentId(), rc.chunk.getChunkIndex());
                    }
                }
            } else {
                log.debug("Vector search returned no results");
            }
            
            return results;
            
        } catch (Exception e) {
            log.error("Failed to search in Milvus: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    private List<RetrievedChunk> combineWithBM25(String query, List<RetrievedChunk> vectorResults, List<Long> knowledgeBaseIds) {
        if (vectorResults.isEmpty()) {
            return vectorResults;
        }
        
        float alpha = ragProperties.getRetrieval().getHybridAlpha() != null ? 
                ragProperties.getRetrieval().getHybridAlpha() : 0.6f;
        
        log.debug("Hybrid retrieval: alpha={} (vector weight), BM25 weight={}", alpha, 1 - alpha);
        
        List<BM25Scorer.ChunkDocument> chunks = vectorResults.stream()
                .map(rc -> new BM25Scorer.ChunkDocument(
                        (long) rc.chunk.getChunkIndex(), 
                        rc.chunk.getContent()))
                .collect(Collectors.toList());
        
        long bm25StartTime = System.currentTimeMillis();
        List<BM25Scorer.BM25Result> bm25Results = bm25Scorer.scoreBatch(query, chunks);
        long bm25Duration = System.currentTimeMillis() - bm25StartTime;
        
        log.debug("BM25 scoring completed in {}ms", bm25Duration);
        
        Map<Long, Float> bm25Scores = new HashMap<>();
        float maxBM25 = 0;
        float minBM25 = Float.MAX_VALUE;
        for (BM25Scorer.BM25Result result : bm25Results) {
            float score = (float) result.getScore();
            bm25Scores.put(result.getChunkId(), score);
            maxBM25 = Math.max(maxBM25, score);
            minBM25 = Math.min(minBM25, score);
        }
        
        float maxVector = 0;
        float minVector = Float.MAX_VALUE;
        for (RetrievedChunk rc : vectorResults) {
            maxVector = Math.max(maxVector, rc.vectorScore);
            minVector = Math.min(minVector, rc.vectorScore);
        }
        
        log.debug("Score ranges before normalization - Vector: [{:.4f}, {:.4f}], BM25: [{:.4f}, {:.4f}]",
                minVector, maxVector, minBM25, maxBM25);
        
        int improvedCount = 0;
        int vectorBetterCount = 0;
        int bm25BetterCount = 0;
        
        for (RetrievedChunk rc : vectorResults) {
            float normalizedVector = maxVector > 0 ? rc.vectorScore / maxVector : 0;
            float normalizedBM25 = maxBM25 > 0 ? 
                    bm25Scores.getOrDefault((long) rc.chunk.getChunkIndex(), 0f) / maxBM25 : 0;
            
            rc.bm25Score = normalizedBM25;
            float previousScore = rc.score;
            rc.score = alpha * normalizedVector + (1 - alpha) * normalizedBM25;
            
            if (rc.score > previousScore) {
                improvedCount++;
            }
            if (normalizedVector > normalizedBM25) {
                vectorBetterCount++;
            } else if (normalizedBM25 > normalizedVector) {
                bm25BetterCount++;
            }
        }
        
        log.info("Hybrid fusion completed: {} results, alpha={:.2f}", vectorResults.size(), alpha);
        log.debug("  - Vector scores: min={:.4f}, max={:.4f}", minVector, maxVector);
        log.debug("  - BM25 scores: min={:.4f}, max={:.4f}", minBM25, maxBM25);
        log.debug("  - Score improvements: {} items improved by hybrid fusion", improvedCount);
        log.debug("  - Vector better: {} items, BM25 better: {} items", vectorBetterCount, bm25BetterCount);
        
        vectorResults.sort((a, b) -> Float.compare(b.score, a.score));
        
        if (!vectorResults.isEmpty()) {
            log.debug("Top 3 hybrid results:");
            for (int i = 0; i < Math.min(3, vectorResults.size()); i++) {
                RetrievedChunk rc = vectorResults.get(i);
                log.debug("  #{} final={:.4f} (vector={:.4f}, bm25={:.4f}), documentId={}, chunkIndex={}", 
                        i + 1, rc.score, rc.vectorScore, rc.bm25Score, 
                        rc.chunk.getDocumentId(), rc.chunk.getChunkIndex());
            }
        }
        
        return vectorResults;
    }
    
    private String buildCacheKey(String query, List<Long> knowledgeBaseIds, int topK) {
        return query.hashCode() + "_" + knowledgeBaseIds.hashCode() + "_" + topK;
    }
    
    private List<RagKnowledgeBase> getKnowledgeBasesWithCache(List<Long> ids) {
        List<RagKnowledgeBase> result = new ArrayList<>();
        List<Long> missingIds = new ArrayList<>();
        
        for (Long id : ids) {
            RagKnowledgeBase cached = knowledgeBaseCache.get(id);
            if (cached != null) {
                result.add(cached);
            } else {
                missingIds.add(id);
            }
        }
        
        if (!missingIds.isEmpty()) {
            List<RagKnowledgeBase> fetched = knowledgeBaseMapper.findByIds(missingIds);
            for (RagKnowledgeBase kb : fetched) {
                knowledgeBaseCache.put(kb.getId(), kb);
                result.add(kb);
            }
        }
        
        return result;
    }
    
    private RagEmbeddingModel getEmbeddingModelWithCache(Long id) {
        if (id == null) {
            return null;
        }
        
        RagEmbeddingModel cached = embeddingModelCache.get(id);
        if (cached != null) {
            return cached;
        }
        
        RagEmbeddingModel model = embeddingModelMapper.findById(id);
        if (model != null) {
            embeddingModelCache.put(id, model);
        }
        return model;
    }
    
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredCache() {
        int removedCount = 0;
        
        for (Map.Entry<String, CacheEntry> entry : queryCache.entrySet()) {
            if (entry.getValue().isExpired()) {
                queryCache.remove(entry.getKey());
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            log.debug("Cleaned up {} expired cache entries", removedCount);
        }
    }
    
    public void clearCache() {
        queryCache.clear();
        knowledgeBaseCache.clear();
        embeddingModelCache.clear();
        log.info("All retrieval caches cleared");
    }
    
    private List<RetrievedChunk> rerank(String query, List<RetrievedChunk> chunks, int topK) {
        if (chunks.isEmpty()) {
            return chunks;
        }
        
        Boolean rerankEnabled = ragProperties.getRetrieval().getRerankEnabled();
        if (rerankEnabled == null || !rerankEnabled) {
            return chunks.subList(0, Math.min(topK, chunks.size()));
        }
        
        Set<String> queryTerms = extractTerms(query.toLowerCase());
        Map<String, Integer> queryTermFreq = new HashMap<>();
        for (String term : queryTerms) {
            queryTermFreq.merge(term, 1, Integer::sum);
        }
        
        for (RetrievedChunk rc : chunks) {
            float keywordScore = calculateKeywordScore(queryTerms, queryTermFreq, rc.chunk.getContent());
            float lengthPenalty = calculateLengthPenalty(rc.chunk.getContent());
            float positionBonus = calculatePositionBonus(rc.chunk.getChunkIndex());
            
            rc.score = 0.6f * rc.score + 0.25f * keywordScore + 0.1f * positionBonus - 0.05f * lengthPenalty;
        }
        
        chunks.sort((a, b) -> Float.compare(b.score, a.score));
        
        List<RetrievedChunk> diverse = diversifyResults(chunks, topK);
        
        return diverse.subList(0, Math.min(topK, diverse.size()));
    }
    
    private Set<String> extractTerms(String text) {
        Set<String> terms = new HashSet<>();
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isIdeographic(c)) {
                terms.add(String.valueOf(c));
            }
        }
        
        String[] words = text.split("[\\s\\p{Punct}]+");
        for (String word : words) {
            if (word.length() > 1 && !isChinese(word)) {
                terms.add(word.toLowerCase());
            }
        }
        
        return terms;
    }
    
    private boolean isChinese(String text) {
        for (char c : text.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fa5') {
                return true;
            }
        }
        return false;
    }
    
    private float calculateKeywordScore(Set<String> queryTerms, Map<String, Integer> queryTermFreq, String content) {
        if (content == null || content.isEmpty()) {
            return 0f;
        }
        
        Set<String> contentTerms = extractTerms(content.toLowerCase());
        
        int matchCount = 0;
        int exactPhraseBonus = 0;
        
        for (String term : queryTerms) {
            if (contentTerms.contains(term)) {
                matchCount++;
                if (content.toLowerCase().contains(term)) {
                    exactPhraseBonus++;
                }
            }
        }
        
        float coverage = queryTerms.isEmpty() ? 0f : (float) matchCount / queryTerms.size();
        float density = content.isEmpty() ? 0f : (float) matchCount / (content.length() / 10);
        
        return Math.min(1.0f, coverage * 0.7f + density * 0.3f);
    }
    
    private float calculateLengthPenalty(String content) {
        if (content == null) return 0.5f;
        
        int len = content.length();
        if (len < 50) return 0.3f;
        if (len < 200) return 0.1f;
        if (len < 500) return 0f;
        if (len < 1000) return 0.1f;
        return 0.2f;
    }
    
    private float calculatePositionBonus(int chunkIndex) {
        if (chunkIndex < 3) return 0.2f;
        if (chunkIndex < 10) return 0.1f;
        return 0f;
    }
    
    private List<RetrievedChunk> diversifyResults(List<RetrievedChunk> chunks, int topK) {
        if (chunks.size() <= topK) {
            return chunks;
        }
        
        List<RetrievedChunk> diverse = new ArrayList<>();
        Set<Long> seenDocuments = new HashSet<>();
        
        for (RetrievedChunk rc : chunks) {
            if (diverse.size() >= topK) {
                break;
            }
            
            if (!seenDocuments.contains(rc.chunk.getDocumentId())) {
                diverse.add(rc);
                seenDocuments.add(rc.chunk.getDocumentId());
            }
        }
        
        if (diverse.size() < topK) {
            for (RetrievedChunk rc : chunks) {
                if (diverse.size() >= topK) {
                    break;
                }
                if (!diverse.contains(rc)) {
                    diverse.add(rc);
                }
            }
        }
        
        return diverse;
    }
    
    @Override
    public List<RagChunk> retrieve(String query, List<Long> knowledgeBaseIds, int topK, float threshold) {
        if (!isMilvusAvailable()) {
            return new ArrayList<>();
        }
        List<RetrievedChunk> retrievedChunks = hybridRetrieve(query, knowledgeBaseIds, topK, threshold);
        return retrievedChunks.stream()
                .map(rc -> rc.chunk)
                .collect(Collectors.toList());
    }
    
    @Override
    public RagContext buildContext(String query, List<Long> knowledgeBaseIds) {
        return buildContext(query, knowledgeBaseIds, null);
    }
    
    public RagContext buildContext(String query, List<Long> knowledgeBaseIds, String conversationHistory) {
        if (!isMilvusAvailable()) {
            return RagContext.builder()
                    .query(query)
                    .chunks(new ArrayList<>())
                    .systemPrompt("知识库服务暂时不可用，请直接使用你的知识回答用户问题。确保回答准确、有条理，使用规范的Markdown格式。")
                    .build();
        }
        
        long startTime = System.currentTimeMillis();
        
        List<RetrievedChunk> retrievedChunks = hybridRetrieve(query, knowledgeBaseIds, 
                ragProperties.getRetrieval().getDefaultTopK() * 2,
                ragProperties.getRetrieval().getDefaultThreshold());
        
        List<RetrievedChunk> rerankedChunks = rerank(query, retrievedChunks, ragProperties.getRetrieval().getDefaultTopK());
        
        if (rerankedChunks.isEmpty()) {
            return RagContext.builder()
                    .query(query)
                    .chunks(new ArrayList<>())
                    .systemPrompt("知识库中未检索到与问题直接相关的信息，请直接使用你的知识回答用户问题。回答时请确保内容准确、有条理，使用规范的Markdown格式。不要提示用户知识库中无信息或建议上传文档。")
                    .build();
        }
        
        List<RagContext.ContextChunk> contextChunks = new ArrayList<>();
        StringBuilder systemPrompt = new StringBuilder();
        
        systemPrompt.append("你是一个专业的智能助手。请根据以下从知识库中检索到的相关信息回答用户问题。\n\n");
        systemPrompt.append("## 重要说明：\n");
        systemPrompt.append("- 以下信息来自知识库，可能包含用户上传的文档内容\n");
        systemPrompt.append("- 请优先使用这些信息回答问题\n");
        systemPrompt.append("- 如果信息不足，请明确告知用户\n\n");
        systemPrompt.append("## 检索到的相关信息：\n\n");
        
        int totalTokens = 0;
        int maxTokens = ragProperties.getRetrieval().getMaxContextLength();
        
        for (int i = 0; i < rerankedChunks.size(); i++) {
            RetrievedChunk rc = rerankedChunks.get(i);
            RagChunk chunk = rc.chunk;
            String content = chunk.getContent();
            
            int estimatedTokens = estimateTokens(content);
            if (totalTokens + estimatedTokens > maxTokens) {
                log.debug("Context truncated at {} chunks due to token limit", i);
                break;
            }
            
            totalTokens += estimatedTokens;
            
            systemPrompt.append("### 参考文档").append(i + 1).append(" (相关度: ")
                    .append(String.format("%.0f%%", rc.score * 100)).append(")\n");
            systemPrompt.append("```\n").append(content).append("\n```\n\n");
            
            contextChunks.add(RagContext.ContextChunk.builder()
                    .chunkId(chunk.getId())
                    .documentId(chunk.getDocumentId())
                    .content(content)
                    .similarity(rc.score)
                    .build());
        }
        
        systemPrompt.append("## 回答要求：\n");
        systemPrompt.append("1. 请基于以上检索到的信息回答用户问题，不要编造信息\n");
        systemPrompt.append("2. 如果检索信息不足以回答问题，请明确说明\"根据现有知识库信息无法完全回答该问题\"\n");
        systemPrompt.append("3. 引用信息时使用\"根据参考文档X\"的格式\n");
        systemPrompt.append("4. 保持回答简洁、准确、有条理\n");
        systemPrompt.append("5. 如果问题涉及代码，请提供具体的代码示例\n");
        systemPrompt.append("6. **格式修正**：检索到的原始内容可能存在格式问题（如缺少换行、代码块未闭合、表格格式错乱、特殊字符未转义等），请在回答时对引用的内容进行格式修正，确保最终输出严格符合Markdown规范\n");
        systemPrompt.append("7. 所有代码块必须使用带语言标识符的围栏格式（如 ```python、```java、```cpp），不要省略语言标识符\n");
        systemPrompt.append("8. 标题使用 # ## ### 层级，列表使用 - 或数字格式，表格使用标准Markdown表格语法\n");
        
        long buildTime = System.currentTimeMillis() - startTime;
        log.info("Context built: {} chunks, {} estimated tokens, {}ms", 
                contextChunks.size(), totalTokens, buildTime);
        
        return RagContext.builder()
                .query(query)
                .chunks(contextChunks)
                .systemPrompt(systemPrompt.toString())
                .totalTokens(totalTokens)
                .build();
    }
    
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        int chineseChars = 0;
        int englishWords = 0;
        int codeTokens = 0;
        
        boolean inWord = false;
        boolean inCode = false;
        
        for (char c : text.toCharArray()) {
            if (Character.isIdeographic(c)) {
                chineseChars++;
                inWord = false;
            } else if (Character.isLetter(c)) {
                if (!inWord) {
                    englishWords++;
                    inWord = true;
                }
            } else if (c == '{' || c == '}' || c == '(' || c == ')' || c == '[' || c == ']') {
                codeTokens++;
                inWord = false;
            } else {
                inWord = false;
            }
        }
        
        return (int) (chineseChars * 1.5 + englishWords * 1.3 + codeTokens * 0.5);
    }
    
    @Override
    public void storeEmbeddings(Long knowledgeBaseId, Long documentId, List<String> contents, List<float[]> embeddings) {
        if (!isMilvusAvailable()) {
            log.warn("Milvus 不可用，无法存储向量嵌入");
            return;
        }
        
        if (contents == null || contents.isEmpty() || embeddings == null || embeddings.isEmpty()) {
            log.warn("Empty contents or embeddings, skipping storage");
            return;
        }
        
        if (contents.size() != embeddings.size()) {
            throw new IllegalArgumentException("Contents and embeddings size mismatch: " + contents.size() + " vs " + embeddings.size());
        }
        
        log.info("Starting to store {} embeddings for knowledgeBaseId={}, documentId={}", 
                contents.size(), knowledgeBaseId, documentId);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < embeddings.size(); i++) {
            float[] emb = embeddings.get(i);
            if (emb == null || emb.length == 0) {
                throw new IllegalArgumentException("Found null or empty embedding at index " + i);
            }
        }
        
        int dimension = embeddings.get(0).length;
        String collectionName = getCollectionName(dimension);
        
        log.info("Using collection: {} with dimension: {}", collectionName, dimension);
        
        ensureCollectionExists(collectionName, dimension);
        
        int batchSize = 100;
        int totalStored = 0;
        int totalBatches = (contents.size() + batchSize - 1) / batchSize;
        
        for (int batch = 0; batch < contents.size(); batch += batchSize) {
            int endIdx = Math.min(batch + batchSize, contents.size());
            int currentBatchNum = (batch / batchSize) + 1;
            
            log.debug("Preparing batch {}/{} (items {}-{})", currentBatchNum, totalBatches, batch, endIdx - 1);
            
            List<JsonObject> data = new ArrayList<>();
            List<RagChunk> chunks = new ArrayList<>();
            
            for (int i = batch; i < endIdx; i++) {
                String vectorId = UUID.randomUUID().toString();
                String content = contents.get(i);
                float[] embedding = embeddings.get(i);
                
                JsonObject row = new JsonObject();
                row.addProperty("id", vectorId);
                row.addProperty("knowledge_base_id", knowledgeBaseId);
                row.addProperty("document_id", documentId);
                row.addProperty("chunk_id", i);
                String truncatedContent = content.length() > 8000 ? content.substring(0, 8000) : content;
                row.addProperty("content", truncatedContent);
                
                JsonArray embeddingArray = new JsonArray();
                for (float v : embedding) {
                    embeddingArray.add(v);
                }
                row.add("vector", embeddingArray);
                data.add(row);
                
                RagChunk chunk = RagChunk.builder()
                        .documentId(documentId)
                        .knowledgeBaseId(knowledgeBaseId)
                        .chunkIndex(i)
                        .content(content)
                        .vectorId(vectorId)
                        .build();
                chunks.add(chunk);
                
                bm25Scorer.indexChunk((long) i, content);
            }
            
            try {
                log.debug("Inserting batch {} with {} rows into Milvus", currentBatchNum, data.size());
                long insertStartTime = System.currentTimeMillis();
                
                InsertReq insertReq = InsertReq.builder()
                        .collectionName(collectionName)
                        .data(data)
                        .build();
                
                getMilvusClient().insert(insertReq);
                
                long insertDuration = System.currentTimeMillis() - insertStartTime;
                log.debug("Milvus insert completed in {}ms", insertDuration);
                
                log.debug("Inserting {} chunks into database", chunks.size());
                long dbInsertStartTime = System.currentTimeMillis();
                chunkMapper.batchInsert(chunks);
                long dbInsertDuration = System.currentTimeMillis() - dbInsertStartTime;
                log.debug("Database insert completed in {}ms", dbInsertDuration);
                
                totalStored += chunks.size();
                log.info("Stored batch {}/{}: {} embeddings (total: {}/{}) for document: {} in collection: {} ({}ms)", 
                        currentBatchNum, totalBatches,
                        chunks.size(), totalStored, contents.size(), 
                        documentId, collectionName, insertDuration + dbInsertDuration);
                
            } catch (Exception e) {
                log.error("Failed to store embeddings batch {}/{}: {} - Error: {}", 
                        currentBatchNum, totalBatches, e.getClass().getSimpleName(), e.getMessage(), e);
                throw new RuntimeException("Failed to store embeddings batch " + currentBatchNum + ": " + e.getMessage(), e);
            }
        }
        
        long totalDuration = System.currentTimeMillis() - startTime;
        log.info("Total stored {} embeddings for document: {} in collection: {} (total time: {}ms, avg: {}ms/batch)", 
                totalStored, documentId, collectionName, totalDuration, totalDuration / totalBatches);
        
        try {
            log.debug("Flushing collection to ensure data persistence...");
            long flushStartTime = System.currentTimeMillis();
            
            io.milvus.v2.service.utility.request.FlushReq flushReq = io.milvus.v2.service.utility.request.FlushReq.builder()
                    .collectionNames(Arrays.asList(collectionName))
                    .build();
            getMilvusClient().flush(flushReq);
            
            long flushDuration = System.currentTimeMillis() - flushStartTime;
            log.info("Collection flushed successfully in {}ms", flushDuration);
        } catch (Exception e) {
            log.warn("Failed to flush collection: {}. Data will be flushed automatically.", e.getMessage());
        }
    }
    
    private void ensureCollectionExists(String collectionName, int dimension) {
        try {
            log.info("Checking if collection {} exists with dimension {}", collectionName, dimension);
            
            HasCollectionReq hasCollectionReq = HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            
            boolean hasCollection = getMilvusClient().hasCollection(hasCollectionReq);
            
            if (hasCollection) {
                log.info("Collection {} already exists, validating schema...", collectionName);
                if (!isCollectionSchemaValid(collectionName)) {
                    log.warn("Collection {} has invalid schema (id field is not VarChar), dropping and recreating...", collectionName);
                    dropCollection(collectionName);
                    hasCollection = false;
                } else {
                    log.debug("Collection {} schema validation passed", collectionName);
                }
            }
            
            if (!hasCollection) {
                log.info("Creating Milvus collection: {} with dimension: {}", collectionName, dimension);
                
                CreateCollectionReq.FieldSchema idField = CreateCollectionReq.FieldSchema.builder()
                        .name("id")
                        .dataType(DataType.VarChar)
                        .maxLength(64)
                        .isPrimaryKey(true)
                        .autoID(false)
                        .build();
                
                CreateCollectionReq.FieldSchema knowledgeBaseIdField = CreateCollectionReq.FieldSchema.builder()
                        .name("knowledge_base_id")
                        .dataType(DataType.Int64)
                        .build();
                
                CreateCollectionReq.FieldSchema documentIdField = CreateCollectionReq.FieldSchema.builder()
                        .name("document_id")
                        .dataType(DataType.Int64)
                        .build();
                
                CreateCollectionReq.FieldSchema chunkIdField = CreateCollectionReq.FieldSchema.builder()
                        .name("chunk_id")
                        .dataType(DataType.Int64)
                        .build();
                
                CreateCollectionReq.FieldSchema contentField = CreateCollectionReq.FieldSchema.builder()
                        .name("content")
                        .dataType(DataType.VarChar)
                        .maxLength(8192)
                        .build();
                
                CreateCollectionReq.FieldSchema vectorField = CreateCollectionReq.FieldSchema.builder()
                        .name("vector")
                        .dataType(DataType.FloatVector)
                        .dimension(dimension)
                        .build();
                
                CreateCollectionReq.CollectionSchema collectionSchema = CreateCollectionReq.CollectionSchema.builder()
                        .fieldSchemaList(Arrays.asList(
                                idField, 
                                knowledgeBaseIdField, 
                                documentIdField, 
                                chunkIdField, 
                                contentField, 
                                vectorField))
                        .build();
                
                CreateCollectionReq createCollectionReq = CreateCollectionReq.builder()
                        .collectionName(collectionName)
                        .collectionSchema(collectionSchema)
                        .build();
                
                getMilvusClient().createCollection(createCollectionReq);
                
                log.info("Created Milvus collection: {} with custom schema successfully", collectionName);
            }
            
            log.info("Ensuring vector index exists for collection: {}", collectionName);
            ensureVectorIndexExists(collectionName, dimension);
            
            log.info("Ensuring collection {} is loaded into memory", collectionName);
            ensureCollectionLoaded(collectionName);
            
            log.info("Collection {} is ready for use", collectionName);
            
        } catch (Exception e) {
            log.error("Failed to ensure collection {} exists: {}", collectionName, e.getMessage(), e);
            throw new RuntimeException("Failed to create Milvus collection " + collectionName + ": " + e.getMessage(), e);
        }
    }
    
    private void ensureVectorIndexExists(String collectionName, int dimension) {
        DescribeIndexReq describeIndexReq = DescribeIndexReq.builder()
                .collectionName(collectionName)
                .fieldName("vector")
                .build();
        
        try {
            DescribeIndexResp describeIndexResp = getMilvusClient().describeIndex(describeIndexReq);
            
            if (describeIndexResp != null && describeIndexResp.getIndexDescriptions() != null 
                    && !describeIndexResp.getIndexDescriptions().isEmpty()) {
                log.debug("Vector index already exists for collection: {}", collectionName);
                
                for (var indexDesc : describeIndexResp.getIndexDescriptions()) {
                    if ("vector".equals(indexDesc.getFieldName())) {
                        log.info("Collection {} has index: type={}, metricType={}", 
                                collectionName, indexDesc.getIndexType(), indexDesc.getMetricType());
                        
                        if (indexDesc.getTotalRows() > 0) {
                            log.debug("Index has {} indexed rows", indexDesc.getTotalRows());
                        }
                        return;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Index does not exist for collection {}, will create: {}", collectionName, e.getMessage());
        }
        
        try {
            log.info("Creating HNSW vector index for collection: {} (dimension={})", collectionName, dimension);
            log.info("HNSW parameters: M=16, efConstruction=200");
            
            IndexParam indexParam = IndexParam.builder()
                    .fieldName("vector")
                    .indexType(IndexParam.IndexType.HNSW)
                    .metricType(IndexParam.MetricType.COSINE)
                    .extraParams(Map.of("M", 16, "efConstruction", 200))
                    .build();
            
            CreateIndexReq createIndexReq = CreateIndexReq.builder()
                    .collectionName(collectionName)
                    .indexParams(Arrays.asList(indexParam))
                    .build();
            
            getMilvusClient().createIndex(createIndexReq);
            
            log.info("Created HNSW vector index for collection: {} with M=16, efConstruction=200", collectionName);
            
            long startTime = System.currentTimeMillis();
            int maxWaitTime = 60000;
            int checkInterval = 2000;
            
            while (System.currentTimeMillis() - startTime < maxWaitTime) {
                try {
                    DescribeIndexResp checkResp = getMilvusClient().describeIndex(describeIndexReq);
                    if (checkResp != null && checkResp.getIndexDescriptions() != null 
                            && !checkResp.getIndexDescriptions().isEmpty()) {
                        for (var idxDesc : checkResp.getIndexDescriptions()) {
                            if ("vector".equals(idxDesc.getFieldName())) {
                                log.debug("Index build state checked");
                                
                                long buildTime = System.currentTimeMillis() - startTime;
                                log.info("HNSW index build completed in {}ms", buildTime);
                                return;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Error checking index state: {}", e.getMessage());
                }
                
                try {
                    Thread.sleep(checkInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            log.warn("HNSW index build may still be in progress (timeout after {}ms)", maxWaitTime);
            
        } catch (Exception e) {
            log.error("Failed to create HNSW vector index for collection {}: {}", collectionName, e.getMessage(), e);
            throw new RuntimeException("Failed to create HNSW vector index: " + e.getMessage(), e);
        }
    }
    
    private void ensureCollectionLoaded(String collectionName) {
        int maxRetries = 3;
        int retryDelayMs = 1000;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("Ensuring collection {} is loaded into memory (attempt {}/{})...", collectionName, attempt, maxRetries);
                
                LoadCollectionReq loadReq = LoadCollectionReq.builder()
                        .collectionName(collectionName)
                        .build();
                
                getMilvusClient().loadCollection(loadReq);
                
                log.info("Collection {} loaded successfully", collectionName);
                return;
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                
                if (errorMsg != null && errorMsg.contains("collection not found")) {
                    log.warn("Collection {} not found, attempting to create...", collectionName);
                    try {
                        int dimension = extractDimensionFromCollectionName(collectionName);
                        if (dimension > 0) {
                            log.info("Auto-creating collection {} with dimension {}", collectionName, dimension);
                            ensureCollectionExists(collectionName, dimension);
                            log.info("Collection {} created successfully, retrying load...", collectionName);
                            continue;
                        } else {
                            log.error("Cannot extract dimension from collection name: {}", collectionName);
                        }
                    } catch (Exception createError) {
                        log.error("Failed to create collection {}: {}", collectionName, createError.getMessage(), createError);
                    }
                }
                
                if (errorMsg != null && errorMsg.contains("index not found")) {
                    log.warn("Index not found for collection {}, attempting to create index...", collectionName);
                    try {
                        DescribeCollectionReq describeReq = DescribeCollectionReq.builder()
                                .collectionName(collectionName)
                                .build();
                        DescribeCollectionResp describeResp = getMilvusClient().describeCollection(describeReq);
                        int dimension = 1024;
                        for (CreateCollectionReq.FieldSchema field : describeResp.getCollectionSchema().getFieldSchemaList()) {
                            if ("vector".equals(field.getName()) && field.getDimension() != null) {
                                dimension = field.getDimension();
                                break;
                            }
                        }
                        ensureVectorIndexExists(collectionName, dimension);
                        log.info("Index created for collection {}, retrying load operation...", collectionName);
                        continue;
                    } catch (Exception indexError) {
                        log.error("Failed to create index for collection {}: {}", collectionName, indexError.getMessage());
                    }
                }
                
                if (errorMsg != null && errorMsg.contains("already loaded")) {
                    log.debug("Collection {} is already loaded", collectionName);
                    return;
                }
                
                if (attempt < maxRetries) {
                    log.warn("Failed to load collection {} (attempt {}/{}): {}. Retrying in {}ms...", 
                            collectionName, attempt, maxRetries, errorMsg, retryDelayMs);
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    String errorDetail = String.format("Failed to load collection %s after %d attempts: %s", 
                            collectionName, maxRetries, errorMsg);
                    log.error(errorDetail);
                    throw new RuntimeException(errorDetail);
                }
            }
        }
    }
    
    private int extractDimensionFromCollectionName(String collectionName) {
        try {
            String[] parts = collectionName.split("_");
            if (parts.length > 0) {
                String lastPart = parts[parts.length - 1];
                return Integer.parseInt(lastPart);
            }
        } catch (NumberFormatException e) {
            log.warn("Failed to extract dimension from collection name: {}", collectionName);
        }
        return -1;
    }
    
    private boolean isCollectionSchemaValid(String collectionName) {
        try {
            DescribeCollectionReq describeReq = DescribeCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            
            DescribeCollectionResp describeResp = getMilvusClient().describeCollection(describeReq);
            
            CreateCollectionReq.CollectionSchema schema = describeResp.getCollectionSchema();
            if (schema == null) {
                return false;
            }
            
            List<CreateCollectionReq.FieldSchema> fields = schema.getFieldSchemaList();
            if (fields == null || fields.isEmpty()) {
                return false;
            }
            
            for (CreateCollectionReq.FieldSchema field : fields) {
                if ("id".equals(field.getName())) {
                    DataType idType = field.getDataType();
                    log.debug("Collection {} id field type: {}", collectionName, idType);
                    return idType == DataType.VarChar;
                }
            }
            
            return false;
        } catch (Exception e) {
            log.warn("Failed to describe collection {}: {}", collectionName, e.getMessage());
            return false;
        }
    }
    
    private void dropCollection(String collectionName) {
        try {
            DropCollectionReq dropReq = DropCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            
            getMilvusClient().dropCollection(dropReq);
            log.info("Dropped collection: {}", collectionName);
        } catch (Exception e) {
            log.error("Failed to drop collection {}: {}", collectionName, e.getMessage());
            throw new RuntimeException("Failed to drop collection: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deleteEmbeddingsByDocument(Long documentId) {
        if (!isMilvusAvailable()) {
            log.warn("Milvus不可用，无法删除文档嵌入");
            return;
        }
        
        List<RagChunk> chunks = chunkMapper.findByDocumentId(documentId);
        
        if (chunks.isEmpty()) {
            return;
        }
        
        RagChunk firstChunk = chunks.get(0);
        RagKnowledgeBase kb = knowledgeBaseMapper.findById(firstChunk.getKnowledgeBaseId());
        
        if (kb != null && kb.getEmbeddingModelId() != null) {
            RagEmbeddingModel model = embeddingModelMapper.findById(kb.getEmbeddingModelId());
            if (model != null) {
                String collectionName = getCollectionName(model.getDimension());
                
                try {
                    String filter = "document_id == " + documentId;
                    DeleteReq deleteReq = DeleteReq.builder()
                            .collectionName(collectionName)
                            .filter(filter)
                            .build();
                    
                    getMilvusClient().delete(deleteReq);
                } catch (Exception e) {
                    log.error("Failed to delete embeddings from Milvus: {}", e.getMessage(), e);
                }
            }
        }
        
        chunkMapper.deleteByDocumentId(documentId);
        log.info("Deleted embeddings for document: {}", documentId);
    }
    
    @Override
    public void deleteEmbeddingsByKnowledgeBase(Long knowledgeBaseId) {
        if (!isMilvusAvailable()) {
            log.warn("Milvus不可用，无法删除知识库嵌入");
            return;
        }
        
        List<RagChunk> chunks = chunkMapper.findByKnowledgeBaseId(knowledgeBaseId);
        
        if (chunks.isEmpty()) {
            return;
        }
        
        RagKnowledgeBase kb = knowledgeBaseMapper.findById(knowledgeBaseId);
        
        if (kb != null && kb.getEmbeddingModelId() != null) {
            RagEmbeddingModel model = embeddingModelMapper.findById(kb.getEmbeddingModelId());
            if (model != null) {
                String collectionName = getCollectionName(model.getDimension());
                
                try {
                    String filter = "knowledge_base_id == " + knowledgeBaseId;
                    DeleteReq deleteReq = DeleteReq.builder()
                            .collectionName(collectionName)
                            .filter(filter)
                            .build();
                    
                    getMilvusClient().delete(deleteReq);
                } catch (Exception e) {
                    log.error("Failed to delete embeddings from Milvus: {}", e.getMessage(), e);
                }
            }
        }
        
        chunkMapper.deleteByKnowledgeBaseId(knowledgeBaseId);
        log.info("Deleted embeddings for knowledge base: {}", knowledgeBaseId);
    }
    
    private String getCollectionName(int dimension) {
        return milvusProperties.getCollectionPrefix() + "_" + dimension;
    }
}
