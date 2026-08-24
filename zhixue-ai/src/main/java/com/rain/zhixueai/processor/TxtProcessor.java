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
public class TxtProcessor implements DocumentProcessor {
    
    private static final int MAX_CONTENT_LENGTH = 10 * 1024 * 1024;
    
    @Override
    public String extractText(InputStream inputStream) throws Exception {
        log.debug("Extracting text from TXT file");
        
        StringBuilder text = new StringBuilder();
        Charset charset = detectCharset(inputStream);
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, charset))) {
            String line;
            int totalLength = 0;
            while ((line = reader.readLine()) != null) {
                if (totalLength + line.length() > MAX_CONTENT_LENGTH) {
                    log.warn("TXT file content truncated at {} characters", MAX_CONTENT_LENGTH);
                    break;
                }
                text.append(line).append("\n");
                totalLength += line.length() + 1;
            }
        } catch (IOException e) {
            log.error("Failed to read TXT file: {}", e.getMessage());
            throw new IOException("TXT文件读取失败: " + e.getMessage(), e);
        }
        
        String result = text.toString().trim();
        if (result.isEmpty()) {
            log.warn("TXT file appears to be empty or contains only whitespace");
        }
        
        return result;
    }
    
    private Charset detectCharset(InputStream inputStream) {
        return StandardCharsets.UTF_8;
    }
    
    @Override
    public String getSupportedType() {
        return "TXT";
    }
    
    @Override
    public boolean supports(String fileType) {
        return "TXT".equalsIgnoreCase(fileType) || 
               "TEXT".equalsIgnoreCase(fileType);
    }
}
