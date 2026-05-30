package com.wereen.competitionplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wereen.competitionplatform.mapper.RewardEventMapper;
import com.wereen.competitionplatform.model.entity.RewardEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RewardEventService {

    private final RewardEventMapper rewardEventMapper;

    public RewardEvent createEvent(Long userId, String eventType, String bizId, String signature, String payload) {
        if (userId == null || !StringUtils.hasText(eventType) || !StringUtils.hasText(bizId)) {
            throw new IllegalArgumentException("reward event 参数缺失");
        }
        RewardEvent existing = rewardEventMapper.selectOne(
            new LambdaQueryWrapper<RewardEvent>()
                .eq(RewardEvent::getUserId, userId)
                .eq(RewardEvent::getEventType, eventType)
                .eq(RewardEvent::getBizId, bizId)
                .eq(RewardEvent::getDeleted, 0)
        );
        if (existing != null) {
            return existing;
        }
        RewardEvent event = new RewardEvent();
        event.setUserId(userId);
        event.setEventType(eventType);
        event.setBizId(bizId);
        event.setSignature(signature);
        event.setPayload(payload);
        event.setStatus(0);
        rewardEventMapper.insert(event);
        return event;
    }

    public List<RewardEvent> listPendingEvents(String eventType, LocalDateTime windowStart, LocalDateTime windowEnd) {
        return rewardEventMapper.selectList(
            new LambdaQueryWrapper<RewardEvent>()
                .eq(RewardEvent::getEventType, eventType)
                .eq(RewardEvent::getStatus, 0)
                .eq(RewardEvent::getDeleted, 0)
                .ge(RewardEvent::getCreatedAt, windowStart)
                .lt(RewardEvent::getCreatedAt, windowEnd)
                .orderByAsc(RewardEvent::getId)
        );
    }

    public List<RewardEvent> listEventsByBatch(String eventType, Long batchId, int status) {
        return rewardEventMapper.selectList(
            new LambdaQueryWrapper<RewardEvent>()
                .eq(RewardEvent::getEventType, eventType)
                .eq(RewardEvent::getBatchId, batchId)
                .eq(RewardEvent::getStatus, status)
                .eq(RewardEvent::getDeleted, 0)
                .orderByAsc(RewardEvent::getId)
        );
    }

    public List<RewardEvent> listRecentCheckins(Long userId, int limit) {
        return rewardEventMapper.selectList(
            new LambdaQueryWrapper<RewardEvent>()
                .eq(RewardEvent::getUserId, userId)
                .eq(RewardEvent::getEventType, "CHECKIN")
                .ge(RewardEvent::getStatus, 1)
                .eq(RewardEvent::getDeleted, 0)
                .orderByDesc(RewardEvent::getCreatedAt)
                .last("limit " + Math.max(1, limit))
        );
    }

    public void markBatched(List<Long> ids, Long batchId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        RewardEvent update = new RewardEvent();
        update.setBatchId(batchId);
        update.setStatus(1);
        rewardEventMapper.update(
            update,
            new LambdaQueryWrapper<RewardEvent>()
                .in(RewardEvent::getId, ids)
        );
    }

    public void markRewarded(List<Long> ids, String txHash) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        RewardEvent update = new RewardEvent();
        update.setStatus(2);
        update.setTxHash(txHash);
        rewardEventMapper.update(
            update,
            new LambdaQueryWrapper<RewardEvent>()
                .in(RewardEvent::getId, ids)
        );
    }

    public boolean existsEvent(Long userId, String eventType, String bizId) {
        if (userId == null || !StringUtils.hasText(eventType) || !StringUtils.hasText(bizId)) {
            return false;
        }
        Long count = rewardEventMapper.selectCount(
            new LambdaQueryWrapper<RewardEvent>()
                .eq(RewardEvent::getUserId, userId)
                .eq(RewardEvent::getEventType, eventType)
                .eq(RewardEvent::getBizId, bizId)
                .eq(RewardEvent::getDeleted, 0)
        );
        return count != null && count > 0;
    }
}
