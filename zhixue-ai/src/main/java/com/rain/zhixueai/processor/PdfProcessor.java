package com.rain.zhixueai.processor;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class PdfProcessor implements DocumentProcessor {
    
    private static final int MAX_PAGES = 2000;
    private static final int MAX_CHARS_PER_PAGE = 100000;
    private static final int PARALLEL_THRESHOLD = 50;
    private static final int BATCH_SIZE = 20;
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    
    @Override
    public String extractText(InputStream inputStream) throws Exception {
        log.debug("Extracting text from PDF file");
        
        StringBuilder text = new StringBuilder();
        
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            
            int numberOfPages = document.getNumberOfPages();
            log.debug("PDF has {} pages", numberOfPages);
            
            if (numberOfPages > MAX_PAGES) {
                log.warn("PDF has {} pages, limiting to {} pages", numberOfPages, MAX_PAGES);
                numberOfPages = MAX_PAGES;
            }
            
            if (numberOfPages > PARALLEL_THRESHOLD) {
                text.append(extractTextParallel(document, stripper, numberOfPages));
            } else {
                text.append(extractTextSequential(document, stripper, numberOfPages));
            }
            
        } catch (IOException e) {
            log.error("Failed to process PDF file: {}", e.getMessage());
            throw new IOException("PDF文件处理失败: " + e.getMessage(), e);
        } catch (OutOfMemoryError e) {
            log.error("Out of memory while processing PDF: {}", e.getMessage());
            throw new IOException("PDF文件过大，内存不足: " + e.getMessage());
        }
        
        String result = text.toString().trim();
        if (result.isEmpty()) {
            log.warn("PDF file appears to contain no extractable text (possibly scanned image)");
            throw new IOException("PDF文件无法提取文本内容，可能是扫描图片或加密文档");
        }
        
        return result;
    }
    
    private String extractTextSequential(PDDocument document, PDFTextStripper stripper, int numberOfPages) {
        StringBuilder text = new StringBuilder();
        
        for (int i = 1; i <= numberOfPages; i++) {
            try {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(document);
                
                if (pageText != null && !pageText.trim().isEmpty()) {
                    if (pageText.length() > MAX_CHARS_PER_PAGE) {
                        pageText = pageText.substring(0, MAX_CHARS_PER_PAGE) + "...[内容已截断]";
                        log.warn("Page {} content truncated at {} characters", i, MAX_CHARS_PER_PAGE);
                    }
                    text.append(pageText.trim()).append("\n\n");
                }
            } catch (IOException e) {
                log.warn("Failed to extract text from page {}: {}", i, e.getMessage());
                text.append("[第").append(i).append("页内容提取失败]\n\n");
            }
            
            if (i % 100 == 0) {
                log.debug("Processed {}/{} pages", i, numberOfPages);
            }
        }
        
        return text.toString();
    }
    
    private String extractTextParallel(PDDocument document, PDFTextStripper stripper, int numberOfPages) {
        log.info("Using parallel extraction for PDF with {} pages", numberOfPages);
        
        List<Callable<String>> tasks = new ArrayList<>();
        
        for (int batchStart = 1; batchStart <= numberOfPages; batchStart += BATCH_SIZE) {
            final int start = batchStart;
            final int end = Math.min(batchStart + BATCH_SIZE - 1, numberOfPages);
            
            tasks.add(() -> {
                StringBuilder batchText = new StringBuilder();
                PDFTextStripper batchStripper = new PDFTextStripper();
                batchStripper.setSortByPosition(true);
                
                for (int i = start; i <= end; i++) {
                    try {
                        batchStripper.setStartPage(i);
                        batchStripper.setEndPage(i);
                        String pageText = batchStripper.getText(document);
                        
                        if (pageText != null && !pageText.trim().isEmpty()) {
                            if (pageText.length() > MAX_CHARS_PER_PAGE) {
                                pageText = pageText.substring(0, MAX_CHARS_PER_PAGE) + "...[内容已截断]";
                            }
                            batchText.append(pageText.trim()).append("\n\n");
                        }
                    } catch (IOException e) {
                        batchText.append("[第").append(i).append("页内容提取失败]\n\n");
                    }
                }
                
                return batchText.toString();
            });
        }
        
        try {
            List<Future<String>> futures = executorService.invokeAll(tasks, 10, TimeUnit.MINUTES);
            
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < futures.size(); i++) {
                try {
                    String batchResult = futures.get(i).get(5, TimeUnit.MINUTES);
                    result.append(batchResult);
                    
                    int processedPages = Math.min((i + 1) * BATCH_SIZE, numberOfPages);
                    if (processedPages % 200 == 0 || processedPages == numberOfPages) {
                        log.debug("Parallel processed {}/{} pages", processedPages, numberOfPages);
                    }
                } catch (Exception e) {
                    log.warn("Failed to get batch result {}: {}", i, e.getMessage());
                }
            }
            
            return result.toString();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Parallel extraction interrupted");
            return extractTextSequential(document, stripper, numberOfPages);
        }
    }
    
    @Override
    public String getSupportedType() {
        return "PDF";
    }
    
    @Override
    public boolean supports(String fileType) {
        return "PDF".equalsIgnoreCase(fileType);
    }
}
