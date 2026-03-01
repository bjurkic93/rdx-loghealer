package com.reddiax.loghealer.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
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

    @JsonSetter("timestamp")
    public void setTimestampFromAny(Object value) {
        if (value == null) {
            this.timestamp = null;
        } else if (value instanceof Number) {
            this.timestamp = Instant.ofEpochMilli(((Number) value).longValue());
        } else if (value instanceof String) {
            String str = (String) value;
            try {
                long millis = Long.parseLong(str);
                this.timestamp = Instant.ofEpochMilli(millis);
            } catch (NumberFormatException e) {
                this.timestamp = Instant.parse(str);
            }
        } else if (value instanceof Instant) {
            this.timestamp = (Instant) value;
        }
    }
}
