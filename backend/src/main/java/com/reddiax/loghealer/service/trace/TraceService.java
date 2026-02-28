package com.reddiax.loghealer.service.trace;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.reddiax.loghealer.document.LogEntryDocument;
import com.reddiax.loghealer.dto.TraceTimelineResponse;
import com.reddiax.loghealer.entity.Project;
import com.reddiax.loghealer.entity.ServiceGroup;
import com.reddiax.loghealer.repository.jpa.ProjectRepository;
import com.reddiax.loghealer.service.ServiceGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TraceService {

    private final ElasticsearchClient elasticsearchClient;
    private final ServiceGroupService serviceGroupService;
    private final ProjectRepository projectRepository;

    private static final String LOG_INDEX_PATTERN = "loghealer-logs-*";

    public TraceTimelineResponse getTraceTimeline(String traceId) {
        try {
            List<LogEntryDocument> logs = searchByTraceId(traceId, null);
            return buildTimeline(traceId, logs);
        } catch (IOException e) {
            log.error("Error getting trace timeline for traceId: {}", traceId, e);
            throw new RuntimeException("Failed to get trace timeline", e);
        }
    }

    public TraceTimelineResponse getTraceTimelineForServiceGroup(String traceId, UUID serviceGroupId) {
        try {
            List<UUID> projectIds = serviceGroupService.getProjectIdsForServiceGroup(serviceGroupId);
            List<String> projectIdStrings = projectIds.stream()
                    .map(UUID::toString)
                    .collect(Collectors.toList());

            List<LogEntryDocument> logs = searchByTraceId(traceId, projectIdStrings);
            return buildTimeline(traceId, logs);
        } catch (IOException e) {
            log.error("Error getting trace timeline for traceId: {} in service group: {}", 
                    traceId, serviceGroupId, e);
            throw new RuntimeException("Failed to get trace timeline", e);
        }
    }

    public List<TraceTimelineResponse> findRelatedTraces(String exceptionGroupId, int limit) {
        try {
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();
            boolQuery.must(m -> m.term(t -> t.field("exceptionGroupId").value(exceptionGroupId)));
            boolQuery.must(m -> m.exists(e -> e.field("traceId")));

            SearchResponse<LogEntryDocument> response = elasticsearchClient.search(s -> s
                    .index(LOG_INDEX_PATTERN)
                    .query(q -> q.bool(boolQuery.build()))
                    .size(limit)
                    .sort(sort -> sort.field(f -> f.field("timestamp").order(SortOrder.Desc))),
                    LogEntryDocument.class
            );

            Set<String> uniqueTraceIds = new LinkedHashSet<>();
            for (Hit<LogEntryDocument> hit : response.hits().hits()) {
                if (hit.source() != null && hit.source().getTraceId() != null) {
                    uniqueTraceIds.add(hit.source().getTraceId());
                    if (uniqueTraceIds.size() >= limit) break;
                }
            }

            List<TraceTimelineResponse> timelines = new ArrayList<>();
            for (String traceId : uniqueTraceIds) {
                try {
                    timelines.add(getTraceTimeline(traceId));
                } catch (Exception e) {
                    log.warn("Failed to get timeline for trace: {}", traceId, e);
                }
            }

            return timelines;
        } catch (IOException e) {
            log.error("Error finding related traces for exception: {}", exceptionGroupId, e);
            throw new RuntimeException("Failed to find related traces", e);
        }
    }

    public Map<String, List<LogEntryDocument>> getCorrelatedLogsForServiceGroup(
            UUID serviceGroupId, String traceId) {
        try {
            List<UUID> projectIds = serviceGroupService.getProjectIdsForServiceGroup(serviceGroupId);
            Map<String, List<LogEntryDocument>> logsByService = new LinkedHashMap<>();

            for (UUID projectId : projectIds) {
                Project project = projectRepository.findById(projectId).orElse(null);
                if (project == null) continue;

                List<LogEntryDocument> logs = searchByTraceIdAndProject(traceId, projectId.toString());
                if (!logs.isEmpty()) {
                    logsByService.put(project.getName(), logs);
                }
            }

            return logsByService;
        } catch (IOException e) {
            log.error("Error getting correlated logs for service group: {} trace: {}", 
                    serviceGroupId, traceId, e);
            throw new RuntimeException("Failed to get correlated logs", e);
        }
    }

    private List<LogEntryDocument> searchByTraceId(String traceId, List<String> projectIds) throws IOException {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();
        boolQuery.must(m -> m.term(t -> t.field("traceId").value(traceId)));

        if (projectIds != null && !projectIds.isEmpty()) {
            boolQuery.filter(f -> f.terms(t -> t
                    .field("projectId")
                    .terms(tv -> tv.value(projectIds.stream()
                            .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                            .toList()))
            ));
        }

        SearchResponse<LogEntryDocument> response = elasticsearchClient.search(s -> s
                .index(LOG_INDEX_PATTERN)
                .query(q -> q.bool(boolQuery.build()))
                .size(1000)
                .sort(sort -> sort.field(f -> f.field("timestamp").order(SortOrder.Asc))),
                LogEntryDocument.class
        );

        return response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<LogEntryDocument> searchByTraceIdAndProject(String traceId, String projectId) throws IOException {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();
        boolQuery.must(m -> m.term(t -> t.field("traceId").value(traceId)));
        boolQuery.filter(f -> f.term(t -> t.field("projectId").value(projectId)));

        SearchResponse<LogEntryDocument> response = elasticsearchClient.search(s -> s
                .index(LOG_INDEX_PATTERN)
                .query(q -> q.bool(boolQuery.build()))
                .size(500)
                .sort(sort -> sort.field(f -> f.field("timestamp").order(SortOrder.Asc))),
                LogEntryDocument.class
        );

        return response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private TraceTimelineResponse buildTimeline(String traceId, List<LogEntryDocument> logs) {
        if (logs.isEmpty()) {
            return TraceTimelineResponse.builder()
                    .traceId(traceId)
                    .totalEvents(0)
                    .events(new ArrayList<>())
                    .servicesInvolved(new ArrayList<>())
                    .hasError(false)
                    .build();
        }

        List<TraceTimelineResponse.TraceEvent> events = new ArrayList<>();
        Set<String> services = new LinkedHashSet<>();
        boolean hasError = false;
        String rootCauseService = null;

        for (LogEntryDocument log : logs) {
            String serviceName = log.getServiceName() != null ? log.getServiceName() : log.getProjectId();
            services.add(serviceName);

            boolean isError = "ERROR".equalsIgnoreCase(log.getLevel()) || log.getStackTrace() != null;
            if (isError && rootCauseService == null) {
                rootCauseService = serviceName;
                hasError = true;
            }

            events.add(TraceTimelineResponse.TraceEvent.builder()
                    .id(log.getId())
                    .timestamp(log.getTimestamp() != null ? log.getTimestamp().toEpochMilli() : 0L)
                    .serviceName(serviceName)
                    .projectId(log.getProjectId())
                    .level(log.getLevel())
                    .message(log.getMessage())
                    .logger(log.getLogger())
                    .spanId(log.getSpanId())
                    .parentSpanId(log.getParentSpanId())
                    .isError(isError)
                    .exceptionType(log.getExceptionClass())
                    .stackTrace(log.getStackTrace())
                    .build());
        }

        long startTime = logs.get(0).getTimestamp() != null ? logs.get(0).getTimestamp().toEpochMilli() : 0L;
        long endTime = logs.get(logs.size() - 1).getTimestamp() != null ? logs.get(logs.size() - 1).getTimestamp().toEpochMilli() : 0L;

        return TraceTimelineResponse.builder()
                .traceId(traceId)
                .startTime(startTime)
                .endTime(endTime)
                .durationMs(endTime - startTime)
                .totalEvents(events.size())
                .events(events)
                .servicesInvolved(new ArrayList<>(services))
                .hasError(hasError)
                .rootCauseService(rootCauseService)
                .build();
    }
}
