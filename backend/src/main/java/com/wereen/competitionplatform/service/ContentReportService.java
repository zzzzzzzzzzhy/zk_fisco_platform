package com.wereen.competitionplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wereen.competitionplatform.common.PageResult;
import com.wereen.competitionplatform.mapper.ContentReportMapper;
import com.wereen.competitionplatform.model.entity.ContentReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentReportService {

    private final ContentReportMapper contentReportMapper;

    /**
     * 用户提交举报
     */
    @Transactional(rollbackFor = Exception.class)
    public void createReport(Long contentShareId, Long reporterId, String reasonCode, String reasonText) {
        ContentReport report = new ContentReport();
        report.setContentShareId(contentShareId);
        report.setReporterId(reporterId);
        report.setReasonCode(reasonCode);
        report.setReasonText(reasonText);
        report.setStatus(0);
        contentReportMapper.insert(report);
        log.info("收到内容举报: contentShareId={}, reporterId={}, reasonCode={}",
            contentShareId, reporterId, reasonCode);
    }

    /**
     * 管理员分页查看举报
     */
    public PageResult<ContentReport> listReports(Long current, Long size, Integer status) {
        Page<ContentReport> page = new Page<>(current, size);
        LambdaQueryWrapper<ContentReport> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(ContentReport::getStatus, status);
        }
        wrapper.orderByDesc(ContentReport::getCreatedAt);
        Page<ContentReport> result = contentReportMapper.selectPage(page, wrapper);
        return new PageResult<>(
            result.getTotal(),
            result.getCurrent(),
            result.getSize(),
            result.getRecords()
        );
    }

    /**
     * 管理员处理举报
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleReport(Long id, Integer status, Long handlerId, String resultNote) {
        ContentReport report = contentReportMapper.selectById(id);
        if (report == null) {
            return;
        }
        report.setStatus(status);
        report.setHandlerId(handlerId);
        report.setHandledAt(LocalDateTime.now());
        report.setResultNote(resultNote);
        contentReportMapper.updateById(report);
        log.info("举报已处理: id={}, status={}, handlerId={}", id, status, handlerId);
    }
}


