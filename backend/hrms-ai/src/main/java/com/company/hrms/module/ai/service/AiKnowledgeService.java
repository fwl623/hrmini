package com.company.hrms.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.common.enums.RoleCode;
import com.company.hrms.common.exception.ForbiddenException;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.module.ai.client.BailianClient;
import com.company.hrms.module.ai.client.QdrantClient;
import com.company.hrms.module.ai.entity.AiKnowledgeDoc;
import com.company.hrms.module.ai.mapper.AiKnowledgeDocMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiKnowledgeService {

    private final AiKnowledgeDocMapper docMapper;
    private final DocumentTextExtractor extractor;
    private final BailianClient bailianClient;
    private final QdrantClient qdrantClient;

    public void requireSysAdmin() {
        if (!SecurityUtils.requireLoginUser().hasRole(RoleCode.SYS_ADMIN.name())) {
            throw new ForbiddenException();
        }
    }

    public List<AiKnowledgeDoc> listDocs() {
        requireSysAdmin();
        return docMapper.selectList(new LambdaQueryWrapper<AiKnowledgeDoc>()
                .orderByDesc(AiKnowledgeDoc::getId));
    }

    @Transactional
    public AiKnowledgeDoc upload(MultipartFile file, String title) {
        requireSysAdmin();
        if (file == null || file.isEmpty()) {
            throw new com.company.hrms.common.exception.BusinessException(10001, "请上传文件");
        }
        String fileName = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        String docTitle = (title == null || title.isBlank()) ? fileName : title.trim();

        AiKnowledgeDoc doc = new AiKnowledgeDoc();
        doc.setTitle(docTitle);
        doc.setFileName(fileName);
        doc.setStatus("PENDING");
        doc.setEnabled(true);
        doc.setChunkCount(0);
        doc.setCreatedBy(SecurityUtils.getUserId());
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        docMapper.insert(doc);

        try {
            String text = extractor.extract(file);
            List<String> chunks = extractor.chunk(text, 500, 50);
            if (chunks.isEmpty()) {
                throw new com.company.hrms.common.exception.BusinessException(10001, "文档无有效文本内容");
            }
            bailianClient.requireApiKey();
            List<float[]> vectors = new ArrayList<>();
            final int batch = 8;
            for (int from = 0; from < chunks.size(); from += batch) {
                int to = Math.min(from + batch, chunks.size());
                vectors.addAll(bailianClient.embed(chunks.subList(from, to)));
            }
            List<QdrantClient.Point> points = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                points.add(new QdrantClient.Point(
                        QdrantClient.newPointId(),
                        doc.getId(),
                        docTitle,
                        chunks.get(i),
                        i,
                        true,
                        vectors.get(i)
                ));
            }
            qdrantClient.upsertPoints(points);

            doc.setStatus("READY");
            doc.setChunkCount(chunks.size());
            doc.setErrorMessage(null);
            doc.setUpdatedAt(LocalDateTime.now());
            docMapper.updateById(doc);
            return doc;
        } catch (Exception e) {
            log.error("Knowledge upload failed, docId={}", doc.getId(), e);
            doc.setStatus("FAILED");
            doc.setErrorMessage(e.getMessage() != null && e.getMessage().length() > 500
                    ? e.getMessage().substring(0, 500) : e.getMessage());
            doc.setUpdatedAt(LocalDateTime.now());
            docMapper.updateById(doc);
            throw e instanceof com.company.hrms.common.exception.BusinessException be
                    ? be
                    : new com.company.hrms.common.exception.BusinessException(90001, "知识库入库失败: " + e.getMessage());
        }
    }

    @Transactional
    public void setEnabled(Long id, boolean enabled) {
        requireSysAdmin();
        AiKnowledgeDoc doc = docMapper.selectById(id);
        if (doc == null) {
            throw new com.company.hrms.common.exception.BusinessException(10001, "文档不存在");
        }
        doc.setEnabled(enabled);
        doc.setUpdatedAt(LocalDateTime.now());
        docMapper.updateById(doc);
        qdrantClient.setEnabledByDocId(id, enabled);
    }

    @Transactional
    public void delete(Long id) {
        requireSysAdmin();
        AiKnowledgeDoc doc = docMapper.selectById(id);
        if (doc == null) {
            return;
        }
        qdrantClient.deleteByDocId(id);
        docMapper.deleteById(id);
    }
}
