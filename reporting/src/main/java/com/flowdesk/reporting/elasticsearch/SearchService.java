package com.flowdesk.reporting.elasticsearch;

import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final SearchDocumentRepository repository;

    public SearchService(ElasticsearchOperations elasticsearchOperations,
                         SearchDocumentRepository repository) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.repository = repository;
    }

    public List<Map<String, Object>> search(String query, UUID tenantId) {
        Criteria criteria = new Criteria("tenantId").is(tenantId.toString())
                .and(new Criteria("title").contains(query)
                        .or(new Criteria("description").contains(query)));
        Query searchQuery = new CriteriaQuery(criteria);

        SearchHits<SearchDocument> hits = elasticsearchOperations.search(searchQuery, SearchDocument.class);

        return hits.stream()
                .map(SearchHit::getContent)
                .map(doc -> Map.<String, Object>of(
                        "id", doc.getId(),
                        "module", doc.getModule() != null ? doc.getModule() : "",
                        "entityType", doc.getEntityType() != null ? doc.getEntityType() : "",
                        "title", doc.getTitle() != null ? doc.getTitle() : "",
                        "status", doc.getStatus() != null ? doc.getStatus() : ""
                ))
                .collect(Collectors.toList());
    }

    public void indexDocument(SearchDocument doc) {
        repository.save(doc);
    }
}
