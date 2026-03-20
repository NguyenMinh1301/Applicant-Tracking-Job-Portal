package com.vietrecruit.feature.candidate.service.impl;

import static com.vietrecruit.common.config.elasticsearch.ElasticsearchConstants.INDEX_CANDIDATES;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.vietrecruit.common.response.SearchPageResponse;
import com.vietrecruit.feature.candidate.document.CandidateDocument;
import com.vietrecruit.feature.candidate.dto.request.CandidateSearchRequest;
import com.vietrecruit.feature.candidate.dto.response.CandidateSearchResponse;
import com.vietrecruit.feature.candidate.service.CandidateSearchService;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScore;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateSearchServiceImpl implements CandidateSearchService {

    private final ElasticsearchClient esClient;

    private static final String RECENCY_SCRIPT =
            "double ageD = (params.now - doc['updated_at'].value.toInstant().toEpochMilli())"
                    + " / 86400000.0; return 1.0 / (1.0 + ageD * ageD / 900.0);";

    @Override
    public SearchPageResponse<CandidateSearchResponse> search(CandidateSearchRequest request) {
        try {
            var query = buildFunctionScoreQuery(request);

            SearchResponse<CandidateDocument> response =
                    esClient.search(
                            s ->
                                    s.index(INDEX_CANDIDATES)
                                            .query(query)
                                            .highlight(
                                                    h ->
                                                            h.fields(
                                                                            "headline",
                                                                            f ->
                                                                                    f.fragmentSize(
                                                                                                    150)
                                                                                            .numberOfFragments(
                                                                                                    2))
                                                                    .fields(
                                                                            "summary",
                                                                            f ->
                                                                                    f.fragmentSize(
                                                                                                    150)
                                                                                            .numberOfFragments(
                                                                                                    2))
                                                                    .fields(
                                                                            "skills",
                                                                            f ->
                                                                                    f.fragmentSize(
                                                                                                    100)
                                                                                            .numberOfFragments(
                                                                                                    3))
                                                                    .preTags("<mark>")
                                                                    .postTags("</mark>"))
                                            .from(request.getPage() * request.getSize())
                                            .size(request.getSize()),
                            CandidateDocument.class);

            return mapToSearchPageResponse(response, request.getPage(), request.getSize());
        } catch (IOException e) {
            log.error("Candidate search failed: {}", e.getMessage(), e);
            return emptyResponse(request.getPage(), request.getSize());
        }
    }

    private Query buildFunctionScoreQuery(CandidateSearchRequest request) {
        var boolQuery = buildBoolQuery(request);

        FunctionScore recencyFunction =
                FunctionScore.of(
                        fn ->
                                fn.scriptScore(
                                        ss ->
                                                ss.script(
                                                        sc ->
                                                                sc.source(RECENCY_SCRIPT)
                                                                        .params(
                                                                                Map.of(
                                                                                        "now",
                                                                                        JsonData.of(
                                                                                                System
                                                                                                        .currentTimeMillis()))))));

        return Query.of(
                q ->
                        q.functionScore(
                                fs ->
                                        fs.query(innerQ -> innerQ.bool(boolQuery))
                                                .functions(recencyFunction)
                                                .scoreMode(FunctionScoreMode.Sum)
                                                .boostMode(FunctionBoostMode.Multiply)));
    }

    private BoolQuery buildBoolQuery(CandidateSearchRequest request) {
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
                                                                        "headline^3",
                                                                        "summary",
                                                                        "desired_position^2",
                                                                        "skills^2",
                                                                        "education_major")
                                                                .type(TextQueryType.BestFields)
                                                                .fuzziness("AUTO")
                                                                .prefixLength(2)));
                    } else {
                        b.must(m -> m.matchAll(ma -> ma));
                    }

                    // Default: only open-to-work candidates
                    Boolean isOpenToWork = request.getIsOpenToWork();
                    if (isOpenToWork == null || isOpenToWork) {
                        b.filter(
                                f ->
                                        f.term(
                                                t ->
                                                        t.field("is_open_to_work")
                                                                .value(FieldValue.of(true))));
                    }

                    if (request.getSkills() != null && request.getSkills().length > 0) {
                        for (String skill : request.getSkills()) {
                            b.should(
                                    sh ->
                                            sh.match(
                                                    mt ->
                                                            mt.field("skills")
                                                                    .query(skill)
                                                                    .boost(2.0f)));
                        }
                        b.minimumShouldMatch("1");
                    }

                    if (request.getExperienceMin() != null) {
                        b.filter(
                                f ->
                                        f.range(
                                                r ->
                                                        r.number(
                                                                n ->
                                                                        n.field(
                                                                                        "years_of_experience")
                                                                                .gte(
                                                                                        (double)
                                                                                                request
                                                                                                        .getExperienceMin()))));
                    }

                    if (request.getWorkType() != null && !request.getWorkType().isBlank()) {
                        b.filter(
                                f ->
                                        f.term(
                                                t ->
                                                        t.field("work_type")
                                                                .value(request.getWorkType())));
                    }

                    if (request.getEducationLevel() != null
                            && !request.getEducationLevel().isBlank()) {
                        b.filter(
                                f ->
                                        f.term(
                                                t ->
                                                        t.field("education_level")
                                                                .value(
                                                                        request
                                                                                .getEducationLevel())));
                    }

                    return b;
                });
    }

    private SearchPageResponse<CandidateSearchResponse> mapToSearchPageResponse(
            SearchResponse<CandidateDocument> response, int page, int size) {
        long total = response.hits().total() != null ? response.hits().total().value() : 0;
        int totalPages = (int) Math.ceil((double) total / size);

        List<CandidateSearchResponse> content =
                response.hits().hits().stream()
                        .map(this::mapHitToResponse)
                        .collect(Collectors.toList());

        return SearchPageResponse.<CandidateSearchResponse>builder()
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

    private CandidateSearchResponse mapHitToResponse(Hit<CandidateDocument> hit) {
        CandidateDocument doc = hit.source();
        if (doc == null) {
            return CandidateSearchResponse.builder().build();
        }

        Map<String, List<String>> highlights = new HashMap<>();
        if (hit.highlight() != null && !hit.highlight().isEmpty()) {
            highlights.putAll(hit.highlight());
        }

        return CandidateSearchResponse.builder()
                .id(doc.getId())
                .headline(doc.getHeadline())
                .summary(doc.getSummary())
                .desiredPosition(doc.getDesiredPosition())
                .desiredPositionLevel(doc.getDesiredPositionLevel())
                .yearsOfExperience(doc.getYearsOfExperience())
                .skills(doc.getSkills())
                .workType(doc.getWorkType())
                .desiredSalaryMin(doc.getDesiredSalaryMin())
                .desiredSalaryMax(doc.getDesiredSalaryMax())
                .educationLevel(doc.getEducationLevel())
                .educationMajor(doc.getEducationMajor())
                .isOpenToWork(doc.getIsOpenToWork())
                .updatedAt(doc.getUpdatedAt())
                .highlights(highlights.isEmpty() ? null : highlights)
                .score(hit.score())
                .build();
    }

    private SearchPageResponse<CandidateSearchResponse> emptyResponse(int page, int size) {
        return SearchPageResponse.<CandidateSearchResponse>builder()
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
