package com.reddiax.loghealer.service;

import com.reddiax.loghealer.entity.Project;
import com.reddiax.loghealer.repository.jpa.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectDiscoveryService {

    private final ProjectRepository projectRepository;

    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "([a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+)"
    );

    private static final Pattern STACK_TRACE_PATTERN = Pattern.compile(
            "at\\s+([a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+)\\.[A-Z]"
    );

    public Optional<Project> discoverProjectFromLog(String logger, String message, String stackTrace) {
        Set<String> packagePrefixes = new HashSet<>();

        if (logger != null && !logger.isEmpty()) {
            extractPackagePrefix(logger).ifPresent(packagePrefixes::add);
        }

        if (stackTrace != null && !stackTrace.isEmpty()) {
            packagePrefixes.addAll(extractPackagePrefixesFromStackTrace(stackTrace));
        }

        if (message != null && !message.isEmpty()) {
            Matcher matcher = PACKAGE_PATTERN.matcher(message);
            while (matcher.find()) {
                String pkg = matcher.group(1);
                if (isLikelyPackageName(pkg)) {
                    extractPackagePrefix(pkg).ifPresent(packagePrefixes::add);
                }
            }
        }

        if (packagePrefixes.isEmpty()) {
            return Optional.empty();
        }

        List<Project> allProjects = projectRepository.findAll().stream()
                .filter(Project::isActive)
                .filter(p -> p.getPackagePrefix() != null && !p.getPackagePrefix().isEmpty())
                .toList();

        for (String pkgPrefix : packagePrefixes) {
            for (Project project : allProjects) {
                if (pkgPrefix.startsWith(project.getPackagePrefix()) || 
                    project.getPackagePrefix().startsWith(pkgPrefix)) {
                    log.debug("Discovered project {} for package prefix {}", project.getName(), pkgPrefix);
                    return Optional.of(project);
                }
            }
        }

        return Optional.empty();
    }

    public Optional<Project> discoverProjectFromPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return Optional.empty();
        }

        List<Project> allProjects = projectRepository.findAll().stream()
                .filter(Project::isActive)
                .filter(p -> p.getPackagePrefix() != null && !p.getPackagePrefix().isEmpty())
                .sorted((a, b) -> Integer.compare(b.getPackagePrefix().length(), a.getPackagePrefix().length()))
                .toList();

        for (Project project : allProjects) {
            if (packageName.startsWith(project.getPackagePrefix())) {
                return Optional.of(project);
            }
        }

        return Optional.empty();
    }

    public List<DiscoveredPackage> analyzeLogsForDiscovery(List<LogPackageInfo> logs) {
        Map<String, DiscoveredPackage> discovered = new HashMap<>();

        for (LogPackageInfo logInfo : logs) {
            Set<String> prefixes = new HashSet<>();

            if (logInfo.logger() != null) {
                extractPackagePrefix(logInfo.logger()).ifPresent(prefixes::add);
            }

            if (logInfo.stackTrace() != null) {
                prefixes.addAll(extractPackagePrefixesFromStackTrace(logInfo.stackTrace()));
            }

            for (String prefix : prefixes) {
                discovered.computeIfAbsent(prefix, k -> new DiscoveredPackage(k, 0, null))
                        .incrementCount();
                
                Optional<Project> project = discoverProjectFromPackage(prefix);
                if (project.isPresent()) {
                    discovered.get(prefix).setMatchedProject(project.get());
                }
            }
        }

        return discovered.values().stream()
                .sorted((a, b) -> Integer.compare(b.count(), a.count()))
                .toList();
    }

    private Optional<String> extractPackagePrefix(String fullPackage) {
        if (fullPackage == null || !fullPackage.contains(".")) {
            return Optional.empty();
        }

        String[] parts = fullPackage.split("\\.");
        if (parts.length < 2) {
            return Optional.empty();
        }

        if (parts.length >= 3) {
            return Optional.of(parts[0] + "." + parts[1] + "." + parts[2]);
        }
        
        return Optional.of(parts[0] + "." + parts[1]);
    }

    private Set<String> extractPackagePrefixesFromStackTrace(String stackTrace) {
        Set<String> prefixes = new HashSet<>();
        Matcher matcher = STACK_TRACE_PATTERN.matcher(stackTrace);
        
        while (matcher.find()) {
            String pkg = matcher.group(1);
            if (isLikelyPackageName(pkg)) {
                extractPackagePrefix(pkg).ifPresent(prefixes::add);
            }
        }
        
        return prefixes;
    }

    private boolean isLikelyPackageName(String pkg) {
        if (pkg.startsWith("java.") || pkg.startsWith("javax.") || 
            pkg.startsWith("sun.") || pkg.startsWith("jdk.")) {
            return false;
        }
        if (pkg.startsWith("org.springframework.") || pkg.startsWith("org.hibernate.") ||
            pkg.startsWith("org.apache.") || pkg.startsWith("org.slf4j.") ||
            pkg.startsWith("ch.qos.logback.")) {
            return false;
        }
        return true;
    }

    public record LogPackageInfo(String logger, String stackTrace) {}

    public static class DiscoveredPackage {
        private final String packagePrefix;
        private int count;
        private Project matchedProject;

        public DiscoveredPackage(String packagePrefix, int count, Project matchedProject) {
            this.packagePrefix = packagePrefix;
            this.count = count;
            this.matchedProject = matchedProject;
        }

        public String packagePrefix() { return packagePrefix; }
        public int count() { return count; }
        public Project matchedProject() { return matchedProject; }

        public void incrementCount() { this.count++; }
        public void setMatchedProject(Project project) { this.matchedProject = project; }
    }
}
