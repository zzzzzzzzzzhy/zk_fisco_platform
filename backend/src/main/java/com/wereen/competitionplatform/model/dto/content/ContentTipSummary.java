package com.wereen.competitionplatform.model.dto.content;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 打赏汇总数据
 */
@Data
public class ContentTipSummary {

    /**
     * 内容ID
     */
    private Long contentId;

    /**
     * 打赏总额
     */
    private BigDecimal totalAmount;
}
