package com.reddiax.loghealer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogEntryRequest {

    @NotBlank(message = "Level is required")
    private String level;

    @NotBlank(message = "Message is required")
    private String message;

    private String logger;

    private String stackTrace;

    private String exceptionClass;

    private String threadName;

    private Map<String, Object> metadata;

    private Instant timestamp;

    private String traceId;

    private String spanId;

    private String hostName;

    private String environment;
}
