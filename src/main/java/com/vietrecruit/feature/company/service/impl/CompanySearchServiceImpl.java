package com.vietrecruit.feature.company.service.impl;

import static com.vietrecruit.common.config.elasticsearch.ElasticsearchConstants.INDEX_COMPANIES;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.vietrecruit.common.response.SearchPageResponse;
import com.vietrecruit.feature.company.document.CompanyDocument;
import com.vietrecruit.feature.company.dto.request.CompanySearchRequest;
import com.vietrecruit.feature.company.dto.response.CompanySearchResponse;
import com.vietrecruit.feature.company.service.CompanySearchService;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanySearchServiceImpl implements CompanySearchService {

    private final ElasticsearchClient esClient;

    @Override
    @CircuitBreaker(name = "elasticsearch", fallbackMethod = "searchFallback")
    public SearchPageResponse<CompanySearchResponse> search(CompanySearchRequest request) {
        try {
            var query = buildQuery(request);

            SearchResponse<CompanyDocument> response =
                    esClient.search(
                            s ->
                                    s.index(INDEX_COMPANIES)
                                            .query(query)
                                            .highlight(
                                                    h ->
                                                            h.fields(
                                                                            "name",
                                                                            f ->
                                                                                    f.fragmentSize(
                                                                                                    150)
                                                                                            .numberOfFragments(
                                                                                                    2))
                                                                    .preTags("<mark>")
                                                                    .postTags("</mark>"))
                                            .from(request.getPage() * request.getSize())
                                            .size(request.getSize()),
                            CompanyDocument.class);

            return mapToSearchPageResponse(response, request.getPage(), request.getSize());
        } catch (IOException e) {
            log.error("Company search failed: {}", e.getMessage(), e);
            return emptyResponse(request.getPage(), request.getSize());
        } catch (RuntimeException e) {
            log.error("Company search failed (transport/runtime): {}", e.getMessage(), e);
            return emptyResponse(request.getPage(), request.getSize());
        }
    }

    private Query buildQuery(CompanySearchRequest request) {
        var boolQuery =
                BoolQuery.of(
                        b -> {
                            String q = request.getQ();
                            if (q != null && !q.isBlank()) {
                                b.must(
                                        m ->
                                                m.multiMatch(
                                                        mm ->
                                                                mm.query(q)
                                                                        .fields("name^3", "domain")
                                                                        .type(
                                                                                TextQueryType
                                                                                        .BestFields)
                                                                        .fuzziness("AUTO")
                                                                        .prefixLength(2)));
                                b.should(
                                        sh ->
                                                sh.term(
                                                        t ->
                                                                t.field("name.keyword")
                                                                        .value(q)
                                                                        .boost(5.0f)));
                            } else {
                                b.must(m -> m.matchAll(ma -> ma));
                            }
                            return b;
                        });

        return Query.of(q -> q.bool(boolQuery));
    }

    private SearchPageResponse<CompanySearchResponse> mapToSearchPageResponse(
            SearchResponse<CompanyDocument> response, int page, int size) {
        long total = response.hits().total() != null ? response.hits().total().value() : 0;
        int totalPages = (int) Math.ceil((double) total / size);

        List<CompanySearchResponse> content =
                response.hits().hits().stream()
                        .map(this::mapHitToResponse)
                        .collect(Collectors.toList());

        return SearchPageResponse.<CompanySearchResponse>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(total)
                .totalPages(totalPages)
                .first(page == 0)
                .last(page >= totalPages - 1)
                .empty(content.isEmpty())
                .tookMs(response.took())
                .build();
    }

    private CompanySearchResponse mapHitToResponse(Hit<CompanyDocument> hit) {
        CompanyDocument doc = hit.source();
        if (doc == null) {
            return CompanySearchResponse.builder().build();
        }

        Map<String, List<String>> highlights = new HashMap<>();
        if (hit.highlight() != null && !hit.highlight().isEmpty()) {
            highlights.putAll(hit.highlight());
        }

        return CompanySearchResponse.builder()
                .id(doc.getId())
                .name(doc.getName())
                .domain(doc.getDomain())
                .website(doc.getWebsite())
                .createdAt(doc.getCreatedAt())
                .highlights(highlights.isEmpty() ? null : highlights)
                .score(hit.score())
                .build();
    }

    private SearchPageResponse<CompanySearchResponse> searchFallback(
            CompanySearchRequest request, Throwable t) {
        log.warn("Elasticsearch circuit breaker open for company search: {}", t.getMessage());
        return emptyResponse(request.getPage(), request.getSize());
    }

    private SearchPageResponse<CompanySearchResponse> emptyResponse(int page, int size) {
        return SearchPageResponse.<CompanySearchResponse>builder()
                .content(new ArrayList<>())
                .page(page)
                .size(size)
                .totalElements(0)
                .totalPages(0)
                .first(true)
                .last(true)
                .empty(true)
                .tookMs(0)
                .build();
    }
}
