package com.vietrecruit.common.config.elasticsearch;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ElasticsearchConstants {

    public static final String INDEX_PREFIX = "vietrecruit_";

    // Index names
    public static final String INDEX_JOBS = INDEX_PREFIX + "jobs";
    public static final String INDEX_CANDIDATES = INDEX_PREFIX + "candidates";
    public static final String INDEX_COMPANIES = INDEX_PREFIX + "companies";

    // Analyzer names
    public static final String TEXT_ANALYZER = "vietrecruit_text_analyzer";
    public static final String SEARCH_ANALYZER = "vietrecruit_search_analyzer";

    // Token filter names
    public static final String EDGE_NGRAM_FILTER = "vietrecruit_edge_ngram";
    public static final String STOP_FILTER = "vietrecruit_stop";
    public static final String SYNONYM_FILTER = "vietrecruit_synonym";
}
