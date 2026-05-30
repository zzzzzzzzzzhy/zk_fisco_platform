package com.wereen.competitionplatform.controller;

import com.wereen.competitionplatform.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试控制器 - 用于调试
 */
@Slf4j
@RestController
@RequestMapping("/test")
@Tag(name = "测试接口", description = "用于调试的临时接口")
public class TestController {

    @GetMapping("/hello")
    @Operation(summary = "Hello World", description = "简单的测试接口")
    public Result<String> hello() {
        log.info("Hello World 被调用");
        return Result.success("Hello World! 测试正常");
    }
}