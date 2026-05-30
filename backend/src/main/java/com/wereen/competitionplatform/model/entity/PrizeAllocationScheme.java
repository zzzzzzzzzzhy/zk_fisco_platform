package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 奖金分配方案实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prize_allocation_schemes")
public class PrizeAllocationScheme extends BaseEntity {

    /**
     * 奖金池ID
     */
    private Long poolId;

    /**
     * 方案名称（如：冠军奖、优秀奖）
     */
    private String schemeName;

    /**
     * 起始排名
     */
    private Integer rankStart;

    /**
     * 结束排名
     */
    private Integer rankEnd;

    /**
     * 每人奖金（分）
     */
    private Long prizeAmountPerUser;

    /**
     * 该奖项总金额（分）
     */
    private Long totalAmount;

    /**
     * 获奖人数
     */
    private Integer totalUsers;

    /**
     * 占奖金池百分比
     */
    private BigDecimal percentage;

    /**
     * 奖项说明
     */
    private String description;

    /**
     * 排序
     */
    private Integer sortOrder;
}
