package com.rain.zhixueai.processor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DocumentProcessorFactory {
    
    private final Map<String, DocumentProcessor> processors = new HashMap<>();
    
    public DocumentProcessorFactory(List<DocumentProcessor> processorList) {
        for (DocumentProcessor processor : processorList) {
            processors.put(processor.getSupportedType().toUpperCase(), processor);
            log.info("Registered document processor: {}", processor.getSupportedType());
        }
    }
    
    public DocumentProcessor getProcessor(String fileType) {
        String type = fileType.toUpperCase();
        DocumentProcessor processor = processors.get(type);
        if (processor == null) {
            throw new IllegalArgumentException("Unsupported file type: " + fileType);
        }
        return processor;
    }
    
    public boolean isSupported(String fileType) {
        return processors.containsKey(fileType.toUpperCase());
    }
    
    public String extractText(String fileType, InputStream inputStream) throws Exception {
        DocumentProcessor processor = getProcessor(fileType);
        return processor.extractText(inputStream);
    }
}
