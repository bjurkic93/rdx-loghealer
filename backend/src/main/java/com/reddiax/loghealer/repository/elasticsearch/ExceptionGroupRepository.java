package com.reddiax.loghealer.repository.elasticsearch;

import com.reddiax.loghealer.document.ExceptionGroupDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExceptionGroupRepository extends ElasticsearchRepository<ExceptionGroupDocument, String> {

    Optional<ExceptionGroupDocument> findByProjectIdAndFingerprint(String projectId, String fingerprint);
}
