package com.rain.zhixueai.processor;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Slf4j
@Component
public class DocxProcessor implements DocumentProcessor {
    
    private static final int MAX_PARAGRAPHS = 10000;
    private static final int MAX_TABLES = 500;
    
    @Override
    public String extractText(InputStream inputStream) throws Exception {
        log.debug("Extracting text from DOCX file");
        
        StringBuilder text = new StringBuilder();
        
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            
            int paragraphCount = 0;
            for (XWPFParagraph paragraph : paragraphs) {
                if (paragraphCount >= MAX_PARAGRAPHS) {
                    log.warn("DOCX has too many paragraphs, limiting to {}", MAX_PARAGRAPHS);
                    text.append("\n...[内容已截断，文档段落过多]\n");
                    break;
                }
                
                String paragraphText = paragraph.getText();
                if (paragraphText != null && !paragraphText.trim().isEmpty()) {
                    text.append(paragraphText.trim()).append("\n");
                }
                paragraphCount++;
            }
            
            List<XWPFTable> tables = document.getTables();
            if (tables != null && !tables.isEmpty()) {
                text.append("\n--- 表格内容 ---\n");
                
                int tableCount = 0;
                for (XWPFTable table : tables) {
                    if (tableCount >= MAX_TABLES) {
                        log.warn("DOCX has too many tables, limiting to {}", MAX_TABLES);
                        break;
                    }
                    
                    for (XWPFTableRow row : table.getRows()) {
                        StringBuilder rowText = new StringBuilder();
                        for (XWPFTableCell cell : row.getTableCells()) {
                            String cellText = cell.getText();
                            if (cellText != null && !cellText.trim().isEmpty()) {
                                if (rowText.length() > 0) {
                                    rowText.append(" | ");
                                }
                                rowText.append(cellText.trim());
                            }
                        }
                        if (rowText.length() > 0) {
                            text.append(rowText).append("\n");
                        }
                    }
                    text.append("\n");
                    tableCount++;
                }
            }
            
        } catch (IOException e) {
            log.error("Failed to process DOCX file: {}", e.getMessage());
            throw new IOException("DOCX文件处理失败: " + e.getMessage(), e);
        } catch (OutOfMemoryError e) {
            log.error("Out of memory while processing DOCX: {}", e.getMessage());
            throw new IOException("DOCX文件过大，内存不足: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error processing DOCX: {}", e.getMessage());
            throw new IOException("DOCX文件格式错误或已损坏: " + e.getMessage(), e);
        }
        
        String result = text.toString().trim();
        if (result.isEmpty()) {
            log.warn("DOCX file appears to be empty");
            throw new IOException("DOCX文件内容为空");
        }
        
        return result;
    }
    
    @Override
    public String getSupportedType() {
        return "DOCX";
    }
    
    @Override
    public boolean supports(String fileType) {
        return "DOCX".equalsIgnoreCase(fileType) || "DOC".equalsIgnoreCase(fileType);
    }
}
