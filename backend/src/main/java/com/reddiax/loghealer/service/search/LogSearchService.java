package com.reddiax.loghealer.service.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.*;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.reddiax.loghealer.document.ExceptionGroupDocument;
import com.reddiax.loghealer.document.LogEntryDocument;
import com.reddiax.loghealer.dto.DashboardStatsResponse;
import com.reddiax.loghealer.dto.LogSearchRequest;
import com.reddiax.loghealer.dto.LogSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogSearchService {

    private final ElasticsearchClient elasticsearchClient;

    private static final String LOG_INDEX_PATTERN = "loghealer-logs-*";
    private static final String EXCEPTION_INDEX = "loghealer-exception-groups";

    public LogSearchResponse search(LogSearchRequest request) {
        try {
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();

            if (request.getQuery() != null && !request.getQuery().isBlank()) {
                boolQuery.must(m -> m.multiMatch(mm -> mm
                    .query(request.getQuery())
                    .fields("message", "stackTrace", "logger", "exceptionClass")
                    .fuzziness("AUTO")
                ));
            }

            if (request.getLevels() != null && !request.getLevels().isEmpty()) {
                boolQuery.filter(f -> f.terms(t -> t
                    .field("level")
                    .terms(tv -> tv.value(request.getLevels().stream()
                        .map(l -> co.elastic.clients.elasticsearch._types.FieldValue.of(l.toUpperCase()))
                        .toList()))
                ));
            }

            if (request.getProjectId() != null) {
                boolQuery.filter(f -> f.term(t -> t.field("projectId").value(request.getProjectId())));
            }

            if (request.getLogger() != null) {
                boolQuery.filter(f -> f.wildcard(w -> w.field("logger").value("*" + request.getLogger() + "*")));
            }

            if (request.getExceptionClass() != null) {
                boolQuery.filter(f -> f.term(t -> t.field("exceptionClass").value(request.getExceptionClass())));
            }

            if (request.getEnvironment() != null) {
                boolQuery.filter(f -> f.term(t -> t.field("environment").value(request.getEnvironment())));
            }

            if (request.getFromTimestamp() != null || request.getToTimestamp() != null) {
                final Double fromMs = request.getFromTimestamp() != null 
                    ? Double.valueOf(request.getFromTimestamp().toEpochMilli()) : null;
                final Double toMs = request.getToTimestamp() != null 
                    ? Double.valueOf(request.getToTimestamp().toEpochMilli()) : null;
                    
                boolQuery.filter(f -> f.range(r -> r.number(n -> {
                    n.field("timestamp");
                    if (fromMs != null) n.gte(fromMs);
                    if (toMs != null) n.lte(toMs);
                    return n;
                })));
            }

            SortOrder sortOrder = "asc".equalsIgnoreCase(request.getSortOrder()) 
                ? SortOrder.Asc : SortOrder.Desc;

            SearchResponse<LogEntryDocument> response = elasticsearchClient.search(s -> s
                .index(LOG_INDEX_PATTERN)
                .query(q -> q.bool(boolQuery.build()))
                .from(request.getPage() * request.getSize())
                .size(request.getSize())
                .sort(sort -> sort.field(f -> f.field(request.getSortBy()).order(sortOrder))),
                LogEntryDocument.class
            );

            List<LogEntryDocument> logs = response.hits().hits().stream()
                .map(Hit::source)
                .toList();

            long totalHits = response.hits().total() != null 
                ? response.hits().total().value() : 0;

            return LogSearchResponse.of(logs, totalHits, request.getPage(), request.getSize());

        } catch (IOException e) {
            log.error("Error searching logs", e);
            throw new RuntimeException("Failed to search logs", e);
        }
    }

    public DashboardStatsResponse getDashboardStats(String projectId, String timeRange) {
        try {
            long totalLogs = countAllLogs(projectId);
            long totalErrors = countLogsByLevel(projectId, "ERROR");
            long totalWarnings = countLogsByLevel(projectId, "WARN");

            List<DashboardStatsResponse.LogLevelCount> logsByLevel = getLogsByLevelSimple(projectId);
            List<DashboardStatsResponse.TopException> topExceptions = getTopExceptions(projectId, 10);

            long totalExceptionGroups = countExceptionGroups(projectId, null);
            long newExceptions = countExceptionGroups(projectId, "NEW");
            long resolvedExceptions = countExceptionGroups(projectId, "RESOLVED");

            return DashboardStatsResponse.builder()
                .totalLogs(totalLogs)
                .totalErrors(totalErrors)
                .totalWarnings(totalWarnings)
                .totalExceptionGroups(totalExceptionGroups)
                .newExceptions(newExceptions)
                .resolvedExceptions(resolvedExceptions)
                .logsByLevel(logsByLevel)
                .logsOverTime(new ArrayList<>())
                .topExceptions(topExceptions)
                .build();

        } catch (IOException e) {
            log.error("Error getting dashboard stats", e);
            throw new RuntimeException("Failed to get dashboard stats", e);
        }
    }

    private long countAllLogs(String projectId) throws IOException {
        SearchResponse<Void> response = elasticsearchClient.search(s -> {
            s.index(LOG_INDEX_PATTERN).size(0).trackTotalHits(t -> t.enabled(true));
            if (projectId != null) {
                s.query(q -> q.term(t -> t.field("projectId").value(projectId)));
            }
            return s;
        }, Void.class);
        return response.hits().total() != null ? response.hits().total().value() : 0;
    }

    private long countLogsByLevel(String projectId, String level) throws IOException {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();
        boolQuery.must(m -> m.term(t -> t.field("level").value(level)));
        if (projectId != null) {
            boolQuery.filter(f -> f.term(t -> t.field("projectId").value(projectId)));
        }
        
        SearchResponse<Void> response = elasticsearchClient.search(s -> s
            .index(LOG_INDEX_PATTERN)
            .query(q -> q.bool(boolQuery.build()))
            .size(0)
            .trackTotalHits(t -> t.enabled(true)),
            Void.class
        );
        return response.hits().total() != null ? response.hits().total().value() : 0;
    }

    private List<DashboardStatsResponse.LogLevelCount> getLogsByLevelSimple(String projectId) throws IOException {
        SearchResponse<Void> response = elasticsearchClient.search(s -> {
            s.index(LOG_INDEX_PATTERN)
                .size(0)
                .aggregations("by_level", a -> a.terms(t -> t.field("level")));
            if (projectId != null) {
                s.query(q -> q.term(t -> t.field("projectId").value(projectId)));
            }
            return s;
        }, Void.class);

        List<DashboardStatsResponse.LogLevelCount> result = new ArrayList<>();
        if (response.aggregations() != null && response.aggregations().containsKey("by_level")) {
            StringTermsAggregate levelAgg = response.aggregations().get("by_level").sterms();
            for (StringTermsBucket bucket : levelAgg.buckets().array()) {
                result.add(DashboardStatsResponse.LogLevelCount.builder()
                    .level(bucket.key().stringValue())
                    .count(bucket.docCount())
                    .build());
            }
        }
        return result;
    }

    private Instant calculateFromTime(String timeRange) {
        return switch (timeRange) {
            case "1h" -> Instant.now().minus(1, ChronoUnit.HOURS);
            case "6h" -> Instant.now().minus(6, ChronoUnit.HOURS);
            case "24h" -> Instant.now().minus(24, ChronoUnit.HOURS);
            case "7d" -> Instant.now().minus(7, ChronoUnit.DAYS);
            case "30d" -> Instant.now().minus(30, ChronoUnit.DAYS);
            default -> Instant.now().minus(24, ChronoUnit.HOURS);
        };
    }

    private long countLogs(String projectId, Instant fromTime, String level) throws IOException {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        if (projectId != null) {
            boolQuery.filter(f -> f.term(t -> t.field("projectId").value(projectId)));
        }
        if (level != null) {
            boolQuery.filter(f -> f.term(t -> t.field("level").value(level)));
        }
        if (fromTime != null) {
            final long fromMs = fromTime.toEpochMilli();
            boolQuery.filter(f -> f.range(r -> r
                .number(n -> n
                    .field("timestamp")
                    .gte((double) fromMs)
                )
            ));
        }

        Query query = boolQuery.hasClauses() 
            ? Query.of(q -> q.bool(boolQuery.build()))
            : Query.of(q -> q.matchAll(m -> m));

        SearchResponse<Void> response = elasticsearchClient.search(s -> s
            .index(LOG_INDEX_PATTERN)
            .query(query)
            .size(0)
            .trackTotalHits(t -> t.enabled(true)),
            Void.class
        );

        return response.hits().total() != null ? response.hits().total().value() : 0;
    }

    private long countExceptionGroups(String projectId, String status) throws IOException {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        if (projectId != null) {
            boolQuery.filter(f -> f.term(t -> t.field("projectId").value(projectId)));
        }
        if (status != null) {
            boolQuery.filter(f -> f.term(t -> t.field("status").value(status)));
        }

        SearchResponse<Void> response = elasticsearchClient.search(s -> s
            .index(EXCEPTION_INDEX)
            .query(q -> q.bool(boolQuery.build()))
            .size(0),
            Void.class
        );

        return response.hits().total() != null ? response.hits().total().value() : 0;
    }

    private List<DashboardStatsResponse.LogLevelCount> getLogsByLevel(String projectId, Instant fromTime) throws IOException {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        if (projectId != null) {
            boolQuery.filter(f -> f.term(t -> t.field("projectId").value(projectId)));
        }
        if (fromTime != null) {
            final long fromMs = fromTime.toEpochMilli();
            boolQuery.filter(f -> f.range(r -> r
                .number(n -> n
                    .field("timestamp")
                    .gte((double) fromMs)
                )
            ));
        }

        Query query = boolQuery.hasClauses() 
            ? Query.of(q -> q.bool(boolQuery.build()))
            : Query.of(q -> q.matchAll(m -> m));

        SearchResponse<Void> response = elasticsearchClient.search(s -> s
            .index(LOG_INDEX_PATTERN)
            .query(query)
            .size(0)
            .aggregations("by_level", a -> a.terms(t -> t.field("level"))),
            Void.class
        );

        List<DashboardStatsResponse.LogLevelCount> result = new ArrayList<>();
        StringTermsAggregate levelAgg = response.aggregations().get("by_level").sterms();
        
        for (StringTermsBucket bucket : levelAgg.buckets().array()) {
            result.add(DashboardStatsResponse.LogLevelCount.builder()
                .level(bucket.key().stringValue())
                .count(bucket.docCount())
                .build());
        }

        return result;
    }

    private List<DashboardStatsResponse.TimeSeriesPoint> getLogsOverTime(
            String projectId, Instant fromTime, String timeRange) throws IOException {
        
        String interval = switch (timeRange) {
            case "1h" -> "5m";
            case "6h" -> "30m";
            case "24h" -> "1h";
            case "7d" -> "6h";
            case "30d" -> "1d";
            default -> "1h";
        };

        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        if (projectId != null) {
            boolQuery.filter(f -> f.term(t -> t.field("projectId").value(projectId)));
        }
        if (fromTime != null) {
            final long fromMs = fromTime.toEpochMilli();
            boolQuery.filter(f -> f.range(r -> r
                .number(n -> n
                    .field("timestamp")
                    .gte((double) fromMs)
                )
            ));
        }

        Query query = boolQuery.hasClauses() 
            ? Query.of(q -> q.bool(boolQuery.build()))
            : Query.of(q -> q.matchAll(m -> m));

        SearchResponse<Void> response = elasticsearchClient.search(s -> s
            .index(LOG_INDEX_PATTERN)
            .query(query)
            .size(0)
            .aggregations("over_time", a -> a
                .dateHistogram(dh -> dh
                    .field("timestamp")
                    .fixedInterval(fi -> fi.time(interval))
                )
                .aggregations("errors", sub -> sub
                    .filter(f -> f.term(t -> t.field("level").value("ERROR")))
                )
            ),
            Void.class
        );

        List<DashboardStatsResponse.TimeSeriesPoint> result = new ArrayList<>();
        DateHistogramAggregate timeAgg = response.aggregations().get("over_time").dateHistogram();

        for (DateHistogramBucket bucket : timeAgg.buckets().array()) {
            long errorCount = bucket.aggregations().get("errors").filter().docCount();
            result.add(DashboardStatsResponse.TimeSeriesPoint.builder()
                .timestamp(bucket.keyAsString())
                .count(bucket.docCount())
                .errors(errorCount)
                .build());
        }

        return result;
    }

    private List<DashboardStatsResponse.TopException> getTopExceptions(String projectId, int limit) throws IOException {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        if (projectId != null) {
            boolQuery.filter(f -> f.term(t -> t.field("projectId").value(projectId)));
        }

        SearchResponse<ExceptionGroupDocument> response = elasticsearchClient.search(s -> s
            .index(EXCEPTION_INDEX)
            .query(q -> q.bool(boolQuery.build()))
            .size(limit)
            .sort(sort -> sort.field(f -> f.field("count").order(SortOrder.Desc))),
            ExceptionGroupDocument.class
        );

        return response.hits().hits().stream()
            .map(Hit::source)
            .map(doc -> DashboardStatsResponse.TopException.builder()
                .exceptionClass(doc.getExceptionClass())
                .message(doc.getMessage())
                .count(doc.getCount())
                .lastSeen(doc.getLastSeen() != null ? doc.getLastSeen().toString() : null)
                .status(doc.getStatus() != null ? doc.getStatus().name() : null)
                .build())
            .toList();
    }

    public List<ExceptionGroupDocument> getExceptionGroups(String projectId, String status, int page, int size) {
        try {
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();

            if (projectId != null) {
                boolQuery.filter(f -> f.term(t -> t.field("projectId").value(projectId)));
            }
            if (status != null) {
                boolQuery.filter(f -> f.term(t -> t.field("status").value(status)));
            }

            SearchResponse<ExceptionGroupDocument> response = elasticsearchClient.search(s -> s
                .index(EXCEPTION_INDEX)
                .query(q -> q.bool(boolQuery.build()))
                .from(page * size)
                .size(size)
                .sort(sort -> sort.field(f -> f.field("lastSeen").order(SortOrder.Desc))),
                ExceptionGroupDocument.class
            );

            return response.hits().hits().stream()
                .map(Hit::source)
                .toList();

        } catch (IOException e) {
            log.error("Error getting exception groups", e);
            throw new RuntimeException("Failed to get exception groups", e);
        }
    }

    public ExceptionGroupDocument getExceptionGroup(String id) {
        try {
            var response = elasticsearchClient.get(g -> g
                .index(EXCEPTION_INDEX)
                .id(id),
                ExceptionGroupDocument.class
            );

            return response.source();
        } catch (IOException e) {
            log.error("Error getting exception group: {}", id, e);
            throw new RuntimeException("Failed to get exception group", e);
        }
    }
}
