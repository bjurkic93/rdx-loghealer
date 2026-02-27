package com.reddiax.loghealer.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.Instant;

@Document(indexName = "loghealer-exception-groups", createIndex = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExceptionGroupDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String projectId;

    @Field(type = FieldType.Keyword)
    private String tenantId;

    @Field(type = FieldType.Keyword)
    private String fingerprint;

    @Field(type = FieldType.Keyword)
    private String exceptionClass;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String message;

    @Field(type = FieldType.Text)
    private String sampleStackTrace;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant firstSeen;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant lastSeen;

    @Field(type = FieldType.Long)
    private Long count;

    @Field(type = FieldType.Keyword)
    private ExceptionStatus status;

    @Field(type = FieldType.Keyword)
    private String lastAnalysisId;

    @Field(type = FieldType.Keyword)
    private String environment;

    public enum ExceptionStatus {
        NEW,
        ACKNOWLEDGED,
        ANALYZING,
        FIX_AVAILABLE,
        RESOLVED,
        IGNORED
    }
}
