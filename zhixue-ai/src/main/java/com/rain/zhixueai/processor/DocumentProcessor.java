package com.rain.zhixueai.processor;

import java.io.InputStream;

public interface DocumentProcessor {
    
    String extractText(InputStream inputStream) throws Exception;
    
    String getSupportedType();
    
    boolean supports(String fileType);
}
