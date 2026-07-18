package com.company.hrms.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDate;

@Getter
public class EmployeeStatusChangeEvent extends ApplicationEvent {

    private final Long employeeId;
    private final String oldStatus;
    private final String newStatus;
    private final LocalDate effectDate;
    private final String triggerSource;
    private final LocalDate lastWorkDay;

    public EmployeeStatusChangeEvent(Object source,
                                     Long employeeId,
                                     String oldStatus,
                                     String newStatus,
                                     LocalDate effectDate,
                                     String triggerSource,
                                     LocalDate lastWorkDay) {
        super(source);
        this.employeeId = employeeId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.effectDate = effectDate;
        this.triggerSource = triggerSource;
        this.lastWorkDay = lastWorkDay;
    }
}
