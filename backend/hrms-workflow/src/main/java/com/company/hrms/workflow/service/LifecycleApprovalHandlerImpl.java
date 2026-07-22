package com.company.hrms.workflow.service;



import org.springframework.context.annotation.Lazy;

import org.springframework.stereotype.Service;



/**

 * 【审批终态回调分发器】{@link DbApprovalService} 在实例 APPROVED / REJECTED / 撤回 CANCELLED 时调用，

 * 按 {@code processType} 路由到对应入转调离业务 Service，更新业务单据状态（不直接操作 instance/task 表）。

 * 分发矩阵：

 *   ONBOARDING → {@link OnboardingService#onApprovalFinished} / {@link OnboardingService#onApprovalWithdrawn}

 *   REGULARIZATION → {@link RegularizationService#onApproved} / {@link RegularizationService#onRejectedOrWithdrawn}

 *   TRANSFER → {@link TransferService#onApproved} / {@link TransferService#onRejectedOrWithdrawn}

 *   RESIGNATION_REQUEST → {@link ResignationService#onRequestApproved} / {@link ResignationService#onRequestRejectedOrWithdrawn}

 *   RESIGNATION → {@link ResignationService#onResignationApproved} / {@link ResignationService#onResignationRejectedOrWithdrawn}

 * 考勤/薪资等流程的终态由各自模块消费 {@code ApprovalCompletedEvent} 或 MQ，不经本类。

 */

@Service

public class LifecycleApprovalHandlerImpl implements LifecycleApprovalHandler {



    private final RegularizationService regularizationService;

    private final TransferService transferService;

    private final ResignationService resignationService;

    private final OnboardingService onboardingService;



    public LifecycleApprovalHandlerImpl(@Lazy RegularizationService regularizationService,

                                        @Lazy TransferService transferService,

                                        @Lazy ResignationService resignationService,

                                        @Lazy OnboardingService onboardingService) {

        this.regularizationService = regularizationService;

        this.transferService = transferService;

        this.resignationService = resignationService;

        this.onboardingService = onboardingService;

    }



    /**

     * 审批实例全部节点通过后，按 processType 回调各业务 Service 执行终态逻辑（如转正、调岗生效、离职待生效）。

     * 【调用】{@code DbApprovalService.action}（最后一岗 APPROVE 且无下一节点时）

     */

    @Override

    public void onApproved(String processType, String businessKey) {

        Long id = parseId(businessKey);

        if (id == null) {

            return;

        }

        switch (processType) {

            case "ONBOARDING" -> onboardingService.onApprovalFinished(businessKey, true, null);

            case "REGULARIZATION" -> regularizationService.onApproved(id);

            case "TRANSFER" -> transferService.onApproved(id);

            case "RESIGNATION_REQUEST" -> resignationService.onRequestApproved(id);

            case "RESIGNATION" -> resignationService.onResignationApproved(id);

            default -> {

            }

        }

    }



    /**

     * 审批被驳回时，按 processType 回调各业务 Service 将单据置为 rejected 或等价终态。

     * 【调用】{@code DbApprovalService.action}（REJECT 分支）

     * 【实现】

     *   <li>{@code parseId(businessKey)} 解析业务 id</li>

     *   <li>ONBOARDING → {@code OnboardingService.onApprovalFinished(businessKey, false, null)}</li>

     *   <li>REGULARIZATION / TRANSFER / RESIGNATION* → 各 Service 的 rejected 回调（withdrawn=false）</li>

     */

    @Override

    public void onRejected(String processType, String businessKey) {

        Long id = parseId(businessKey);

        if (id == null) {

            return;

        }

        switch (processType) {

            case "ONBOARDING" -> onboardingService.onApprovalFinished(businessKey, false, null);

            case "REGULARIZATION" -> regularizationService.onRejectedOrWithdrawn(id);

            case "TRANSFER" -> transferService.onRejectedOrWithdrawn(id, false);

            case "RESIGNATION_REQUEST" -> resignationService.onRequestRejectedOrWithdrawn(id, false);

            case "RESIGNATION" -> resignationService.onResignationRejectedOrWithdrawn(id, false);

            default -> {

            }

        }

    }



    /**

     * 发起人撤回审批实例时，按 processType 回调各业务 Service 回退单据（如入职回 draft、调岗 CANCELLED）。

     * 【调用】{@code DbApprovalService.doWithdrawInstance}

     * 【实现】

     *   <li>{@code parseId(businessKey)} 解析业务 id</li>

     *   <li>ONBOARDING → {@code OnboardingService.onApprovalWithdrawn}</li>

     *   <li>REGULARIZATION → {@code RegularizationService.onRejectedOrWithdrawn}（与驳回共用）</li>

     *   <li>TRANSFER / RESIGNATION* → 各 Service 的 withdrawn=true 分支</li>

     */

    @Override

    public void onWithdrawn(String processType, String businessKey) {

        Long id = parseId(businessKey);

        if (id == null) {

            return;

        }

        switch (processType) {

            case "ONBOARDING" -> onboardingService.onApprovalWithdrawn(businessKey);

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

            int idx = businessKey.lastIndexOf(':');

            if (idx >= 0 && idx + 1 < businessKey.length()) {

                try {

                    return Long.parseLong(businessKey.substring(idx + 1).trim());

                } catch (NumberFormatException ignored) {

                    return null;

                }

            }

            return null;

        }

    }

}

