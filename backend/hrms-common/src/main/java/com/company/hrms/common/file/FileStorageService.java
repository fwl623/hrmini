package com.company.hrms.common.file;

import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 本地磁盘文件存储（请假证明、附件等）：校验扩展名与大小、UUID 落盘、按名加载 Resource。
 */
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final Set<String> ALLOWED_EXT = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp",
            "pdf", "doc", "docx", "xls", "xlsx", "txt"
    );

    private final FileStorageProperties properties;
    private Path rootDir;

    @PostConstruct
    void init() throws IOException {
        rootDir = Paths.get(properties.getDir()).toAbsolutePath().normalize();
        Files.createDirectories(rootDir);
    }

    public Map<String, String> store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请选择要上传的文件");
        }
        if (file.getSize() > properties.getMaxSizeBytes()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "文件大小不能超过 10MB");
        }
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String ext = extensionOf(original);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "仅支持图片或常见文档（jpg/png/pdf/doc/docx/xls/xlsx 等）");
        }
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = rootDir.resolve(storedName).normalize();
        if (!target.startsWith(rootDir)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "非法文件名");
        }
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件保存失败");
        }
        String url = "/api/v1/files/" + storedName;
        return Map.of(
                "url", url,
                "fileName", original.isBlank() ? storedName : original,
                "storedName", storedName
        );
    }

    public Resource loadAsResource(String storedName) {
        if (!StringUtils.hasText(storedName) || storedName.contains("..") || storedName.contains("/") || storedName.contains("\\")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "非法文件名");
        }
        String ext = extensionOf(storedName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "不支持的文件类型");
        }
        try {
            Path file = rootDir.resolve(storedName).normalize();
            if (!file.startsWith(rootDir) || !Files.exists(file) || !Files.isRegularFile(file)) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在");
            }
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在");
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在");
        }
    }

    public String contentTypeOf(String storedName) {
        String ext = extensionOf(storedName);
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }

    private static String extensionOf(String name) {
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) {
            return "";
        }
        return name.substring(idx + 1).toLowerCase(Locale.ROOT);
    }
}
