package com.wereen.competitionplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wereen.competitionplatform.common.PageResult;
import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.mapper.ContentSharePostRelationMapper;
import com.wereen.competitionplatform.mapper.ForumPostMapper;
import com.wereen.competitionplatform.model.dto.forum.ForumPostRequest;
import com.wereen.competitionplatform.model.entity.ContentSharePostRelation;
import com.wereen.competitionplatform.model.entity.ForumPost;
import com.wereen.competitionplatform.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 社区帖子服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForumPostService {

    private final ForumPostMapper forumPostMapper;
    private final UserService userService;
    private final ContentSharePostRelationMapper contentSharePostRelationMapper;
    private final ForumTokenService forumTokenService;

    /**
     * 创建帖子
     */
    @Transactional(rollbackFor = Exception.class)
    public ForumPost createPost(ForumPostRequest request) {
        if (request.getAuthorId() == null) {
            throw new BusinessException("作者ID不能为空");
        }
        if (!StringUtils.hasText(request.getTitle())) {
            throw new BusinessException("帖子标题不能为空");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new BusinessException("帖子内容不能为空");
        }

        ForumPost post = new ForumPost();
        post.setAuthorId(request.getAuthorId());
        post.setCompetitionId(request.getCompetitionId());
        post.setTitle(request.getTitle().trim());
        post.setContent(request.getContent());
        post.setCategory(request.getCategory());
        post.setTags(request.getTags());
        post.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        post.setPinned(Boolean.FALSE);
        post.setViewCount(0);
        post.setReplyCount(0);
        post.setLikeCount(0);
        post.setLastReplyAt(LocalDateTime.now());

        int rows = forumPostMapper.insert(post);
        if (rows == 0) {
            throw new BusinessException("创建帖子失败");
        }
        applyAuthorName(post);
        if (request.getRelatedContentShareId() != null) {
            bindPostToContentShare(post.getId(), request.getRelatedContentShareId());
        }
        log.info("创建帖子成功: id={}, authorId={}", post.getId(), post.getAuthorId());
        
        // 异步触发发帖代币奖励（不影响帖子创建主流程）
        try {
            String postIdStr = "post_" + post.getId();
            forumTokenService.rewardPostAsync(post.getAuthorId(), postIdStr);
        } catch (Exception e) {
            log.warn("发帖代币奖励异步触发失败（不影响帖子创建）: postId={}, error={}",
                    post.getId(), e.getMessage());
        }
        
        return post;
    }

    /**
     * 分页查询帖子
     */
    public PageResult<ForumPost> getPostPage(Long current, Long size, Long competitionId, String category, String keyword) {
        Page<ForumPost> page = new Page<>(current, size);
        LambdaQueryWrapper<ForumPost> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(ForumPost::getStatus, 1);

        if (competitionId != null) {
            wrapper.eq(ForumPost::getCompetitionId, competitionId);
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(ForumPost::getCategory, category);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(q -> q.like(ForumPost::getTitle, keyword)
                    .or()
                    .like(ForumPost::getContent, keyword));
        }

        wrapper.orderByDesc(ForumPost::getPinned)
                .orderByDesc(ForumPost::getLastReplyAt)
                .orderByDesc(ForumPost::getCreatedAt);

        Page<ForumPost> resultPage = forumPostMapper.selectPage(page, wrapper);

        fillAuthorNames(resultPage.getRecords());
        // 填充关联的内容分享信息（用于前端展示与跳转）
        fillRelatedContentShares(resultPage.getRecords());

        return new PageResult<>(
                resultPage.getTotal(),
                resultPage.getCurrent(),
                resultPage.getSize(),
                resultPage.getRecords()
        );
    }

    /**
     * 帖子详情（附带浏览量累计）
     */
    @Transactional(rollbackFor = Exception.class)
    public ForumPost getPostDetail(Long id) {
        ForumPost post = findById(id);

        forumPostMapper.update(
                null,
                new LambdaUpdateWrapper<ForumPost>()
                        .eq(ForumPost::getId, id)
                        .setSql("view_count = IFNULL(view_count, 0) + 1")
        );
        post.setViewCount((post.getViewCount() == null ? 0 : post.getViewCount()) + 1);
        applyAuthorName(post);
        // 单条详情也补充关联内容分享信息
        fillRelatedContentShares(Collections.singletonList(post));
        return post;
    }

    /**
     * 置顶/取消置顶
     */
    @Transactional(rollbackFor = Exception.class)
    public void pinPost(Long id, boolean pinned) {
        findById(id);
        forumPostMapper.update(
                null,
                new LambdaUpdateWrapper<ForumPost>()
                        .eq(ForumPost::getId, id)
                        .set(ForumPost::getPinned, pinned)
        );
        log.info("更新帖子置顶状态: id={}, pinned={}", id, pinned);
    }

    /**
     * 删除帖子（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long id) {
        findById(id);
        forumPostMapper.deleteById(id);
        contentSharePostRelationMapper.delete(
                new LambdaQueryWrapper<ContentSharePostRelation>()
                        .eq(ContentSharePostRelation::getPostId, id)
        );
        log.info("删除帖子成功: id={}", id);
    }

    /**
     * 增加回复计数（评论服务调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void increaseReplyStats(Long postId, LocalDateTime replyTime) {
        int updated = forumPostMapper.update(
                null,
                new LambdaUpdateWrapper<ForumPost>()
                        .eq(ForumPost::getId, postId)
                        .setSql("reply_count = IFNULL(reply_count, 0) + 1")
                        .set(ForumPost::getLastReplyAt, replyTime)
        );
        if (updated == 0) {
            throw new BusinessException("帖子不存在或已被删除");
        }
    }

    /**
     * 内部使用：查询帖子（不增加浏览量）
     */
    public ForumPost findById(Long id) {
        ForumPost post = forumPostMapper.selectById(id);
        if (post == null) {
            throw new BusinessException("帖子不存在");
        }
        return applyAuthorName(post);
    }

    private ForumPost applyAuthorName(ForumPost post) {
        if (post == null || post.getAuthorId() == null) {
            return post;
        }
        try {
            User user = userService.getUserById(post.getAuthorId());
            post.setAuthorName(user.getUsername());
        } catch (Exception e) {
            log.warn("获取帖子作者信息失败: postId={}, authorId={}, error={}",
                    post.getId(), post.getAuthorId(), e.getMessage());
        }
        return post;
    }

    /**
     * 根据内容分享ID获取关联帖子
     */
    public List<ForumPost> getPostsByContentShareId(Long contentShareId) {
        if (contentShareId == null) {
            throw new BusinessException("内容分享ID不能为空");
        }

        List<ContentSharePostRelation> relations = contentSharePostRelationMapper.selectList(
                new LambdaQueryWrapper<ContentSharePostRelation>()
                        .eq(ContentSharePostRelation::getContentShareId, contentShareId)
                        .orderByDesc(ContentSharePostRelation::getCreatedAt)
        );
        if (CollectionUtils.isEmpty(relations)) {
            return Collections.emptyList();
        }

        List<Long> postIds = relations.stream()
                .map(ContentSharePostRelation::getPostId)
                .collect(Collectors.toList());
        List<ForumPost> posts = forumPostMapper.selectList(
                new LambdaQueryWrapper<ForumPost>()
                        .in(ForumPost::getId, postIds)
                        .eq(ForumPost::getStatus, 1)
        );
        if (CollectionUtils.isEmpty(posts)) {
            return Collections.emptyList();
        }
        fillAuthorNames(posts);
        Map<Long, ForumPost> postMap = posts.stream()
                .collect(Collectors.toMap(ForumPost::getId, Function.identity()));

        List<ForumPost> orderedPosts = new ArrayList<>();
        for (ContentSharePostRelation relation : relations) {
            ForumPost post = postMap.get(relation.getPostId());
            if (post != null) {
                orderedPosts.add(post);
            }
        }
        return orderedPosts;
    }

    private void bindPostToContentShare(Long postId, Long contentShareId) {
        if (postId == null || contentShareId == null) {
            return;
        }
        ContentSharePostRelation relation = new ContentSharePostRelation();
        relation.setPostId(postId);
        relation.setContentShareId(contentShareId);
        try {
            contentSharePostRelationMapper.insert(relation);
            log.info("建立内容分享帖子关联: shareId={}, postId={}", contentShareId, postId);
        } catch (DuplicateKeyException e) {
            log.warn("内容分享帖子关联已存在: shareId={}, postId={}", contentShareId, postId);
        }
    }

    private void fillAuthorNames(List<ForumPost> posts) {
        if (CollectionUtils.isEmpty(posts)) {
            return;
        }
        Set<Long> authorIds = posts.stream()
                .map(ForumPost::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (authorIds.isEmpty()) {
            return;
        }
        Map<Long, User> userMap = userService.getUserMapByIds(authorIds);
        posts.forEach(post -> {
            User user = userMap.get(post.getAuthorId());
            if (user != null) {
                post.setAuthorName(user.getUsername());
            }
        });
    }

    /**
     * 批量填充帖子关联的内容分享信息
     */
    private void fillRelatedContentShares(List<ForumPost> posts) {
        if (CollectionUtils.isEmpty(posts)) {
            return;
        }
        List<Long> postIds = posts.stream()
                .map(ForumPost::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (postIds.isEmpty()) {
            return;
        }

        List<ContentSharePostRelation> relations = contentSharePostRelationMapper.selectList(
                new LambdaQueryWrapper<ContentSharePostRelation>()
                        .in(ContentSharePostRelation::getPostId, postIds)
        );
        if (CollectionUtils.isEmpty(relations)) {
            return;
        }

        Map<Long, List<Long>> postIdToShareIds = relations.stream()
                .collect(Collectors.groupingBy(
                        ContentSharePostRelation::getPostId,
                        Collectors.mapping(ContentSharePostRelation::getContentShareId, Collectors.toList())
                ));

        posts.forEach(post -> {
            List<Long> shareIds = postIdToShareIds.get(post.getId());
            if (!CollectionUtils.isEmpty(shareIds)) {
                String idsStr = shareIds.stream()
                        .filter(Objects::nonNull)
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));
                post.setRelatedContentShareIds(idsStr);
                post.setRelatedContentShareCount(shareIds.size());
            }
        });
    }
}
