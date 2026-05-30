package com.wereen.competitionplatform.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.data.redis.RedisSystemException;
import io.lettuce.core.RedisBusyException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Redis Streams 消费者配置
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamConsumerConfig {

    private final RedisConnectionFactory redisConnectionFactory;

    @Value("${redis-streams.consumer-group:competition-platform-group}")
    private String consumerGroup;

    @Value("${redis-streams.consumer-name:consumer-1}")
    private String consumerName;

    /**
     * 创建 StreamMessageListenerContainer
     */
    @Bean
    @SuppressWarnings("unchecked")
    public StreamMessageListenerContainer<String, ObjectRecord<String, Map<String, String>>> streamMessageListenerContainer() {
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, ObjectRecord<String, Map<String, String>>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .builder()
                        .pollTimeout(Duration.ofSeconds(1))
                        .targetType((Class<Map<String, String>>) (Class<?>) Map.class)
                        .build();

        StreamMessageListenerContainer<String, ObjectRecord<String, Map<String, String>>> container =
                StreamMessageListenerContainer.create(redisConnectionFactory, options);

        container.start();
        log.info("Redis Streams 消息监听容器已启动");

        return container;
    }

    /**
     * 注册上传预检消费者
     */
    @Bean
    public Subscription uploadPrecheckSubscription(
            StreamMessageListenerContainer<String, ObjectRecord<String, Map<String, String>>> container,
            UploadPrecheckConsumer uploadPrecheckConsumer) {

        return registerConsumer(container, "tasks:upload_precheck", uploadPrecheckConsumer);
    }

    /**
     * 注册链上存证消费者
     */
    @Bean
    public Subscription chainEvidenceSubscription(
            StreamMessageListenerContainer<String, ObjectRecord<String, Map<String, String>>> container,
            ChainEvidenceConsumer chainEvidenceConsumer) {

        return registerConsumer(container, "tasks:chain_evidence", chainEvidenceConsumer);
    }

    /**
     * 注册评测消费者
     */
    @Bean
    public Subscription evaluateSubscription(
            StreamMessageListenerContainer<String, ObjectRecord<String, Map<String, String>>> container,
            EvaluateConsumer evaluateConsumer) {

        return registerConsumer(container, "tasks:evaluate", evaluateConsumer);
    }

    /**
     * 注册奖金入账消费者
     */
    @Bean
    public Subscription prizeBookkeepingSubscription(
            StreamMessageListenerContainer<String, ObjectRecord<String, Map<String, String>>> container,
            PrizeBookkeepingConsumer prizeBookkeepingConsumer) {

        return registerConsumer(container, "tasks:prize_bookkeeping", prizeBookkeepingConsumer);
    }

    /**
     * 通用消费者注册方法
     */
    private Subscription registerConsumer(
            StreamMessageListenerContainer<String, ObjectRecord<String, Map<String, String>>> container,
            String streamKey,
            StreamListener<String, ObjectRecord<String, Map<String, String>>> listener) {

        ensureConsumerGroup(streamKey);
        Subscription subscription = container.receive(
                Consumer.from(consumerGroup, consumerName),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                listener
        );

        log.info("注册消费者成功: stream={}, group={}, consumer={}",
                streamKey, consumerGroup, consumerName);

        return subscription;
    }

    private void ensureConsumerGroup(String streamKey) {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            try {
                connection.xGroupCreate(
                    streamKey.getBytes(StandardCharsets.UTF_8),
                    consumerGroup,
                    ReadOffset.latest(),
                    true
                );
                log.info("创建 Redis Stream group: stream={}, group={}", streamKey, consumerGroup);
            } catch (RedisSystemException e) {
                Throwable cause = e.getCause();
                String message = e.getMessage();
                String causeMessage = cause != null ? cause.getMessage() : null;
                if (cause instanceof RedisBusyException
                    || (message != null && message.contains("BUSYGROUP"))
                    || (causeMessage != null && causeMessage.contains("BUSYGROUP"))) {
                    return;
                }
                log.warn("创建 Redis Stream group 失败: stream={}, group={}, err={}",
                    streamKey, consumerGroup, e.getMessage());
            }
        }
    }
}
