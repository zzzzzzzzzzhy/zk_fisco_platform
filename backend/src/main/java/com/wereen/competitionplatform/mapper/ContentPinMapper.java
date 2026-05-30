package com.wereen.competitionplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wereen.competitionplatform.model.entity.ContentPin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 内容置顶记录 Mapper
 */
@Mapper
public interface ContentPinMapper extends BaseMapper<ContentPin> {

    /**
     * 查询当前生效的置顶记录
     */
    @Select("SELECT cp.*, u.username as userName " +
            "FROM content_pins cp " +
            "LEFT JOIN users u ON cp.user_id = u.id " +
            "WHERE cp.status = 1 AND cp.end_time > NOW() " +
            "ORDER BY cp.create_time DESC")
    List<ContentPin> selectActivePins();

    /**
     * 根据内容查询置顶记录
     */
    @Select("SELECT cp.*, u.username as userName " +
            "FROM content_pins cp " +
            "LEFT JOIN users u ON cp.user_id = u.id " +
            "WHERE cp.content_type = #{contentType} AND cp.content_id = #{contentId} " +
            "ORDER BY cp.create_time DESC")
    List<ContentPin> selectByContent(@Param("contentType") String contentType, @Param("contentId") Long contentId);

    /**
     * 查询用户购买的置顶记录
     */
    @Select("SELECT cp.*, " +
            "CASE " +
            "  WHEN cp.content_type = 'POST' THEN fp.title " +
            "  WHEN cp.content_type = 'CONTENT_SHARE' THEN cs.title " +
            "  ELSE 'Unknown' " +
            "END as contentTitle " +
            "FROM content_pins cp " +
            "LEFT JOIN forum_posts fp ON cp.content_type = 'POST' AND cp.content_id = fp.id " +
            "LEFT JOIN content_shares cs ON cp.content_type = 'CONTENT_SHARE' AND cp.content_id = cs.id " +
            "WHERE cp.user_id = #{userId} " +
            "ORDER BY cp.create_time DESC")
    List<ContentPin> selectByUserId(@Param("userId") Long userId);

    /**
     * 检查内容是否已置顶
     */
    @Select("SELECT COUNT(*) > 0 " +
            "FROM content_pins " +
            "WHERE content_type = #{contentType} AND content_id = #{contentId} " +
            "AND status = 1 AND end_time > NOW()")
    boolean isContentPinned(@Param("contentType") String contentType, @Param("contentId") Long contentId);

    /**
     * 更新过期的置顶记录
     */
    @Update("UPDATE content_pins SET status = 2 WHERE end_time <= NOW() AND status = 1")
    int updateExpiredPins();

    /**
     * 批量查询内容的有效置顶状态
     */
    @Select({
        "<script>",
        "SELECT content_id, end_time",
        "FROM content_pins",
        "WHERE content_type = #{contentType}",
        "AND status = 1",
        "AND end_time > NOW()",
        "AND content_id IN",
        "<foreach collection='contentIds' item='id' open='(' separator=',' close=')'>",
        "#{id}",
        "</foreach>",
        "</script>"
    })
    List<ContentPin> selectActivePinsByContentIds(
            @Param("contentType") String contentType,
            @Param("contentIds") List<Long> contentIds);
}
