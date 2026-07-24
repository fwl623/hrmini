package com.company.hrms.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档元数据实体（表 ai_knowledge_doc）；向量分块存在 Qdrant，不在本表。
 */
@Data
@TableName("ai_knowledge_doc")
public class AiKnowledgeDoc {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    @TableField("file_name")
    private String fileName;
    /** PENDING / READY / FAILED */
    private String status;
    private Boolean enabled;
    @TableField("chunk_count")
    private Integer chunkCount;
    @TableField("error_message")
    private String errorMessage;
    @TableField("created_by")
    private Long createdBy;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
