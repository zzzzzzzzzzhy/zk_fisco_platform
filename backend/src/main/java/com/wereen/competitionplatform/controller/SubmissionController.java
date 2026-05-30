package com.wereen.competitionplatform.controller;

import com.wereen.competitionplatform.common.PageResult;
import com.wereen.competitionplatform.common.Result;
import com.wereen.competitionplatform.model.entity.Submission;
import com.wereen.competitionplatform.service.MinioService;
import com.wereen.competitionplatform.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 提交控制器
 */
@RestController
@RequestMapping("/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;
    private final MinioService minioService;

    /**
     * 获取预签名上传URL
     */
    @GetMapping("/presigned-url")
    public Result<Map<String, String>> getPresignedUploadUrl(
            @RequestParam Long competitionId,
            @RequestParam String fileName) {

        String objectName = String.format("submissions/%d/%s", competitionId, fileName);
        String uploadUrl = minioService.getPresignedUploadUrl("submissions", objectName, 60);

        Map<String, String> result = new HashMap<>();
        result.put("uploadUrl", uploadUrl);
        result.put("objectName", objectName);

        return Result.success(result);
    }

    /**
     * 创建提交记录
     */
    @PostMapping
    public Result<Submission> createSubmission(@RequestBody Submission submission) {
        Submission created = submissionService.createSubmission(submission);
        return Result.success(created);
    }

    /**
     * 获取提交详情
     */
    @GetMapping("/{id}")
    public Result<Submission> getSubmissionById(@PathVariable Long id) {
        Submission submission = submissionService.getSubmissionById(id);
        return Result.success(submission);
    }

    /**
     * 获取用户提交记录
     */
    @GetMapping("/my")
    public Result<PageResult<Submission>> getUserSubmissions(
            @RequestParam Long userId,
            @RequestParam(required = false) Long competitionId,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size) {

        PageResult<Submission> page = submissionService.getUserSubmissions(userId, competitionId, current, size);
        return Result.success(page);
    }
}
