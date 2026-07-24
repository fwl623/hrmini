package com.company.hrms.module.attendance.service.serviceImpl;

import com.company.hrms.common.leave.LeaveBalanceBriefDTO;
import com.company.hrms.common.leave.LeaveBalanceQueryService;
import com.company.hrms.module.attendance.dto.LeaveBalanceVO;
import com.company.hrms.module.attendance.service.LeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 假期余额 SPI：供 AI 额度卡片等跨模块只读查询。
 */
@Service
@RequiredArgsConstructor
public class LeaveBalanceQueryServiceImpl implements LeaveBalanceQueryService {

    private final LeaveService leaveService;

    @Override
    public List<LeaveBalanceBriefDTO> listBalances(Long employeeId) {
        List<LeaveBalanceVO> vos = leaveService.getBalances(employeeId);
        List<LeaveBalanceBriefDTO> out = new ArrayList<>();
        if (vos == null) {
            return out;
        }
        for (LeaveBalanceVO vo : vos) {
            if (vo == null) {
                continue;
            }
            out.add(new LeaveBalanceBriefDTO(
                    vo.getLeaveType(),
                    labelOf(vo.getLeaveType()),
                    vo.getBalance()));
        }
        return out;
    }

    private static String labelOf(String leaveType) {
        if (leaveType == null) {
            return "假期";
        }
        return switch (leaveType.toUpperCase(Locale.ROOT)) {
            case "ANNUAL" -> "年假剩余(天)";
            case "COMP_OFF" -> "调休剩余(天)";
            case "SICK" -> "病假";
            case "PERSONAL" -> "事假";
            default -> leaveType;
        };
    }
}
