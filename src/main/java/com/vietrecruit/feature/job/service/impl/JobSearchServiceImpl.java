package com.vietrecruit.feature.job.service.impl;

import static com.vietrecruit.common.config.elasticsearch.ElasticsearchConstants.INDEX_JOBS;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.vietrecruit.common.response.SearchPageResponse;
import com.vietrecruit.feature.job.document.JobDocument;
import com.vietrecruit.feature.job.dto.request.JobSearchRequest;
import com.vietrecruit.feature.job.dto.response.JobSearchResponse;
import com.vietrecruit.feature.job.service.JobSearchService;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.FieldValueFactorModifier;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScore;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobSearchServiceImpl implements JobSearchService {

    private final ElasticsearchClient esClient;

    private static final String PUBLISHED_AT_DECAY_SCRIPT =
            "if (!doc.containsKey('published_at') || doc['published_at'].empty) { return 0.5; }"
                    + " double ageD = (params.now - doc['published_at'].value.toInstant().toEpochMilli())"
                    + " / 86400000.0; return 1.0 / (1.0 + ageD * ageD / 196.0);";

    @Override
    @CircuitBreaker(name = "elasticsearch", fallbackMethod = "searchFallback")
    public SearchPageResponse<JobSearchResponse> search(JobSearchRequest request) {
        try {
            var query = buildFunctionScoreQuery(request);

            List<SortOptions> sortOptions = buildSortOptions(request.getSort());

            SearchResponse<JobDocument> response =
                    esClient.search(
                            s ->
                                    s.index(INDEX_JOBS)
                                            .query(query)
                                            .sort(sortOptions)
                                            .highlight(
                                                    h ->
                                                            h.fields(
                                                                            "title",
                                                                            f ->
                                                                                    f.fragmentSize(
                                                                                                    150)
                                                                                            .numberOfFragments(
                                                                                                    2))
                                                                    .fields(
                                                                            "description",
                                                                            f ->
                                                                                    f.fragmentSize(
                                                                                                    150)
                                                                                            .numberOfFragments(
                                                                                                    2))
                                                                    .fields(
                                                                            "requirements",
                                                                            f ->
                                                                                    f.fragmentSize(
                                                                                                    150)
                                                                                            .numberOfFragments(
                                                                                                    2))
                                                                    .preTags("<mark>")
                                                                    .postTags("</mark>"))
                                            .from(
                                                    Math.min(
                                                            request.getPage() * request.getSize(),
                                                            10_000 - request.getSize()))
                                            .size(request.getSize())
                                            .trackTotalHits(t -> t.enabled(true)),
                            JobDocument.class);

            return mapToSearchPageResponse(response, request.getPage(), request.getSize());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<String> autocomplete(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        try {
            SearchResponse<JobDocument> response =
                    esClient.search(
                            s ->
                                    s.index(INDEX_JOBS)
                                            .query(
                                                    q ->
                                                            q.bool(
                                                                    b ->
                                                                            b.must(
                                                                                            m ->
                                                                                                    m
                                                                                                            .matchPhrasePrefix(
                                                                                                                    mp ->
                                                                                                                            mp.field(
                                                                                                                                            "title")
                                                                                                                                    .query(
                                                                                                                                            query)))
                                                                                    .filter(
                                                                                            f ->
                                                                                                    f
                                                                                                            .term(
                                                                                                                    t ->
                                                                                                                            t.field(
                                                                                                                                            "status")
                                                                                                                                    .value(
                                                                                                                                            FieldValue
                                                                                                                                                    .of(
                                                                                                                                                            "PUBLISHED"))))))
                                            .collapse(c -> c.field("title.keyword"))
                                            .source(sc -> sc.filter(sf -> sf.includes("title")))
                                            .size(limit),
                            JobDocument.class);

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(doc -> doc != null && doc.getTitle() != null)
                    .map(JobDocument::getTitle)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Job autocomplete failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private Query buildFunctionScoreQuery(JobSearchRequest request) {
        var boolQuery = buildBoolQuery(request);

        List<FunctionScore> functions = new ArrayList<>();

        // 1. field_value_factor: view_count, modifier log1p, factor 0.3
        functions.add(
                FunctionScore.of(
                        fn ->
                                fn.fieldValueFactor(
                                        fvf ->
                                                fvf.field("view_count")
                                                        .modifier(FieldValueFactorModifier.Log1p)
                                                        .factor(0.3)
                                                        .missing(0.0))));

        // 2. field_value_factor: application_count, modifier log1p, factor 0.2
        functions.add(
                FunctionScore.of(
                        fn ->
                                fn.fieldValueFactor(
                                        fvf ->
                                                fvf.field("application_count")
                                                        .modifier(FieldValueFactorModifier.Log1p)
                                                        .factor(0.2)
                                                        .missing(0.0))));

        // 3. gauss decay: published_at, scale 14d, decay 0.5 (Cauchy approximation via script)
        functions.add(
                FunctionScore.of(
                        fn ->
                                fn.scriptScore(
                                        ss ->
                                                ss.script(
                                                        sc ->
                                                                sc.source(PUBLISHED_AT_DECAY_SCRIPT)
                                                                        .params(
                                                                                Map.of(
                                                                                        "now",
                                                                                        JsonData.of(
                                                                                                System
                                                                                                        .currentTimeMillis())))))));

        // 4. filter boost: is_hot = true, weight 2.0
        functions.add(
                FunctionScore.of(
                        fn ->
                                fn.filter(f -> f.term(t -> t.field("is_hot").value(true)))
                                        .weight(2.0)));

        // 5. filter boost: is_featured = true, weight 1.5
        functions.add(
                FunctionScore.of(
                        fn ->
                                fn.filter(f -> f.term(t -> t.field("is_featured").value(true)))
                                        .weight(1.5)));

        return Query.of(
                q ->
                        q.functionScore(
                                fs ->
                                        fs.query(innerQ -> innerQ.bool(boolQuery))
                                                .functions(functions)
                                                .scoreMode(FunctionScoreMode.Sum)
                                                .boostMode(FunctionBoostMode.Multiply)));
    }

    private BoolQuery buildBoolQuery(JobSearchRequest request) {
        return BoolQuery.of(
                b -> {
                    String q = request.getQ();
                    if (q != null && !q.isBlank()) {
                        b.must(
                                m ->
                                        m.multiMatch(
                                                mm ->
                                                        mm.query(q)
                                                                .fields(
                                                                        "title^3",
                                                                        "description",
                                                                        "requirements^1.5",
                                                                        "company_name",
                                                                        "category_name^2")
                                                                .type(TextQueryType.BestFields)
                                                                .fuzziness("AUTO")
                                                                .prefixLength(2)));
                        b.should(sh -> sh.term(t -> t.field("title.keyword").value(q).boost(5.0f)));
                    } else {
                        b.must(m -> m.matchAll(ma -> ma));
                    }

                    // Hard filter: only PUBLISHED jobs
                    b.filter(f -> f.term(t -> t.field("status").value(FieldValue.of("PUBLISHED"))));

                    if (request.getLocationId() != null) {
                        b.filter(
                                f ->
                                        f.term(
                                                t ->
                                                        t.field("location_id")
                                                                .value(
                                                                        request.getLocationId()
                                                                                .toString())));
                    }

                    if (request.getCategoryId() != null) {
                        b.filter(
                                f ->
                                        f.term(
                                                t ->
                                                        t.field("category_id")
                                                                .value(
                                                                        request.getCategoryId()
                                                                                .toString())));
                    }

                    if (request.getSalaryMin() != null) {
                        b.filter(
                                f ->
                                        f.range(
                                                r ->
                                                        r.number(
                                                                n ->
                                                                        n.field("max_salary")
                                                                                .gte(
                                                                                        request
                                                                                                .getSalaryMin()))));
                    }

                    if (request.getSalaryMax() != null) {
                        b.filter(
                                f ->
                                        f.range(
                                                r ->
                                                        r.number(
                                                                n ->
                                                                        n.field("min_salary")
                                                                                .lte(
                                                                                        request
                                                                                                .getSalaryMax()))));
                    }

                    return b;
                });
    }

    private SearchPageResponse<JobSearchResponse> mapToSearchPageResponse(
            SearchResponse<JobDocument> response, int page, int size) {
        long total = response.hits().total() != null ? response.hits().total().value() : 0;
        int totalPages = (int) Math.ceil((double) total / size);

        List<JobSearchResponse> content =
                response.hits().hits().stream()
                        .map(this::mapHitToResponse)
                        .collect(Collectors.toList());

        return SearchPageResponse.<JobSearchResponse>builder()
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

    private JobSearchResponse mapHitToResponse(Hit<JobDocument> hit) {
        JobDocument doc = hit.source();
        if (doc == null) {
            return JobSearchResponse.builder().build();
        }

        Map<String, List<String>> highlights = new HashMap<>();
        if (hit.highlight() != null && !hit.highlight().isEmpty()) {
            highlights.putAll(hit.highlight());
        }

        return JobSearchResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .description(truncate(doc.getDescription(), 300))
                .requirements(truncate(doc.getRequirements(), 300))
                .companyName(doc.getCompanyName())
                .categoryName(doc.getCategoryName())
                .locationName(doc.getLocationName())
                .minSalary(doc.getMinSalary())
                .maxSalary(doc.getMaxSalary())
                .currency(doc.getCurrency())
                .isNegotiable(doc.getIsNegotiable())
                .status(doc.getStatus())
                .publicLink(doc.getPublicLink())
                .createdAt(doc.getCreatedAt())
                .highlights(highlights.isEmpty() ? null : highlights)
                .score(hit.score())
                .build();
    }

    private List<SortOptions> buildSortOptions(String sort) {
        if (sort == null || sort.isBlank() || "relevance".equalsIgnoreCase(sort)) {
            return List.of(); // use score-based ordering from function_score query
        }
        return switch (sort.toLowerCase()) {
            case "newest" ->
                    List.of(
                            SortOptions.of(
                                    s ->
                                            s.field(
                                                    f ->
                                                            f.field("published_at")
                                                                    .order(SortOrder.Desc))));
            case "oldest" ->
                    List.of(
                            SortOptions.of(
                                    s ->
                                            s.field(
                                                    f ->
                                                            f.field("published_at")
                                                                    .order(SortOrder.Asc))));
            case "salary_desc" ->
                    List.of(
                            SortOptions.of(
                                    s ->
                                            s.field(
                                                    f ->
                                                            f.field("max_salary")
                                                                    .order(SortOrder.Desc)
                                                                    .missing("_last"))));
            case "salary_asc" ->
                    List.of(
                            SortOptions.of(
                                    s ->
                                            s.field(
                                                    f ->
                                                            f.field("min_salary")
                                                                    .order(SortOrder.Asc)
                                                                    .missing("_last"))));
            default -> List.of();
        };
    }

    private SearchPageResponse<JobSearchResponse> searchFallback(
            JobSearchRequest request, Throwable t) {
        log.warn("Elasticsearch circuit breaker open for job search: {}", t.getMessage());
        return emptyResponse(request.getPage(), request.getSize());
    }

    private SearchPageResponse<JobSearchResponse> emptyResponse(int page, int size) {
        return SearchPageResponse.<JobSearchResponse>builder()
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

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
