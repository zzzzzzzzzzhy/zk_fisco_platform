package com.wereen.competitionplatform.controller;

import com.wereen.competitionplatform.common.Result;
import com.wereen.competitionplatform.model.entity.Competition;
import com.wereen.competitionplatform.model.entity.Registration;
import com.wereen.competitionplatform.service.RegistrationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 报名控制器
 */
@RestController
@RequestMapping("/registrations")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    /**
     * 报名竞赛
     */
    @PostMapping
    public Result<Registration> register(@RequestBody RegisterRequest request) {
        Registration registration = registrationService.register(
                request.getUserId(),
                request.getCompetitionId(),
                request.getAgreementVersion()
        );
        return Result.success(registration);
    }

    /**
     * 查询用户报名记录
     */
    @GetMapping
    public Result<Registration> getUserRegistration(
            @RequestParam Long userId,
            @RequestParam Long competitionId) {

        Registration registration = registrationService.getUserRegistration(userId, competitionId);
        return Result.success(registration);
    }

    /**
     * 获取用户已报名的竞赛列表
     */
    @GetMapping("/my-competitions")
    public Result<List<Competition>> getUserRegisteredCompetitions(@RequestParam Long userId) {
        List<Competition> competitions = registrationService.getUserRegisteredCompetitions(userId);
        return Result.success(competitions);
    }

    @Data
    public static class RegisterRequest {
        private Long userId;
        private Long competitionId;
        private String agreementVersion;
    }
}
