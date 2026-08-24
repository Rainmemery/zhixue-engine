package com.rain.zhixueai.processor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class MarkdownProcessor implements DocumentProcessor {
    
    private static final int MAX_CONTENT_LENGTH = 10 * 1024 * 1024;
    
    @Override
    public String extractText(InputStream inputStream) throws Exception {
        log.debug("Extracting text from Markdown file");
        
        StringBuilder text = new StringBuilder();
        Charset charset = StandardCharsets.UTF_8;
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, charset))) {
            String line;
            int totalLength = 0;
            
            while ((line = reader.readLine()) != null) {
                if (totalLength + line.length() > MAX_CONTENT_LENGTH) {
                    log.warn("Markdown file content truncated at {} characters", MAX_CONTENT_LENGTH);
                    break;
                }
                
                String processedLine = processMarkdownLine(line);
                text.append(processedLine).append("\n");
                totalLength += line.length() + 1;
            }
        } catch (IOException e) {
            log.error("Failed to read Markdown file: {}", e.getMessage());
            throw new IOException("Markdown文件读取失败: " + e.getMessage(), e);
        }
        
        String result = text.toString().trim();
        if (result.isEmpty()) {
            log.warn("Markdown file appears to be empty");
        }
        
        return result;
    }
    
    private String processMarkdownLine(String line) {
        line = line.replaceAll("^#{1,6}\\s+", "");
        line = line.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        line = line.replaceAll("\\*(.+?)\\*", "$1");
        line = line.replaceAll("__(.+?)__", "$1");
        line = line.replaceAll("_(.+?)_", "$1");
        line = line.replaceAll("`(.+?)`", "$1");
        line = line.replaceAll("```\\w*\\n?", "");
        line = line.replaceAll("\\[(.+?)\\]\\(.+?\\)", "$1");
        line = line.replaceAll("^\\s*[-*+]\\s+", "");
        line = line.replaceAll("^\\s*\\d+\\.\\s+", "");
        line = line.replaceAll("^\\s*>\\s+", "");
        line = line.replaceAll("!\\[.+?\\]\\(.+?\\)", "[图片]");
        line = line.replaceAll("<[^>]+>", "");
        
        return line;
    }
    
    @Override
    public String getSupportedType() {
        return "MD";
    }
    
    @Override
    public boolean supports(String fileType) {
        return "MD".equalsIgnoreCase(fileType) || 
               "MARKDOWN".equalsIgnoreCase(fileType) ||
               "MDOWN".equalsIgnoreCase(fileType) ||
               "MKD".equalsIgnoreCase(fileType);
    }
}
