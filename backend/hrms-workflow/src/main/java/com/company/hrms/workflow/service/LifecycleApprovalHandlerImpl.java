package com.company.hrms.workflow.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class LifecycleApprovalHandlerImpl implements LifecycleApprovalHandler {

    private final RegularizationService regularizationService;
    private final TransferService transferService;
    private final ResignationService resignationService;

    public LifecycleApprovalHandlerImpl(@Lazy RegularizationService regularizationService,
                                        @Lazy TransferService transferService,
                                        @Lazy ResignationService resignationService) {
        this.regularizationService = regularizationService;
        this.transferService = transferService;
        this.resignationService = resignationService;
    }

    @Override
    public void onApproved(String processType, String businessKey) {
        Long id = parseId(businessKey);
        if (id == null) {
            return;
        }
        switch (processType) {
            case "REGULARIZATION" -> regularizationService.onApproved(id);
            case "TRANSFER" -> transferService.onApproved(id);
            case "RESIGNATION_REQUEST" -> resignationService.onRequestApproved(id);
            case "RESIGNATION" -> resignationService.onResignationApproved(id);
            default -> {
            }
        }
    }

    @Override
    public void onRejected(String processType, String businessKey) {
        Long id = parseId(businessKey);
        if (id == null) {
            return;
        }
        switch (processType) {
            case "REGULARIZATION" -> regularizationService.onRejectedOrWithdrawn(id);
            case "TRANSFER" -> transferService.onRejectedOrWithdrawn(id, false);
            case "RESIGNATION_REQUEST" -> resignationService.onRequestRejectedOrWithdrawn(id, false);
            case "RESIGNATION" -> resignationService.onResignationRejectedOrWithdrawn(id, false);
            default -> {
            }
        }
    }

    @Override
    public void onWithdrawn(String processType, String businessKey) {
        Long id = parseId(businessKey);
        if (id == null) {
            return;
        }
        switch (processType) {
            case "REGULARIZATION" -> regularizationService.onRejectedOrWithdrawn(id);
            case "TRANSFER" -> transferService.onRejectedOrWithdrawn(id, true);
            case "RESIGNATION_REQUEST" -> resignationService.onRequestRejectedOrWithdrawn(id, true);
            case "RESIGNATION" -> resignationService.onResignationRejectedOrWithdrawn(id, true);
            default -> {
            }
        }
    }

    private static Long parseId(String businessKey) {
        if (businessKey == null || businessKey.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(businessKey.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
