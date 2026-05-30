package com.wereen.competitionplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wereen.competitionplatform.common.PageResult;
import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.mapper.CompetitionMapper;
import com.wereen.competitionplatform.model.entity.Competition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 竞赛服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompetitionService {

    private final CompetitionMapper competitionMapper;
    private final ObjectMapper objectMapper;

    /**
     * 创建竞赛
     */
    @Transactional(rollbackFor = Exception.class)
    public Competition createCompetition(Competition competition) {
        competition.setStatus(0); // 草稿状态

        // 计算总奖金
        Long totalPrize = calculateTotalPrize(competition.getPrizeConfig());
        competition.setTotalPrize(totalPrize);

        int rows = competitionMapper.insert(competition);
        if (rows == 0) {
            throw new BusinessException("创建竞赛失败");
        }
        log.info("创建竞赛成功: id={}, totalPrize={}", competition.getId(), totalPrize);
        return competition;
    }

    /**
     * 更新竞赛
     */
    @Transactional(rollbackFor = Exception.class)
    public Competition updateCompetition(Competition competition) {
        if (competition.getId() == null) {
            throw new BusinessException("竞赛ID不能为空");
        }

        Competition existing = competitionMapper.selectById(competition.getId());
        if (existing == null) {
            throw new BusinessException("竞赛不存在");
        }

        // 计算总奖金
        Long totalPrize = calculateTotalPrize(competition.getPrizeConfig());
        competition.setTotalPrize(totalPrize);

        int rows = competitionMapper.updateById(competition);
        if (rows == 0) {
            throw new BusinessException("更新竞赛失败");
        }
        log.info("更新竞赛成功: id={}, totalPrize={}", competition.getId(), totalPrize);
        return competition;
    }

    /**
     * 根据ID查询竞赛
     */
    public Competition getCompetitionById(Long id) {
        Competition competition = competitionMapper.selectById(id);
        if (competition == null) {
            throw new BusinessException("竞赛不存在");
        }
        return competition;
    }

    /**
     * 分页查询竞赛列表
     */
    public PageResult<Competition> getCompetitionPage(Long current, Long size, Integer status) {
        Page<Competition> page = new Page<>(current, size);
        LambdaQueryWrapper<Competition> wrapper = new LambdaQueryWrapper<>();

        if (status != null) {
            wrapper.eq(Competition::getStatus, status);
        }

        wrapper.orderByDesc(Competition::getCreatedAt);

        Page<Competition> resultPage = competitionMapper.selectPage(page, wrapper);

        return new PageResult<>(
                resultPage.getTotal(),
                resultPage.getCurrent(),
                resultPage.getSize(),
                resultPage.getRecords()
        );
    }

    /**
     * 查询所有进行中的竞赛
     */
    public List<Competition> getOngoingCompetitions() {
        LambdaQueryWrapper<Competition> wrapper = new LambdaQueryWrapper<>();
        LocalDateTime now = LocalDateTime.now();

        wrapper.eq(Competition::getStatus, 2) // 进行中
                .le(Competition::getSubmissionStartTime, now)
                .ge(Competition::getSubmissionEndTime, now)
                .orderByDesc(Competition::getCreatedAt);

        return competitionMapper.selectList(wrapper);
    }

    /**
     * 发布竞赛（从草稿到报名中）
     */
    @Transactional(rollbackFor = Exception.class)
    public void publishCompetition(Long competitionId) {
        Competition competition = getCompetitionById(competitionId);

        if (competition.getStatus() != 0) {
            throw new BusinessException("只有草稿状态的竞赛才能发布");
        }

        competition.setStatus(1); // 报名中
        competitionMapper.updateById(competition);
        log.info("发布竞赛成功: id={}", competitionId);
    }

    /**
     * 删除竞赛（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteCompetition(Long competitionId) {
        Competition competition = getCompetitionById(competitionId);

        if (competition.getStatus() == 2) {
            throw new BusinessException("进行中的竞赛不能删除");
        }

        competitionMapper.deleteById(competitionId);
        log.info("删除竞赛成功: id={}", competitionId);
    }

    /**
     * 计算总奖金
     * 根据prizeConfig JSON字符串计算所有奖项金额的总和
     */
    private Long calculateTotalPrize(String prizeConfig) {
        if (prizeConfig == null || prizeConfig.trim().isEmpty()) {
            return 0L;
        }

        try {
            JsonNode prizeArray = objectMapper.readTree(prizeConfig);
            long total = 0L;

            if (prizeArray.isArray()) {
                for (JsonNode prizeNode : prizeArray) {
                    if (prizeNode.has("amount")) {
                        total += prizeNode.get("amount").asLong();
                    }
                }
            }

            return total;
        } catch (Exception e) {
            log.error("计算总奖金失败: prizeConfig={}", prizeConfig, e);
            return 0L;
        }
    }
}
