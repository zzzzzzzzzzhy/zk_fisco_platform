package com.wereen.competitionplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wereen.competitionplatform.model.dto.content.ContentTipSummary;
import com.wereen.competitionplatform.model.entity.ContentTip;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 内容打赏记录 Mapper
 */
@Mapper
public interface ContentTipMapper extends BaseMapper<ContentTip> {

    /**
     * 根据内容创作者查询打赏记录
     */
    @Select("SELECT ct.*, u.username as tipperName " +
            "FROM content_tips ct " +
            "LEFT JOIN users u ON ct.tipper_id = u.id " +
            "WHERE ct.creator_id = #{creatorId} " +
            "ORDER BY ct.create_time DESC " +
            "LIMIT #{limit}")
    List<ContentTip> selectByCreatorId(@Param("creatorId") Long creatorId, @Param("limit") int limit);

    /**
     * 根据内容查询打赏记录
     */
    @Select("SELECT ct.*, u1.username as tipperName, u2.username as creatorName " +
            "FROM content_tips ct " +
            "LEFT JOIN users u1 ON ct.tipper_id = u1.id " +
            "LEFT JOIN users u2 ON ct.creator_id = u2.id " +
            "WHERE ct.content_type = #{contentType} AND ct.content_id = #{contentId} " +
            "ORDER BY ct.create_time DESC")
    List<ContentTip> selectByContent(@Param("contentType") String contentType, @Param("contentId") Long contentId);

    /**
     * 查询创作者收到的总打赏金额
     */
    @Select("SELECT COALESCE(SUM(amount), 0) " +
            "FROM content_tips " +
            "WHERE creator_id = #{creatorId} AND status = 1")
    BigDecimal selectTotalTipsByCreator(@Param("creatorId") Long creatorId);

    /**
     * 查询内容的总打赏金额
     */
    @Select("SELECT COALESCE(SUM(amount), 0) " +
            "FROM content_tips " +
            "WHERE content_type = #{contentType} AND content_id = #{contentId} AND status = 1")
    BigDecimal selectTotalTipsByContent(@Param("contentType") String contentType, @Param("contentId") Long contentId);

    /**
     * 查询用户的打赏统计
     */
    @Select("SELECT COUNT(*) as tipCount, COALESCE(SUM(amount), 0) as totalAmount " +
            "FROM content_tips " +
            "WHERE tipper_id = #{tipperId} AND status = 1")
    ContentTip selectUserTipStats(@Param("tipperId") Long tipperId);

    /**
     * 查询指定内容集合的总打赏金额
     */
    @Select({
        "<script>",
        "SELECT content_id as contentId, COALESCE(SUM(amount), 0) as totalAmount",
        "FROM content_tips",
        "WHERE status = 1",
        "AND content_type = #{contentType}",
        "AND content_id IN",
        "<foreach collection='contentIds' item='id' open='(' separator=',' close=')'>",
        "#{id}",
        "</foreach>",
        "GROUP BY content_id",
        "</script>"
    })
    List<ContentTipSummary> selectTotalTipsByContentIds(
            @Param("contentType") String contentType,
            @Param("contentIds") List<Long> contentIds);
}
