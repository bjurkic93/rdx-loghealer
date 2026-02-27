package com.reddiax.loghealer.service.detection;

import com.reddiax.loghealer.document.ExceptionGroupDocument;
import com.reddiax.loghealer.document.LogEntryDocument;
import com.reddiax.loghealer.repository.elasticsearch.ExceptionGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExceptionDetectorService {

    private final ExceptionGroupRepository exceptionGroupRepository;

    public void processException(LogEntryDocument logEntry) {
        String fingerprint = generateFingerprint(logEntry);
        logEntry.setFingerprint(fingerprint);
        
        Optional<ExceptionGroupDocument> existingGroup = exceptionGroupRepository
            .findByProjectIdAndFingerprint(logEntry.getProjectId(), fingerprint);
        
        if (existingGroup.isPresent()) {
            updateExistingGroup(existingGroup.get());
        } else {
            createNewGroup(logEntry, fingerprint);
        }
    }

    private String generateFingerprint(LogEntryDocument logEntry) {
        StringBuilder fingerprintSource = new StringBuilder();
        
        fingerprintSource.append(logEntry.getProjectId());
        
        if (logEntry.getExceptionClass() != null) {
            fingerprintSource.append(logEntry.getExceptionClass());
        }
        
        String normalizedStackTrace = normalizeStackTrace(logEntry.getStackTrace());
        fingerprintSource.append(normalizedStackTrace);
        
        return hashString(fingerprintSource.toString());
    }

    private String normalizeStackTrace(String stackTrace) {
        if (stackTrace == null || stackTrace.isBlank()) {
            return "";
        }
        
        String[] lines = stackTrace.split("\n");
        StringBuilder normalized = new StringBuilder();
        
        for (int i = 0; i < Math.min(5, lines.length); i++) {
            String line = lines[i].trim();
            line = line.replaceAll(":\\d+\\)", ":X)");
            line = line.replaceAll("@[a-f0-9]+", "@X");
            normalized.append(line);
        }
        
        return normalized.toString();
    }

    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private void updateExistingGroup(ExceptionGroupDocument group) {
        group.setLastSeen(Instant.now());
        group.setCount(group.getCount() + 1);
        
        if (group.getStatus() == ExceptionGroupDocument.ExceptionStatus.RESOLVED) {
            group.setStatus(ExceptionGroupDocument.ExceptionStatus.NEW);
            log.warn("Exception group {} has regressed!", group.getId());
        }
        
        exceptionGroupRepository.save(group);
        log.debug("Updated exception group: {} (count: {})", group.getId(), group.getCount());
    }

    private void createNewGroup(LogEntryDocument logEntry, String fingerprint) {
        ExceptionGroupDocument group = ExceptionGroupDocument.builder()
            .id(UUID.randomUUID().toString())
            .projectId(logEntry.getProjectId())
            .tenantId(logEntry.getTenantId())
            .fingerprint(fingerprint)
            .exceptionClass(logEntry.getExceptionClass())
            .message(extractFirstLine(logEntry.getMessage()))
            .sampleStackTrace(logEntry.getStackTrace())
            .firstSeen(Instant.now())
            .lastSeen(Instant.now())
            .count(1L)
            .status(ExceptionGroupDocument.ExceptionStatus.NEW)
            .environment(logEntry.getEnvironment())
            .build();
        
        exceptionGroupRepository.save(group);
        log.info("Created new exception group: {} (class: {})", group.getId(), group.getExceptionClass());
    }

    private String extractFirstLine(String message) {
        if (message == null) return null;
        int newlineIndex = message.indexOf('\n');
        return newlineIndex > 0 ? message.substring(0, newlineIndex) : message;
    }
}
