package com.wereen.competitionplatform.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解
 * 基于 Redis + 令牌桶算法实现接口限流
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流key前缀
     */
    String key() default "rate_limit";

    /**
     * 时间窗口（秒）
     */
    int time() default 60;

    /**
     * 时间窗口内最大请求次数
     */
    int count() default 10;

    /**
     * 限流类型（IP、USER、GLOBAL）
     */
    LimitType limitType() default LimitType.IP;

    /**
     * 限流类型枚举
     */
    enum LimitType {
        /**
         * 根据IP限流
         */
        IP,
        /**
         * 根据用户限流
         */
        USER,
        /**
         * 全局限流
         */
        GLOBAL
    }
}
