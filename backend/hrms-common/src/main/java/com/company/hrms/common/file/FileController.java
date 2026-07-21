package com.company.hrms.common.file;

import com.company.hrms.common.web.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 通用文件上传/下载（请假证明材料等）。
 * <p>
 * POST /api/v1/files — 上传<br>
 * GET  /api/v1/files/{storedName} — 下载/预览
 */
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(fileStorageService.store(file));
    }

    @GetMapping("/{storedName}")
    public ResponseEntity<Resource> download(@PathVariable String storedName) {
        Resource resource = fileStorageService.loadAsResource(storedName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fileStorageService.contentTypeOf(storedName)))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + storedName + "\"")
                .body(resource);
    }
}
