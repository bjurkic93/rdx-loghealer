package com.reddiax.loghealer.service;

import com.reddiax.loghealer.entity.Project;
import com.reddiax.loghealer.repository.jpa.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class EventIngestionService {

    private static final Logger log = LoggerFactory.getLogger(EventIngestionService.class);

    private final ProjectRepository projectRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    public EventIngestionService(ProjectRepository projectRepository,
                                  ElasticsearchOperations elasticsearchOperations) {
        this.projectRepository = projectRepository;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public int processEvents(String apiKey, List<Map<String, Object>> events) {
        if (events == null || events.isEmpty()) {
            return 0;
        }

        Optional<Project> projectOpt = projectRepository.findByApiKey(apiKey);
        if (projectOpt.isEmpty()) {
            log.warn("Invalid API key: {}", apiKey.substring(0, Math.min(8, apiKey.length())) + "...");
            throw new IllegalArgumentException("Invalid or inactive API key");
        }

        Project project = projectOpt.get();
        if (!project.getIsActive()) {
            throw new IllegalArgumentException("Project is inactive");
        }

        List<IndexQuery> indexQueries = new ArrayList<>();

        for (Map<String, Object> event : events) {
            String type = (String) event.getOrDefault("type", "unknown");
            String indexName = getIndexName(type);

            Map<String, Object> document = new HashMap<>(event);
            document.put("projectId", project.getId().toString());
            document.put("projectName", project.getName());
            document.put("tenantId", project.getTenant().getId().toString());
            document.put("ingestedAt", new Date());

            IndexQuery indexQuery = new IndexQueryBuilder()
                    .withId(UUID.randomUUID().toString())
                    .withObject(document)
                    .build();

            indexQueries.add(indexQuery);

            if ("exception".equals(type)) {
                log.info("Exception received: {} - {} [traceId={}, project={}]",
                        event.get("exceptionClass"),
                        event.get("exceptionMessage"),
                        event.get("traceId"),
                        project.getName());
            } else if ("slow_request".equals(type)) {
                log.info("Slow request: {} {} took {}ms [traceId={}, project={}]",
                        event.get("method"),
                        event.get("endpoint"),
                        event.get("durationMs"),
                        event.get("traceId"),
                        project.getName());
            }

            IndexCoordinates indexCoordinates = IndexCoordinates.of(indexName);
            elasticsearchOperations.index(indexQuery, indexCoordinates);
        }

        log.debug("Indexed {} events for project {}", events.size(), project.getName());
        return events.size();
    }

    private String getIndexName(String eventType) {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
        return switch (eventType) {
            case "exception" -> "loghealer-exceptions-" + dateStr;
            case "slow_request" -> "loghealer-performance-" + dateStr;
            default -> "loghealer-events-" + dateStr;
        };
    }
}
