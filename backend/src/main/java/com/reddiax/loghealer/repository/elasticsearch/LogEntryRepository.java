package com.reddiax.loghealer.repository.elasticsearch;

import com.reddiax.loghealer.document.LogEntryDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LogEntryRepository extends ElasticsearchRepository<LogEntryDocument, String> {
}
