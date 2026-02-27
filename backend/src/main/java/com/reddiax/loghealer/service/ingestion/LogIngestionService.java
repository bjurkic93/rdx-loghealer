package com.reddiax.loghealer.service.ingestion;

import com.reddiax.loghealer.document.LogEntryDocument;
import com.reddiax.loghealer.dto.LogEntryRequest;
import com.reddiax.loghealer.entity.Project;
import com.reddiax.loghealer.repository.elasticsearch.LogEntryRepository;
import com.reddiax.loghealer.repository.jpa.ProjectRepository;
import com.reddiax.loghealer.service.detection.ExceptionDetectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogIngestionService {

    private final ProjectRepository projectRepository;
    private final LogEntryRepository logEntryRepository;
    private final ExceptionDetectorService exceptionDetectorService;

    public void ingestSingle(String apiKey, LogEntryRequest request) {
        Project project = validateApiKey(apiKey);
        LogEntryDocument document = mapToDocument(request, project);
        
        saveAndProcess(document);
        log.debug("Ingested single log for project: {}", project.getName());
    }

    public int ingestBatch(String apiKey, List<LogEntryRequest> requests) {
        Project project = validateApiKey(apiKey);
        
        List<LogEntryDocument> documents = requests.stream()
            .map(req -> mapToDocument(req, project))
            .toList();
        
        saveAndProcessBatch(documents);
        log.info("Ingested {} logs for project: {}", documents.size(), project.getName());
        
        return documents.size();
    }

    private Project validateApiKey(String apiKey) {
        return projectRepository.findByApiKey(apiKey)
            .filter(Project::isActive)
            .orElseThrow(() -> new IllegalArgumentException("Invalid or inactive API key"));
    }

    private LogEntryDocument mapToDocument(LogEntryRequest request, Project project) {
        return LogEntryDocument.builder()
            .id(UUID.randomUUID().toString())
            .projectId(project.getId().toString())
            .tenantId(project.getTenant().getId().toString())
            .level(request.getLevel().toUpperCase())
            .logger(request.getLogger())
            .message(request.getMessage())
            .stackTrace(request.getStackTrace())
            .exceptionClass(request.getExceptionClass())
            .threadName(request.getThreadName())
            .metadata(request.getMetadata())
            .timestamp(request.getTimestamp() != null ? request.getTimestamp() : Instant.now())
            .traceId(request.getTraceId())
            .spanId(request.getSpanId())
            .hostName(request.getHostName())
            .environment(request.getEnvironment())
            .build();
    }

    @Async
    protected void saveAndProcess(LogEntryDocument document) {
        logEntryRepository.save(document);
        
        if (isException(document)) {
            exceptionDetectorService.processException(document);
        }
    }

    @Async
    protected void saveAndProcessBatch(List<LogEntryDocument> documents) {
        logEntryRepository.saveAll(documents);
        
        documents.stream()
            .filter(this::isException)
            .forEach(exceptionDetectorService::processException);
    }

    private boolean isException(LogEntryDocument document) {
        return "ERROR".equalsIgnoreCase(document.getLevel()) 
            && document.getStackTrace() != null 
            && !document.getStackTrace().isBlank();
    }
}
