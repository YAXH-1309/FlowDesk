package com.flowdesk.reporting.readmodel;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

/**
 * Spring Data Elasticsearch repository for the CQRS read model.
 * All reporting GET queries use this repository — never PostgreSQL directly.
 */
public interface ReadModelRepository extends ElasticsearchRepository<ReadModelDocument, String> {

    Page<ReadModelDocument> findByTenantIdAndModule(String tenantId, String module, Pageable pageable);

    Page<ReadModelDocument> findByTenantIdAndModuleAndEntityType(
            String tenantId, String module, String entityType, Pageable pageable);

    Page<ReadModelDocument> findByTenantIdAndModuleAndStatus(
            String tenantId, String module, String status, Pageable pageable);

    long countByTenantIdAndModule(String tenantId, String module);

    long countByTenantIdAndModuleAndStatus(String tenantId, String module, String status);

    List<ReadModelDocument> findByTenantIdAndDisplayNameContainingOrTenantIdAndDescriptionContaining(
            String tenantId1, String displayName, String tenantId2, String description);
}
