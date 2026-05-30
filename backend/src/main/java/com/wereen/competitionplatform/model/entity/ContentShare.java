package com.wereen.competitionplatform.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wereen.competitionplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 内容分享记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("content_shares")
public class ContentShare extends BaseEntity {

    /**
     * 发布者用户ID
     */
    private Long userId;

    /**
     * 标题
     */
    private String title;

    /**
     * 描述
     */
    private String description;

    /**
     * 媒体类型 (IMAGE/VIDEO)
     */
    private String mediaType;

    /**
     * 媒体在 MinIO 中的对象路径
     */
    private String mediaUrl;

    /**
     * 缩略图路径（视频可选）
     */
    private String thumbnailUrl;

    /**
     * 视频时长（秒）
     */
    private Long durationSeconds;

    /**
     * 文件哈希算法
     */
    private String hashAlgorithm;

    /**
     * 文件哈希值
     */
    private String fileHash;

    /**
     * 自定义元数据（JSON字符串）
     */
    private String metadata;

    /**
     * 可见性 (1-公开 0-下线/隐藏)
     */
    private Integer visibility;

    /**
     * 是否已删除 (0-未删除 1-已删除)
     */
    private Integer deleted;

    /**
     * FISCO 上链状态 (0-未处理 1-处理中 2-完成 3-失败)
     */
    private Integer fiscoStatus;

    /**
     * Polygon 上链状态 (0-未处理 1-处理中 2-完成 3-失败)
     */
    private Integer polygonStatus;

    /**
     * FISCO 交易哈希
     */
    private String fiscoTxHash;

    /**
     * Polygon 交易哈希
     */
    private String polygonTxHash;

    /**
     * FISCO 区块高度
     */
    private Long fiscoBlockHeight;

    /**
     * Polygon 区块高度
     */
    private Long polygonBlockNumber;

    /**
     * FISCO 区块时间
     */
    private LocalDateTime fiscoBlockTime;

    /**
     * Polygon 区块时间
     */
    private LocalDateTime polygonBlockTime;

    /**
     * FISCO 上链失败原因
     */
    private String fiscoError;

    /**
     * Polygon 上链失败原因
     */
    private String polygonError;

    /**
     * 审核状态 (0-待审核 1-通过 2-拒绝)
     */
    private Integer reviewStatus;

    /**
     * 审核人用户ID
     */
    private Long reviewerId;

    /**
     * 审核时间
     */
    private LocalDateTime reviewedAt;

    /**
     * 审核备注/原因
     */
    private String reviewReason;

    // 暂时注释掉新增的字段，让系统先正常启动
    // private Long relatedPostId;
    // private Long competitionId;
    // private Integer commentCount;
    // private Integer likeCount;
    @TableField(exist = false)
    private BigDecimal totalTips;

    @TableField(exist = false)
    private Boolean pinned;

    @TableField(exist = false)
    private LocalDateTime pinEndTime;

    /**
     * 作者名称（非持久化）
     */
    @TableField(exist = false)
    private String authorName;

    /**
     * 关联帖子标题（非持久化）
     */
    @TableField(exist = false)
    private String relatedPostTitle;

    /**
     * 竞赛名称（非持久化）
     */
    @TableField(exist = false)
    private String competitionName;

    /**
     * 作者钱包地址（非持久化）
     */
    @TableField(exist = false)
    private String authorWalletAddress;

    // 暂时注释掉兼容方法，因为字段也被注释了
    // public Boolean getIsPinned() {
    //     return pinned;
    // }

    // public void setIsPinned(Boolean pinned) {
    //     this.pinned = pinned;
    // }
}
