package com.reddiax.loghealer.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.Instant;
import java.util.Map;

@Document(indexName = "loghealer-logs-#{T(java.time.LocalDate).now().format(T(java.time.format.DateTimeFormatter).ofPattern('yyyy-MM'))}", createIndex = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogEntryDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String projectId;

    @Field(type = FieldType.Keyword)
    private String tenantId;

    @Field(type = FieldType.Keyword)
    private String level;

    @Field(type = FieldType.Keyword)
    private String logger;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String message;

    @Field(type = FieldType.Text)
    private String stackTrace;

    @Field(type = FieldType.Keyword)
    private String exceptionClass;

    @Field(type = FieldType.Keyword)
    private String fingerprint;

    @Field(type = FieldType.Keyword)
    private String threadName;

    @Field(type = FieldType.Flattened)
    private Map<String, Object> metadata;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant timestamp;

    @Field(type = FieldType.Keyword)
    private String traceId;

    @Field(type = FieldType.Keyword)
    private String spanId;

    @Field(type = FieldType.Keyword)
    private String hostName;

    @Field(type = FieldType.Keyword)
    private String environment;
}
