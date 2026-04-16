package com.flowdesk.reporting.elasticsearch;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface SearchDocumentRepository extends ElasticsearchRepository<SearchDocument, String> {

    List<SearchDocument> findByTenantIdAndTitleContainingOrTenantIdAndDescriptionContaining(
            String tenantId1, String title, String tenantId2, String description);
}
