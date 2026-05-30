package com.wereen.competitionplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wereen.competitionplatform.common.PageResult;
import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.mapper.ForumCommentMapper;
import com.wereen.competitionplatform.model.dto.forum.ForumCommentRequest;
import com.wereen.competitionplatform.model.entity.ForumComment;
import com.wereen.competitionplatform.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评论服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForumCommentService {

    private final ForumCommentMapper forumCommentMapper;
    private final ForumPostService forumPostService;
    private final UserService userService;

    /**
     * 创建评论
     */
    @Transactional(rollbackFor = Exception.class)
    public ForumComment createComment(Long postId, ForumCommentRequest request) {
        if (request.getAuthorId() == null) {
            throw new BusinessException("作者ID不能为空");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new BusinessException("评论内容不能为空");
        }

        // 确认帖子存在
        forumPostService.findById(postId);

        // 校验父评论
        if (request.getParentId() != null) {
            ForumComment parent = forumCommentMapper.selectById(request.getParentId());
            if (parent == null || !postId.equals(parent.getPostId())) {
                throw new BusinessException("父评论不存在或不属于当前帖子");
            }
        }

        ForumComment comment = new ForumComment();
        comment.setPostId(postId);
        comment.setParentId(request.getParentId());
        comment.setAuthorId(request.getAuthorId());
        comment.setContent(request.getContent());
        comment.setStatus(1);
        comment.setLikeCount(0);

        forumCommentMapper.insert(comment);
        forumPostService.increaseReplyStats(postId, LocalDateTime.now());
        applyAuthorName(comment);
        log.info("新评论创建成功: id={}, postId={}, authorId={}", comment.getId(), postId, comment.getAuthorId());
        
        return comment;
    }

    /**
     * 评论列表
     */
    public PageResult<ForumComment> getComments(Long postId, Long current, Long size) {
        forumPostService.findById(postId);

        Page<ForumComment> page = new Page<>(current, size);
        LambdaQueryWrapper<ForumComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ForumComment::getPostId, postId)
                .eq(ForumComment::getStatus, 1)
                .orderByAsc(ForumComment::getCreatedAt);

        Page<ForumComment> resultPage = forumCommentMapper.selectPage(page, wrapper);
        fillAuthorNames(resultPage.getRecords());
        return new PageResult<>(
                resultPage.getTotal(),
                resultPage.getCurrent(),
                resultPage.getSize(),
                resultPage.getRecords()
        );
    }

    public ForumComment getById(Long commentId) {
        return forumCommentMapper.selectById(commentId);
    }

    private void applyAuthorName(ForumComment comment) {
        if (comment == null || comment.getAuthorId() == null) {
            return;
        }
        try {
            User user = userService.getUserById(comment.getAuthorId());
            if (user != null) {
                comment.setAuthorName(user.getUsername());
            }
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * 删除评论（管理员或作者）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long postId, Long commentId) {
        ForumComment comment = forumCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("评论不存在");
        }
        if (!comment.getPostId().equals(postId)) {
            throw new BusinessException("评论不属于该帖子");
        }
        forumCommentMapper.deleteById(commentId);
        log.info("删除评论成功: commentId={}, postId={}", commentId, postId);
    }

    private void fillAuthorNames(List<ForumComment> comments) {
        if (CollectionUtils.isEmpty(comments)) {
            return;
        }
        Set<Long> ids = comments.stream()
                .map(ForumComment::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, User> userMap = userService.getUserMapByIds(ids);
        comments.forEach(comment -> {
            User user = userMap.get(comment.getAuthorId());
            if (user != null) {
                comment.setAuthorName(user.getUsername());
            }
        });
    }
}
