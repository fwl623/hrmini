package com.company.hrms.employee.feign;

import com.company.hrms.common.web.Result;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * hrms-workflow 审批引擎接口定义（非 Feign 运行时，仅联调约定）
 *
 * ⚠️ 依赖 C 组 (郭策) 的审批模块提供真实审批实例
 * 手机号变更申请提交后，调用此接口创建 MOBILE_CHANGE 审批
 */
public interface ApprovalFeignClient {

    /**
     * 发起审批实例
     * POST /api/v1/approvals/instances
     *
     * ⚠️ 手机号变更审批依赖 C 组审批引擎 — 联调时约定 processType=MOBILE_CHANGE 的请求体
     */
    @PostMapping("/api/v1/approvals/instances")
    Result<StartInstanceResponse> startInstance(@RequestBody StartInstanceRequest request);

    // ===== DTO =====

    class StartInstanceRequest {
        private String processType;
        private String businessKey;
        private Long initiatorId;
        private String title;
        private String businessSummary;
        public String getProcessType() { return processType; }
        public void setProcessType(String processType) { this.processType = processType; }
        public String getBusinessKey() { return businessKey; }
        public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }
        public Long getInitiatorId() { return initiatorId; }
        public void setInitiatorId(Long initiatorId) { this.initiatorId = initiatorId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getBusinessSummary() { return businessSummary; }
        public void setBusinessSummary(String businessSummary) { this.businessSummary = businessSummary; }
    }

    class StartInstanceResponse {
        private Long instanceId;
        public Long getInstanceId() { return instanceId; }
        public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    }
}
