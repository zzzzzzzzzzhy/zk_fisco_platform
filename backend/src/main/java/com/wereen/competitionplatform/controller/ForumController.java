package com.wereen.competitionplatform.controller;

import com.wereen.competitionplatform.annotation.RequireRole;
import com.wereen.competitionplatform.common.PageResult;
import com.wereen.competitionplatform.common.Result;
import com.wereen.competitionplatform.common.UserRole;
import com.wereen.competitionplatform.model.dto.forum.ForumCommentRequest;
import com.wereen.competitionplatform.model.dto.forum.ForumPostRequest;
import com.wereen.competitionplatform.model.entity.ForumComment;
import com.wereen.competitionplatform.model.entity.ForumPost;
import com.wereen.competitionplatform.service.ForumCommentService;
import com.wereen.competitionplatform.service.ForumPostService;
import com.wereen.competitionplatform.service.RewardEventService;
import com.wereen.competitionplatform.service.WeeBalanceService;
import com.wereen.competitionplatform.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * 社区论坛接口
 */
@RestController
@RequestMapping("/forum")
@RequiredArgsConstructor
public class ForumController {

    private final ForumPostService forumPostService;
    private final ForumCommentService forumCommentService;
    private final RewardEventService rewardEventService;
    private final WeeBalanceService weeBalanceService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    /**
     * 帖子列表
     */
    @GetMapping("/posts")
    public Result<PageResult<ForumPost>> getPosts(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) Long competitionId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {

        PageResult<ForumPost> page = forumPostService.getPostPage(current, size, competitionId, category, keyword);
        return Result.success(page);
    }

    /**
     * 创建帖子
     */
    @PostMapping("/posts")
    public Result<ForumPost> createPost(@RequestBody ForumPostRequest request,
                                         HttpServletRequest httpRequest) {
        if (request.getAuthorId() == null) {
            String bearer = httpRequest.getHeader("Authorization");
            if (bearer != null && bearer.startsWith("Bearer ")) {
                request.setAuthorId(jwtUtil.getUserIdFromToken(bearer.substring(7)));
            }
        }
        ForumPost post = forumPostService.createPost(request);
        if (post.getAuthorId() != null) {
            weeBalanceService.addReward(post.getAuthorId(), WeeBalanceService.REWARD_POST, "发帖奖励");
        }
        return Result.success(post);
    }

    /**
     * 帖子详情
     */
    @GetMapping("/posts/{id}")
    public Result<ForumPost> getPostDetail(@PathVariable Long id) {
        ForumPost post = forumPostService.getPostDetail(id);
        return Result.success(post);
    }

    /**
     * 删除帖子（管理员）
     */
    @DeleteMapping("/posts/{id}")
    @RequireRole(UserRole.ADMIN)
    public Result<Void> deletePost(@PathVariable Long id) {
        forumPostService.deletePost(id);
        return Result.success();
    }

    /**
     * 置顶帖子（管理员）
     */
    @PostMapping("/posts/{id}/pin")
    @RequireRole(UserRole.ADMIN)
    public Result<Void> pinPost(@PathVariable Long id,
                                @RequestParam(defaultValue = "true") boolean pinned) {
        forumPostService.pinPost(id, pinned);
        return Result.success();
    }

    /**
     * 评论列表
     */
    @GetMapping("/posts/{id}/comments")
    public Result<PageResult<ForumComment>> getComments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size) {

        PageResult<ForumComment> page = forumCommentService.getComments(id, current, size);
        return Result.success(page);
    }

    /**
     * 创建评论
     */
    @PostMapping("/posts/{id}/comments")
    public Result<ForumComment> createComment(@PathVariable Long id,
                                               @RequestBody ForumCommentRequest request,
                                               HttpServletRequest httpRequest) {
        if (request.getAuthorId() == null) {
            String bearer = httpRequest.getHeader("Authorization");
            if (bearer != null && bearer.startsWith("Bearer ")) {
                request.setAuthorId(jwtUtil.getUserIdFromToken(bearer.substring(7)));
            }
        }
        ForumComment comment = forumCommentService.createComment(id, request);
        if (comment.getAuthorId() != null) {
            weeBalanceService.addReward(comment.getAuthorId(), WeeBalanceService.REWARD_COMMENT, "评论奖励");
        }
        return Result.success(comment);
    }

    /**
     * 根据内容分享ID获取关联帖子
     */
    @GetMapping("/posts/content-share/{contentShareId}")
    public Result<List<ForumPost>> getPostsByContentShare(@PathVariable Long contentShareId) {
        System.out.println("ForumController.getPostsByContentShare被调用，contentShareId: " + contentShareId);
        try {
            List<ForumPost> posts = forumPostService.getPostsByContentShareId(contentShareId);
            return Result.success(posts);
        } catch (Exception e) {
            System.out.println("异常: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * 创建与内容分享关联的帖子
     */
    @PostMapping("/posts/content-share")
    public Result<ForumPost> createContentSharePost(@RequestBody ForumPostRequest request) {
        ForumPost post = forumPostService.createPost(request);
        return Result.success(post);
    }

    /**
     * 删除评论（管理员或作者）
     */
    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public Result<Void> deleteComment(@PathVariable Long postId, @PathVariable Long commentId) {
        forumCommentService.deleteComment(postId, commentId);
        return Result.success();
    }

    /**
     * 评论用户确认签名（不直接上链）
     */
    @PostMapping("/posts/{postId}/comments/{commentId}/consent")
    public Result<Map<String, Object>> submitCommentConsent(
        @PathVariable Long postId,
        @PathVariable Long commentId,
        @RequestBody Map<String, String> request
    ) {
        ForumComment comment = forumCommentService.getById(commentId);
        if (comment == null || !postId.equals(comment.getPostId())) {
            return Result.error("评论不存在");
        }
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(comment.getAuthorId())) {
            return Result.error("无权限提交签名");
        }
        String signature = request.get("signature");
        String userAddress = request.get("userAddress");
        if (!org.springframework.util.StringUtils.hasText(signature)) {
            return Result.error("签名不能为空");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("commentId", commentId);
        payload.put("postId", postId);
        payload.put("userAddress", userAddress);

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            payloadJson = "{}";
        }

        rewardEventService.createEvent(
            currentUserId,
            "COMMENT",
            "comment_" + commentId,
            signature,
            payloadJson
        );

        Map<String, Object> result = new HashMap<>();
        result.put("accepted", true);
        return Result.success(result);
    }

    private Long getCurrentUserId() {
        try {
            jakarta.servlet.http.HttpServletRequest request =
                ((org.springframework.web.context.request.ServletRequestAttributes)
                 org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes())
                .getRequest();
            String bearer = request.getHeader("Authorization");
            if (bearer != null && bearer.startsWith("Bearer ")) {
                return jwtUtil.getUserIdFromToken(bearer.substring(7));
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
