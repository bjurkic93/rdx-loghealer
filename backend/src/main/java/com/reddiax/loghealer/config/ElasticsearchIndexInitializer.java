package com.reddiax.loghealer.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.ExistsIndexTemplateRequest;
import co.elastic.clients.elasticsearch.indices.PutIndexTemplateRequest;
import co.elastic.clients.elasticsearch.indices.put_index_template.IndexTemplateMapping;
import co.elastic.clients.elasticsearch._types.mapping.*;
import com.reddiax.loghealer.document.LogEntryDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchIndexInitializer {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient elasticsearchClient;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeIndices() {
        createLogIndexTemplate();
        createIndexIfNotExists(LogEntryDocument.class);
        log.info("Elasticsearch indices initialized");
    }

    private void createLogIndexTemplate() {
        try {
            boolean exists = elasticsearchClient.indices()
                .existsIndexTemplate(ExistsIndexTemplateRequest.of(r -> r.name("loghealer-logs-template")))
                .value();
            
            if (!exists) {
                Map<String, Property> properties = new HashMap<>();
                properties.put("id", Property.of(p -> p.keyword(k -> k)));
                properties.put("projectId", Property.of(p -> p.keyword(k -> k)));
                properties.put("tenantId", Property.of(p -> p.keyword(k -> k)));
                properties.put("level", Property.of(p -> p.keyword(k -> k)));
                properties.put("logger", Property.of(p -> p.keyword(k -> k)));
                properties.put("message", Property.of(p -> p.text(tx -> tx.analyzer("standard"))));
                properties.put("stackTrace", Property.of(p -> p.text(tx -> tx)));
                properties.put("exceptionClass", Property.of(p -> p.keyword(k -> k)));
                properties.put("fingerprint", Property.of(p -> p.keyword(k -> k)));
                properties.put("threadName", Property.of(p -> p.keyword(k -> k)));
                properties.put("metadata", Property.of(p -> p.flattened(f -> f)));
                properties.put("timestamp", Property.of(p -> p.date(d -> d.format("epoch_millis"))));
                properties.put("traceId", Property.of(p -> p.keyword(k -> k)));
                properties.put("spanId", Property.of(p -> p.keyword(k -> k)));
                properties.put("parentSpanId", Property.of(p -> p.keyword(k -> k)));
                properties.put("serviceName", Property.of(p -> p.keyword(k -> k)));
                properties.put("hostName", Property.of(p -> p.keyword(k -> k)));
                properties.put("environment", Property.of(p -> p.keyword(k -> k)));

                elasticsearchClient.indices().putIndexTemplate(PutIndexTemplateRequest.of(r -> r
                    .name("loghealer-logs-template")
                    .indexPatterns("loghealer-logs-*")
                    .priority(100L)
                    .template(IndexTemplateMapping.of(t -> t
                        .mappings(TypeMapping.of(m -> m.properties(properties)))
                    ))
                ));
                log.info("Created index template: loghealer-logs-template");
            }
        } catch (IOException e) {
            log.error("Failed to create index template", e);
        }
    }

    private void createIndexIfNotExists(Class<?> documentClass) {
        IndexOperations indexOps = elasticsearchOperations.indexOps(documentClass);
        if (!indexOps.exists()) {
            indexOps.create();
            indexOps.putMapping(indexOps.createMapping(documentClass));
            log.info("Created index for: {}", documentClass.getSimpleName());
        }
    }
}
