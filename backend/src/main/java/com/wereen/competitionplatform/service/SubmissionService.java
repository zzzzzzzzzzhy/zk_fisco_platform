package com.wereen.competitionplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wereen.competitionplatform.common.PageResult;
import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.mapper.SubmissionMapper;
import com.wereen.competitionplatform.model.entity.Competition;
import com.wereen.competitionplatform.model.entity.Submission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/**
 * 提交服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionMapper submissionMapper;
    private final CompetitionService competitionService;
    private final BlockchainEvidenceService blockchainEvidenceService;
    private final RedisStreamService redisStreamService;

    /**
     * 创建提交记录
     */
    @Transactional(rollbackFor = Exception.class)
    public Submission createSubmission(Submission submission) {
        // 验证竞赛状态
        Competition competition = competitionService.getCompetitionById(submission.getCompetitionId());

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(competition.getSubmissionStartTime())) {
            throw new BusinessException("提交尚未开始");
        }
        if (now.isAfter(competition.getSubmissionEndTime())) {
            throw new BusinessException("提交已截止");
        }

        // 设置初始状态
        submission.setPrecheckStatus(0); // 待检查
        submission.setChainStatus(0); // 未上链

        int rows = submissionMapper.insert(submission);
        if (rows == 0) {
            throw new BusinessException("创建提交记录失败");
        }

        // 推送文件预检任务，异步执行安全校验
        redisStreamService.sendUploadPrecheckTask(submission.getId(), submission.getFilePath());

        log.info("创建提交记录成功: id={}, userId={}, competitionId={}",
                submission.getId(), submission.getUserId(), submission.getCompetitionId());

        // 在事务提交后异步上链 - 确保记录已经持久化到数据库
        Long submissionId = submission.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("事务已提交，开始触发异步上链: submissionId={}", submissionId);
                blockchainEvidenceService.uploadSubmissionToBlockchainAsync(submissionId);
            }
        });

        return submission;
    }

    /**
     * 根据ID查询提交记录
     */
    public Submission getSubmissionById(Long id) {
        Submission submission = submissionMapper.selectById(id);
        if (submission == null) {
            throw new BusinessException("提交记录不存在");
        }
        return submission;
    }

    /**
     * 查询用户在指定竞赛的提交记录
     */
    public PageResult<Submission> getUserSubmissions(Long userId, Long competitionId, Long current, Long size) {
        Page<Submission> page = new Page<>(current, size);
        LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(Submission::getUserId, userId);
        if (competitionId != null) {
            wrapper.eq(Submission::getCompetitionId, competitionId);
        }
        wrapper.orderByDesc(Submission::getCreatedAt);

        Page<Submission> resultPage = submissionMapper.selectPage(page, wrapper);

        return new PageResult<>(
                resultPage.getTotal(),
                resultPage.getCurrent(),
                resultPage.getSize(),
                resultPage.getRecords()
        );
    }

    /**
     * 更新预检状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePrecheckStatus(Long submissionId, Integer status, String reason) {
        Submission submission = getSubmissionById(submissionId);
        submission.setPrecheckStatus(status);
        submission.setPrecheckReason(reason);

        submissionMapper.updateById(submission);
        log.info("更新提交预检状态: id={}, status={}", submissionId, status);
    }

    /**
     * 更新文件哈希值
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateFileHash(Long submissionId, String fileHash, String hashAlgorithm) {
        Submission submission = getSubmissionById(submissionId);
        submission.setFileHash(fileHash);
        submission.setHashAlgorithm(hashAlgorithm);

        submissionMapper.updateById(submission);
        log.info("更新提交文件哈希: id={}, algorithm={}, hash={}", submissionId, hashAlgorithm, fileHash);
    }

}
