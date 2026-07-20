package com.company.hrms.module.ai.service;

import com.company.hrms.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class DocumentTextExtractor {

    public String extract(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".pdf")) {
                return extractPdf(file.getBytes());
            }
            // md / txt / 其他按文本
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(10001, "读取上传文件失败: " + e.getMessage());
        }
    }

    private String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    /** 按字符粗分块，约 500 字一块，重叠 50 */
    public List<String> chunk(String text, int size, int overlap) {
        String cleaned = text == null ? "" : text.replace("\r\n", "\n").trim();
        if (cleaned.isEmpty()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int i = 0;
        while (i < cleaned.length()) {
            int end = Math.min(i + size, cleaned.length());
            chunks.add(cleaned.substring(i, end));
            if (end >= cleaned.length()) {
                break;
            }
            i = Math.max(end - overlap, i + 1);
        }
        return chunks;
    }
}
