package com.wereen.competitionplatform.service;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 内容奖励事件
 */
@Getter
public class ContentRewardEvent extends ApplicationEvent {
    private final Long publisherId;
    private final Long shareId;
    private final String mediaType;

    public ContentRewardEvent(Object source, Long publisherId, Long shareId, String mediaType) {
        super(source);
        this.publisherId = publisherId;
        this.shareId = shareId;
        this.mediaType = mediaType;
    }
}

