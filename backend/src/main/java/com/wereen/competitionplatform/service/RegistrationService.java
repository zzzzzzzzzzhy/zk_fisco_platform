package com.wereen.competitionplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.mapper.CompetitionMapper;
import com.wereen.competitionplatform.mapper.RegistrationMapper;
import com.wereen.competitionplatform.model.entity.Competition;
import com.wereen.competitionplatform.model.entity.Registration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 报名服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final RegistrationMapper registrationMapper;
    private final CompetitionMapper competitionMapper;
    private final CompetitionService competitionService;

    /**
     * 报名竞赛
     */
    @Transactional(rollbackFor = Exception.class)
    public Registration register(Long userId, Long competitionId, String agreementVersion) {
        // 检查竞赛是否存在
        Competition competition = competitionService.getCompetitionById(competitionId);

        // 检查是否已报名
        LambdaQueryWrapper<Registration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Registration::getUserId, userId)
                .eq(Registration::getCompetitionId, competitionId);

        if (registrationMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("您已报名该竞赛");
        }

        // 创建报名记录
        Registration registration = new Registration();
        registration.setUserId(userId);
        registration.setCompetitionId(competitionId);
        registration.setAgreementVersion(agreementVersion);
        registration.setStatus(0); // 待审核

        int rows = registrationMapper.insert(registration);
        if (rows == 0) {
            throw new BusinessException("报名失败");
        }

        log.info("报名成功: userId={}, competitionId={}", userId, competitionId);
        return registration;
    }

    /**
     * 查询用户报名记录
     */
    public Registration getUserRegistration(Long userId, Long competitionId) {
        LambdaQueryWrapper<Registration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Registration::getUserId, userId)
                .eq(Registration::getCompetitionId, competitionId);

        return registrationMapper.selectOne(wrapper);
    }

    /**
     * 获取用户已报名的竞赛列表
     */
    public List<Competition> getUserRegisteredCompetitions(Long userId) {
        // 查询用户的所有报名记录
        LambdaQueryWrapper<Registration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Registration::getUserId, userId)
                .orderByDesc(Registration::getCreatedAt);

        List<Registration> registrations = registrationMapper.selectList(wrapper);

        if (registrations.isEmpty()) {
            return new ArrayList<>();
        }

        // 获取竞赛ID列表
        List<Long> competitionIds = registrations.stream()
                .map(Registration::getCompetitionId)
                .collect(Collectors.toList());

        // 查询竞赛信息
        List<Competition> competitions = competitionMapper.selectBatchIds(competitionIds);

        log.info("查询用户已报名竞赛: userId={}, count={}", userId, competitions.size());
        return competitions;
    }
}
