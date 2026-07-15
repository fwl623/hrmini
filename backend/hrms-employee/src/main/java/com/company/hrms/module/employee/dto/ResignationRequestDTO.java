package com.company.hrms.module.employee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 员工离职申请（门户）
 * POST /api/v1/profile/resignation-requests
 */
@Data
@Schema(description = "员工离职申请")
public class ResignationRequestDTO {

    @NotNull
    @FutureOrPresent
    @Schema(description = "期望离职日期")
    private LocalDate expectedResignDate;

    @NotBlank
    @Schema(description = "离职原因分类 VOLUNTARY/INVOLUNTARY/NEGOTIATED")
    private String reasonCategory;

    @NotBlank
    @Schema(description = "离职类型 resignation/dismissal/contract_expiry/other")
    private String resignationType;

    @Schema(description = "详细说明")
    private String reasonDetail;
}
