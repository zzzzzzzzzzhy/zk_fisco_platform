package com.wereen.competitionplatform.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.StringJoiner;

@Slf4j
@Service
public class RollupTaskLogService {

    private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Value("${reward.rollup.log-file:docs/rollup-execution.log}")
    private String logFile;

    public void log(String action, Map<String, String> fields) {
        String line = formatLine(action, fields);
        appendLine(line);
    }

    private String formatLine(String action, Map<String, String> fields) {
        StringJoiner joiner = new StringJoiner(" ");
        joiner.add("ts=" + LocalDateTime.now().format(TS_FORMATTER));
        joiner.add("action=" + safe(action));
        if (fields != null) {
            fields.forEach((k, v) -> joiner.add(safe(k) + "=" + safe(v)));
        }
        return joiner.toString();
    }

    private void appendLine(String line) {
        if (!StringUtils.hasText(logFile)) {
            return;
        }
        try {
            Path path = Path.of(logFile);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                path,
                line + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (Exception e) {
            log.warn("Rollup log write failed: {}", e.getMessage());
        }
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", "_");
    }
}
