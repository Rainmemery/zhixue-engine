package com.rain.zhixueai.util;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class TextChunker {
    
    private final int chunkSize;
    private final int overlap;
    
    private static final Pattern SENTENCE_PATTERN = Pattern.compile(
        "[^。！？.!?]+[。！？.!?]+|[^。！？.!?]+$"
    );
    
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile("\n{2,}");
    
    private static final Pattern SECTION_HEADER_PATTERN = Pattern.compile(
        "^(#{1,6}\\s+.+|第[一二三四五六七八九十]+[章节篇部].*|[一二三四五六七八九十]+[、.．].+|\\d+[、.．].+)$",
        Pattern.MULTILINE
    );
    
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
        "```[\\s\\S]*?```|`[^`]+`",
        Pattern.DOTALL
    );
    
    private static final Pattern CHINESE_SENTENCE_PATTERN = Pattern.compile(
        "[^。！？；\\n]+[。！？；]+|[^。！？；\\n]+$"
    );
    
    private static final Pattern TECH_TERM_PATTERN = Pattern.compile(
        "[a-zA-Z_][a-zA-Z0-9_]*|\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?"
    );
    
    public TextChunker(int chunkSize, int overlap) {
        this.chunkSize = Math.max(100, chunkSize);
        this.overlap = Math.min(Math.max(0, overlap), chunkSize / 2);
    }
    
    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        
        text = preprocessText(text);
        
        if (text.length() <= chunkSize) {
            chunks.add(text);
            return chunks;
        }
        
        List<TextSegment> segments = extractSegments(text);
        
        List<String> mergedChunks = mergeSegments(segments, text);
        
        log.debug("Text chunked into {} chunks (chunkSize={}, overlap={})", mergedChunks.size(), chunkSize, overlap);
        return mergedChunks;
    }
    
    private String preprocessText(String text) {
        text = text.replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]", "");
        text = text.replaceAll("\\r\\n", "\n");
        text = text.replaceAll("[ \\t]+", " ");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.trim();
    }
    
    private List<TextSegment> extractSegments(String text) {
        List<TextSegment> segments = new ArrayList<>();
        
        List<int[]> codeBlocks = new ArrayList<>();
        Matcher codeMatcher = CODE_BLOCK_PATTERN.matcher(text);
        while (codeMatcher.find()) {
            codeBlocks.add(new int[]{codeMatcher.start(), codeMatcher.end()});
        }
        
        int lastEnd = 0;
        String[] paragraphs = PARAGRAPH_PATTERN.split(text);
        
        for (String paragraph : paragraphs) {
            if (paragraph.trim().isEmpty()) continue;
            
            int start = text.indexOf(paragraph, lastEnd);
            int end = start + paragraph.length();
            lastEnd = end;
            
            boolean isCodeBlock = false;
            for (int[] cb : codeBlocks) {
                if (start >= cb[0] && end <= cb[1]) {
                    isCodeBlock = true;
                    break;
                }
            }
            
            SegmentType type = classifySegment(paragraph, isCodeBlock);
            
            if (paragraph.length() > chunkSize && type != SegmentType.CODE) {
                List<TextSegment> subSegments = splitLargeSegment(paragraph, start, type);
                segments.addAll(subSegments);
            } else {
                segments.add(new TextSegment(paragraph, start, end, type));
            }
        }
        
        return segments;
    }
    
    private SegmentType classifySegment(String text, boolean isCodeBlock) {
        if (isCodeBlock) {
            return SegmentType.CODE;
        }
        
        if (SECTION_HEADER_PATTERN.matcher(text).matches()) {
            return SegmentType.HEADER;
        }
        
        int chineseCount = 0;
        int codeCount = 0;
        
        for (char c : text.toCharArray()) {
            if (Character.isIdeographic(c)) {
                chineseCount++;
            } else if (c == '{' || c == '}' || c == '(' || c == ')' || 
                       c == '[' || c == ']' || c == ';' || c == '=') {
                codeCount++;
            }
        }
        
        if (codeCount > text.length() * 0.1) {
            return SegmentType.MIXED_CODE;
        }
        
        if (chineseCount > text.length() * 0.3) {
            return SegmentType.CHINESE;
        }
        
        return SegmentType.GENERAL;
    }
    
    private List<TextSegment> splitLargeSegment(String text, int offset, SegmentType type) {
        List<TextSegment> segments = new ArrayList<>();
        
        if (type == SegmentType.CHINESE) {
            List<String> sentences = splitChineseSentences(text);
            StringBuilder current = new StringBuilder();
            int currentStart = offset;
            
            for (String sentence : sentences) {
                if (current.length() + sentence.length() > chunkSize && current.length() > 0) {
                    segments.add(new TextSegment(current.toString().trim(), currentStart, 
                            currentStart + current.length(), type));
                    currentStart = offset + text.indexOf(sentence, currentStart - offset);
                    current = new StringBuilder();
                }
                current.append(sentence);
            }
            
            if (current.length() > 0) {
                segments.add(new TextSegment(current.toString().trim(), currentStart, 
                        currentStart + current.length(), type));
            }
        } else {
            List<String> sentences = splitBySentences(text);
            StringBuilder current = new StringBuilder();
            int currentStart = offset;
            
            for (String sentence : sentences) {
                if (current.length() + sentence.length() > chunkSize && current.length() > 0) {
                    segments.add(new TextSegment(current.toString().trim(), currentStart, 
                            currentStart + current.length(), type));
                    currentStart = offset + text.indexOf(sentence, currentStart - offset);
                    current = new StringBuilder();
                }
                current.append(sentence).append(" ");
            }
            
            if (current.length() > 0) {
                segments.add(new TextSegment(current.toString().trim(), currentStart, 
                        currentStart + current.length(), type));
            }
        }
        
        return segments;
    }
    
    private List<String> splitChineseSentences(String text) {
        List<String> sentences = new ArrayList<>();
        Matcher matcher = CHINESE_SENTENCE_PATTERN.matcher(text);
        
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }
        
        if (sentences.isEmpty()) {
            sentences.add(text);
        }
        
        return sentences;
    }
    
    private List<String> mergeSegments(List<TextSegment> segments, String originalText) {
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        int currentSize = 0;
        
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            String content = segment.content;
            
            if (content.length() > chunkSize) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                    currentSize = 0;
                }
                
                for (String part : splitByFixedSize(content)) {
                    chunks.add(part);
                }
                continue;
            }
            
            if (currentSize + content.length() + 1 > chunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                
                if (overlap > 0) {
                    String overlapText = getOverlapText(currentChunk.toString());
                    currentChunk = new StringBuilder(overlapText);
                    currentSize = overlapText.length();
                } else {
                    currentChunk = new StringBuilder();
                    currentSize = 0;
                }
            }
            
            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
                currentSize += 2;
            }
            currentChunk.append(content);
            currentSize += content.length();
        }
        
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        
        return chunks;
    }
    
    private List<String> splitBySentences(String text) {
        List<String> sentences = new ArrayList<>();
        Matcher matcher = SENTENCE_PATTERN.matcher(text);
        
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }
        
        if (sentences.isEmpty()) {
            sentences.add(text);
        }
        
        return sentences;
    }
    
    private List<String> splitByFixedSize(String text) {
        List<String> chunks = new ArrayList<>();
        
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            
            if (end < text.length()) {
                int breakPoint = findBreakPoint(text, start, end);
                if (breakPoint > start) {
                    end = breakPoint;
                }
            }
            
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            
            start = end;
            if (overlap > 0 && start < text.length()) {
                start = Math.max(0, start - overlap);
            }
        }
        
        return chunks;
    }
    
    private int findBreakPoint(String text, int start, int end) {
        for (int i = end - 1; i >= start + chunkSize / 2; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?' ||
                c == '\n' || c == '；' || c == ';' || c == '，' || c == ',') {
                return i + 1;
            }
        }
        return end;
    }
    
    private String getOverlapText(String lastChunk) {
        if (lastChunk.length() <= overlap) {
            return lastChunk;
        }
        
        int start = lastChunk.length() - overlap;
        
        for (int i = start; i < lastChunk.length(); i++) {
            char c = lastChunk.charAt(i);
            if (c == '。' || c == '\n' || c == ' ' || c == '.') {
                start = i + 1;
                break;
            }
        }
        
        return lastChunk.substring(Math.min(start, lastChunk.length() - overlap / 2));
    }
    
    public List<String> chunkWithMetadata(String text, String documentTitle) {
        List<String> chunks = chunk(text);
        List<String> enrichedChunks = new ArrayList<>();
        
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            String enriched = String.format("[文档: %s - 第%d/%d段]\n%s", 
                    documentTitle, i + 1, chunks.size(), chunk);
            enrichedChunks.add(enriched);
        }
        
        return enrichedChunks;
    }
    
    public List<ChunkWithMetadata> chunkWithFullMetadata(String text, String documentTitle, String documentId) {
        List<String> chunks = chunk(text);
        List<ChunkWithMetadata> result = new ArrayList<>();
        
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            ChunkWithMetadata metadata = new ChunkWithMetadata();
            metadata.setContent(chunk);
            metadata.setDocumentTitle(documentTitle);
            metadata.setDocumentId(documentId);
            metadata.setChunkIndex(i);
            metadata.setTotalChunks(chunks.size());
            metadata.setCharStart(text.indexOf(chunk));
            metadata.setCharEnd(metadata.getCharStart() + chunk.length());
            
            result.add(metadata);
        }
        
        return result;
    }
    
    public static List<String> splitByParagraphs(String text) {
        List<String> paragraphs = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return paragraphs;
        }
        
        String[] parts = PARAGRAPH_PATTERN.split(text);
        
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        
        return paragraphs;
    }
    
    public static List<String> splitBySections(String text) {
        List<String> sections = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return sections;
        }
        
        String[] parts = SECTION_HEADER_PATTERN.split(text);
        
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                sections.add(trimmed);
            }
        }
        
        if (sections.isEmpty()) {
            return splitByParagraphs(text);
        }
        
        return sections;
    }
    
    private static class TextSegment {
        String content;
        int start;
        int end;
        SegmentType type;
        
        TextSegment(String content, int start, int end, SegmentType type) {
            this.content = content;
            this.start = start;
            this.end = end;
            this.type = type;
        }
    }
    
    private enum SegmentType {
        CHINESE,
        CODE,
        MIXED_CODE,
        HEADER,
        GENERAL
    }
    
    public static class ChunkWithMetadata {
        private String content;
        private String documentTitle;
        private String documentId;
        private int chunkIndex;
        private int totalChunks;
        private int charStart;
        private int charEnd;
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getDocumentTitle() { return documentTitle; }
        public void setDocumentTitle(String documentTitle) { this.documentTitle = documentTitle; }
        public String getDocumentId() { return documentId; }
        public void setDocumentId(String documentId) { this.documentId = documentId; }
        public int getChunkIndex() { return chunkIndex; }
        public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
        public int getTotalChunks() { return totalChunks; }
        public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }
        public int getCharStart() { return charStart; }
        public void setCharStart(int charStart) { this.charStart = charStart; }
        public int getCharEnd() { return charEnd; }
        public void setCharEnd(int charEnd) { this.charEnd = charEnd; }
    }
}
