package com.rain.zhixueai.service.rag.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class BM25Scorer {
    
    private static final double K1 = 1.5;
    private static final double B = 0.75;
    private static final Pattern CHINESE_WORD_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]+|[a-zA-Z]+|\\d+");
    
    private final Map<String, Integer> documentFrequency = new HashMap<>();
    private final Map<Long, Integer> documentLengths = new HashMap<>();
    private double avgDocumentLength = 0;
    private int totalDocuments = 0;
    
    public void indexDocument(Long docId, String content) {
        List<String> terms = tokenize(content);
        documentLengths.put(docId, terms.size());
        totalDocuments++;
        
        Set<String> uniqueTerms = new HashSet<>(terms);
        for (String term : uniqueTerms) {
            documentFrequency.merge(term, 1, Integer::sum);
        }
        
        updateAvgDocumentLength();
    }
    
    public void indexChunk(Long chunkId, String content) {
        List<String> terms = tokenize(content);
        documentLengths.put(chunkId, terms.size());
        totalDocuments++;
        
        Set<String> uniqueTerms = new HashSet<>(terms);
        for (String term : uniqueTerms) {
            documentFrequency.merge(term, 1, Integer::sum);
        }
        
        updateAvgDocumentLength();
    }
    
    public void clear() {
        documentFrequency.clear();
        documentLengths.clear();
        totalDocuments = 0;
        avgDocumentLength = 0;
    }
    
    public double score(String query, Long docId, String content) {
        List<String> queryTerms = tokenize(query);
        List<String> docTerms = tokenize(content);
        
        if (queryTerms.isEmpty() || docTerms.isEmpty()) {
            return 0.0;
        }
        
        Map<String, Integer> termFrequency = new HashMap<>();
        for (String term : docTerms) {
            termFrequency.merge(term, 1, Integer::sum);
        }
        
        int docLength = documentLengths.getOrDefault(docId, docTerms.size());
        
        double score = 0.0;
        
        for (String term : queryTerms) {
            int tf = termFrequency.getOrDefault(term, 0);
            if (tf == 0) continue;
            
            int df = documentFrequency.getOrDefault(term, 0);
            double idf = calculateIDF(df);
            
            double numerator = tf * (K1 + 1);
            double denominator = tf + K1 * (1 - B + B * (docLength / avgDocumentLength));
            
            score += idf * (numerator / denominator);
        }
        
        return score;
    }
    
    public List<BM25Result> scoreBatch(String query, List<ChunkDocument> chunks) {
        List<BM25Result> results = new ArrayList<>();
        
        for (ChunkDocument chunk : chunks) {
            double score = score(query, chunk.getChunkId(), chunk.getContent());
            if (score > 0) {
                results.add(new BM25Result(chunk.getChunkId(), score));
            }
        }
        
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        
        return results;
    }
    
    private double calculateIDF(int documentFrequency) {
        if (documentFrequency == 0) {
            return 0;
        }
        return Math.log((totalDocuments - documentFrequency + 0.5) / (documentFrequency + 0.5) + 1);
    }
    
    private void updateAvgDocumentLength() {
        if (totalDocuments > 0) {
            int totalLength = documentLengths.values().stream().mapToInt(Integer::intValue).sum();
            avgDocumentLength = (double) totalLength / totalDocuments;
        }
    }
    
    public List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return tokens;
        }
        
        text = text.toLowerCase();
        
        Matcher matcher = CHINESE_WORD_PATTERN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            
            if (isChinese(token)) {
                for (int i = 0; i < token.length(); i++) {
                    tokens.add(String.valueOf(token.charAt(i)));
                }
            } else {
                tokens.add(token);
            }
        }
        
        return tokens;
    }
    
    private boolean isChinese(String text) {
        for (char c : text.toCharArray()) {
            if (c < '\u4e00' || c > '\u9fa5') {
                return false;
            }
        }
        return true;
    }
    
    public static class BM25Result {
        private final Long chunkId;
        private final double score;
        
        public BM25Result(Long chunkId, double score) {
            this.chunkId = chunkId;
            this.score = score;
        }
        
        public Long getChunkId() { return chunkId; }
        public double getScore() { return score; }
    }
    
    public static class ChunkDocument {
        private final Long chunkId;
        private final String content;
        
        public ChunkDocument(Long chunkId, String content) {
            this.chunkId = chunkId;
            this.content = content;
        }
        
        public Long getChunkId() { return chunkId; }
        public String getContent() { return content; }
    }
}
