package com.company.hrms.workflow.controller;

import com.company.hrms.common.web.Result;
import com.company.hrms.workflow.dto.OnboardingDtos;
import com.company.hrms.workflow.service.OnboardingService;
import com.company.hrms.workflow.support.CurrentUserProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/onboarding/applications")
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final CurrentUserProvider currentUserProvider;

    public OnboardingController(OnboardingService onboardingService, CurrentUserProvider currentUserProvider) {
        this.onboardingService = onboardingService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public Result<OnboardingDtos.OnboardingListResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize) {
        return Result.success(onboardingService.list(page, pageSize, status));
    }

    @GetMapping("/stats")
    public Result<OnboardingDtos.OnboardingStatsVO> stats() {
        return Result.success(onboardingService.stats());
    }

    @PostMapping
    public Result<OnboardingDtos.OnboardingVO> create(@RequestBody OnboardingDtos.OnboardingFormRequest body) {
        long userId = currentUserProvider.requireUserId();
        return Result.success(onboardingService.create(body, userId));
    }

    @PutMapping("/{id}")
    public Result<OnboardingDtos.OnboardingVO> update(@PathVariable("id") long id,
                                                      @RequestBody OnboardingDtos.OnboardingFormRequest body) {
        return Result.success(onboardingService.update(id, body));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") long id) {
        onboardingService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/submit")
    public Result<OnboardingDtos.OnboardingVO> submit(@PathVariable("id") long id) {
        long userId = currentUserProvider.requireUserId();
        return Result.success(onboardingService.submit(id, userId));
    }

    @PostMapping("/{id}/withdraw")
    public Result<OnboardingDtos.OnboardingVO> withdraw(@PathVariable("id") long id) {
        long userId = currentUserProvider.requireUserId();
        return Result.success(onboardingService.withdraw(id, userId));
    }

    @PostMapping("/{id}/confirm")
    public Result<OnboardingDtos.OnboardingVO> confirm(@PathVariable("id") long id) {
        return Result.success(onboardingService.confirm(id));
    }

    @PostMapping("/{id}/abandon")
    public Result<OnboardingDtos.OnboardingVO> abandon(@PathVariable("id") long id) {
        return Result.success(onboardingService.abandon(id));
    }
}
