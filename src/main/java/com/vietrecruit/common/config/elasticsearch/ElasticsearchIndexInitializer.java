package com.vietrecruit.common.config.elasticsearch;

import static com.vietrecruit.common.config.elasticsearch.ElasticsearchConstants.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.analysis.Analyzer;
import co.elastic.clients.elasticsearch._types.analysis.CharFilter;
import co.elastic.clients.elasticsearch._types.analysis.CustomAnalyzer;
import co.elastic.clients.elasticsearch._types.analysis.EdgeNGramTokenFilter;
import co.elastic.clients.elasticsearch._types.analysis.StopTokenFilter;
import co.elastic.clients.elasticsearch._types.analysis.SynonymGraphTokenFilter;
import co.elastic.clients.elasticsearch._types.analysis.TokenFilter;
import co.elastic.clients.elasticsearch._types.mapping.KeywordProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchIndexInitializer {

    private final ElasticsearchClient esClient;

    private static final List<String> VIETNAMESE_STOPWORDS =
            List.of(
                    "của", "và", "là", "trong", "có", "cho", "với", "được", "các", "này", "không",
                    "những", "một", "để", "từ", "khi", "đã", "về", "tại", "theo", "như", "hoặc",
                    "hay", "bạn", "cần", "việc", "năm", "nếu", "sẽ", "đến", "trên", "tới", "qua",
                    "còn", "rất", "nhiều", "bằng", "cùng", "vào", "nên", "phải", "thì");

    private static final List<String> DOMAIN_SYNONYMS =
            List.of(
                    "dev,developer,lập trình viên",
                    "hr,human resources,nhân sự",
                    "pm,project manager,quản lý dự án",
                    "fe,frontend,front-end,giao diện",
                    "be,backend,back-end",
                    "fs,fullstack,full-stack,full stack",
                    "qa,quality assurance,tester,kiểm thử",
                    "ux,ui,designer,thiết kế",
                    "tuyển dụng,recruitment,hiring",
                    "ứng viên,candidate,người ứng tuyển",
                    "lương,salary,thu nhập,compensation",
                    "intern,thực tập,thực tập sinh",
                    "senior,cao cấp",
                    "junior,mới vào nghề",
                    "remote,từ xa,làm việc từ xa",
                    "devops,sre,infrastructure");

    @EventListener(ApplicationReadyEvent.class)
    public void initializeIndices() {
        createIndexIfNotExists(INDEX_JOBS, this::buildJobsMappings);
        createIndexIfNotExists(INDEX_CANDIDATES, this::buildCandidatesMappings);
        createIndexIfNotExists(INDEX_COMPANIES, this::buildCompaniesMappings);
    }

    private void createIndexIfNotExists(String indexName, MappingBuilder mappingBuilder) {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(indexName)).value();
            if (exists) {
                log.info("ES index [{}] already exists, skipping creation", indexName);
                return;
            }

            var settings = buildAnalysisSettings();
            var mappings = mappingBuilder.build();

            esClient.indices()
                    .create(
                            c ->
                                    c.index(indexName)
                                            .settings(settings)
                                            .mappings(
                                                    m ->
                                                            m.properties(mappings)
                                                                    .dynamic(
                                                                            co.elastic.clients
                                                                                    .elasticsearch
                                                                                    ._types.mapping
                                                                                    .DynamicMapping
                                                                                    .Strict)));

            log.info("ES index [{}] created successfully", indexName);
        } catch (IOException e) {
            log.error("Failed to create ES index [{}]: {}", indexName, e.getMessage(), e);
        }
    }

    private IndexSettings buildAnalysisSettings() {
        return IndexSettings.of(
                s ->
                        s.numberOfShards("1")
                                .numberOfReplicas("0")
                                .analysis(
                                        a ->
                                                a.charFilter(
                                                                "html_strip_filter",
                                                                CharFilter.of(
                                                                        cf ->
                                                                                cf.definition(
                                                                                        d ->
                                                                                                d
                                                                                                        .htmlStrip(
                                                                                                                h ->
                                                                                                                        h))))
                                                        .tokenizer(
                                                                "icu_tokenizer",
                                                                t ->
                                                                        t.definition(
                                                                                d ->
                                                                                        d
                                                                                                .icuTokenizer(
                                                                                                        icu ->
                                                                                                                icu
                                                                                                                        .ruleFiles(
                                                                                                                                ""))))
                                                        .filter(buildTokenFilters())
                                                        .analyzer(buildAnalyzers())));
    }

    private Map<String, TokenFilter> buildTokenFilters() {
        return Map.of(
                EDGE_NGRAM_FILTER,
                TokenFilter.of(
                        tf ->
                                tf.definition(
                                        d ->
                                                d.edgeNgram(
                                                        EdgeNGramTokenFilter.of(
                                                                eng ->
                                                                        eng.minGram(2)
                                                                                .maxGram(20))))),
                STOP_FILTER,
                TokenFilter.of(
                        tf ->
                                tf.definition(
                                        d ->
                                                d.stop(
                                                        StopTokenFilter.of(
                                                                st ->
                                                                        st.stopwords(
                                                                                VIETNAMESE_STOPWORDS))))),
                SYNONYM_FILTER,
                TokenFilter.of(
                        tf ->
                                tf.definition(
                                        d ->
                                                d.synonymGraph(
                                                        SynonymGraphTokenFilter.of(
                                                                sg ->
                                                                        sg.synonyms(
                                                                                DOMAIN_SYNONYMS))))));
    }

    private Map<String, Analyzer> buildAnalyzers() {
        return Map.of(
                TEXT_ANALYZER,
                Analyzer.of(
                        a ->
                                a.custom(
                                        CustomAnalyzer.of(
                                                ca ->
                                                        ca.tokenizer("icu_tokenizer")
                                                                .charFilter("html_strip_filter")
                                                                .filter(
                                                                        "icu_folding",
                                                                        "lowercase",
                                                                        STOP_FILTER,
                                                                        SYNONYM_FILTER,
                                                                        EDGE_NGRAM_FILTER)))),
                SEARCH_ANALYZER,
                Analyzer.of(
                        a ->
                                a.custom(
                                        CustomAnalyzer.of(
                                                ca ->
                                                        ca.tokenizer("icu_tokenizer")
                                                                .charFilter("html_strip_filter")
                                                                .filter(
                                                                        "icu_folding",
                                                                        "lowercase",
                                                                        STOP_FILTER,
                                                                        SYNONYM_FILTER)))));
    }

    private Map<String, Property> buildJobsMappings() {
        return Map.ofEntries(
                Map.entry("id", Property.of(p -> p.keyword(KeywordProperty.of(k -> k)))),
                Map.entry(
                        "title",
                        Property.of(
                                p ->
                                        p.text(
                                                TextProperty.of(
                                                        t ->
                                                                t.analyzer(TEXT_ANALYZER)
                                                                        .searchAnalyzer(
                                                                                SEARCH_ANALYZER)
                                                                        .indexOptions(
                                                                                co.elastic.clients
                                                                                        .elasticsearch
                                                                                        ._types
                                                                                        .mapping
                                                                                        .IndexOptions
                                                                                        .Offsets)
                                                                        .fields(
                                                                                "keyword",
                                                                                Property.of(
                                                                                        f ->
                                                                                                f
                                                                                                        .keyword(
                                                                                                                k ->
                                                                                                                        k
                                                                                                                                .ignoreAbove(
                                                                                                                                        256))))
                                                                        .fields(
                                                                                "suggest",
                                                                                Property.of(
                                                                                        f ->
                                                                                                f
                                                                                                        .completion(
                                                                                                                c ->
                                                                                                                        c
                                                                                                                                .analyzer(
                                                                                                                                        SEARCH_ANALYZER)))))))),
                Map.entry("description", textFieldWithAnalyzer(true)),
                Map.entry("requirements", textFieldWithAnalyzer(true)),
                Map.entry("status", Property.of(p -> p.keyword(KeywordProperty.of(k -> k)))),
                Map.entry("company_id", Property.of(p -> p.keyword(KeywordProperty.of(k -> k)))),
                Map.entry(
                        "company_name",
                        Property.of(
                                p ->
                                        p.text(
                                                TextProperty.of(
                                                        t ->
                                                                t.analyzer(TEXT_ANALYZER)
                                                                        .searchAnalyzer(
                                                                                SEARCH_ANALYZER)
                                                                        .fields(
                                                                                "keyword",
                                                                                Property.of(
                                                                                        f ->
                                                                                                f
                                                                                                        .keyword(
                                                                                                                k ->
                                                                                                                        k
                                                                                                                                .ignoreAbove(
                                                                                                                                        256)))))))),
                Map.entry("category_id", Property.of(p -> p.keyword(KeywordProperty.of(k -> k)))),
                Map.entry(
                        "category_name",
                        Property.of(
                                p ->
                                        p.text(
                                                TextProperty.of(
                                                        t ->
                                                                t.analyzer(TEXT_ANALYZER)
                                                                        .searchAnalyzer(
                                                                                SEARCH_ANALYZER)
                                                                        .fields(
                                                                                "keyword",
                                                                                Property.of(
                                                                                        f ->
                                                                                                f
                                                                                                        .keyword(
                                                                                                                k ->
                                                                                                                        k
                                                                                                                                .ignoreAbove(
                                                                                                                                        256)))))))),
                Map.entry("location_id", Property.of(p -> p.keyword(KeywordProperty.of(k -> k)))),
                Map.entry(
                        "location_name",
                        Property.of(
                                p ->
                                        p.text(
                                                TextProperty.of(
                                                        t ->
                                                                t.analyzer(TEXT_ANALYZER)
                                                                        .searchAnalyzer(
                                                                                SEARCH_ANALYZER)
                                                                        .fields(
                                                                                "keyword",
                                                                                Property.of(
                                                                                        f ->
                                                                                                f
                                                                                                        .keyword(
                                                                                                                k ->
                                                                                                                        k
                                                                                                                                .ignoreAbove(
                                                                                                                                        256)))))))),
                Map.entry("min_salary", Property.of(p -> p.double_(d -> d))),
                Map.entry("max_salary", Property.of(p -> p.double_(d -> d))),
                Map.entry("currency", Property.of(p -> p.keyword(KeywordProperty.of(k -> k)))),
                Map.entry("is_negotiable", Property.of(p -> p.boolean_(b -> b))),
                Map.entry("deadline", Property.of(p -> p.date(d -> d))),
                Map.entry("public_link", Property.of(p -> p.keyword(KeywordProperty.of(k -> k)))),
                Map.entry("created_at", Property.of(p -> p.date(d -> d))),
                Map.entry("updated_at", Property.of(p -> p.date(d -> d))));
    }

    private Map<String, Property> buildCandidatesMappings() {
        return Map.ofEntries(
                Map.entry("id", Property.of(p -> p.keyword(KeywordProperty.of(k -> k)))),
                Map.entry("user_id", Property.of(p -> p.keyword(KeywordProperty.of(k -> k)))),
                Map.entry("headline", textFieldWithAnalyzer(true)),
                Map.entry("summary", textFieldWithAnalyzer(true)),
                Map.entry("desired_position", textFieldWithAnalyzer(false)),
                Map.entry(
                        "desired_position_level",
                        Property.of(p -> p.keyword(KeywordProperty.of(k -> k)))),
                Map.entry("years_of_experience", Property.of(p -> p.short_(s -> s))),
                Map.entry(
                        "skills",
                        Property.of(
                                p ->
                                        p.text(
                                                TextProperty.of(
                                                        t ->
                                                                t.analyzer(TEXT_ANALYZER)
                                                                        .searchAnalyzer(
                                                                                SEARCH_ANALYZER)
                                                                        .fields(
                                                                                "keyword",
                                                                                Property.of(
                                                                                        f ->
                                                                                                f
                                                                                                        .keyword(
                                                                                                                k ->
                                                                                                                        k
                                                                                                                                .ignoreAbove(
                                                                                                                                        100)))))))),
                Map.entry("work_type", Property.of(p -> p.keyword(KeywordProperty.of(k -> k)))),
                Map.entry("desired_salary_min", Property.of(p -> p.long_(l -> l))),
                Map.entry("desired_salary_max", Property.of(p -> p.long_(l -> l))),
                Map.entry(
                        "education_level", Property.of(p -> p.keyword(KeywordProperty.of(k -> k)))),
                Map.entry("education_major", textFieldWithAnalyzer(false)),
                Map.entry("is_open_to_work", Property.of(p -> p.boolean_(b -> b))),
                Map.entry("available_from", Property.of(p -> p.date(d -> d))),
                Map.entry("created_at", Property.of(p -> p.date(d -> d))),
                Map.entry("updated_at", Property.of(p -> p.date(d -> d))));
    }

    private Map<String, Property> buildCompaniesMappings() {
        return Map.of(
                "id",
                Property.of(p -> p.keyword(KeywordProperty.of(k -> k))),
                "name",
                Property.of(
                        p ->
                                p.text(
                                        TextProperty.of(
                                                t ->
                                                        t.analyzer(TEXT_ANALYZER)
                                                                .searchAnalyzer(SEARCH_ANALYZER)
                                                                .fields(
                                                                        "keyword",
                                                                        Property.of(
                                                                                f ->
                                                                                        f.keyword(
                                                                                                k ->
                                                                                                        k
                                                                                                                .ignoreAbove(
                                                                                                                        256))))
                                                                .fields(
                                                                        "suggest",
                                                                        Property.of(
                                                                                f ->
                                                                                        f
                                                                                                .completion(
                                                                                                        c ->
                                                                                                                c
                                                                                                                        .analyzer(
                                                                                                                                SEARCH_ANALYZER))))))),
                "domain",
                Property.of(p -> p.keyword(KeywordProperty.of(k -> k))),
                "website",
                Property.of(p -> p.keyword(KeywordProperty.of(k -> k))),
                "created_at",
                Property.of(p -> p.date(d -> d)),
                "updated_at",
                Property.of(p -> p.date(d -> d)));
    }

    private Property textFieldWithAnalyzer(boolean withOffsets) {
        return Property.of(
                p ->
                        p.text(
                                TextProperty.of(
                                        t -> {
                                            t.analyzer(TEXT_ANALYZER)
                                                    .searchAnalyzer(SEARCH_ANALYZER);
                                            if (withOffsets) {
                                                t.indexOptions(
                                                        co.elastic.clients.elasticsearch._types
                                                                .mapping.IndexOptions.Offsets);
                                            }
                                            return t;
                                        })));
    }

    @FunctionalInterface
    private interface MappingBuilder {
        Map<String, Property> build();
    }
}
