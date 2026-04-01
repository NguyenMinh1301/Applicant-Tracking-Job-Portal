package com.vietrecruit.feature.job.service;

import java.util.List;

import com.vietrecruit.common.response.SearchPageResponse;
import com.vietrecruit.feature.job.dto.request.JobSearchRequest;
import com.vietrecruit.feature.job.dto.response.JobSearchResponse;

public interface JobSearchService {

    /**
     * Executes a full-text job search against Elasticsearch using the provided filters.
     *
     * @param request search parameters including keywords, location, category, salary range, and
     *     pagination
     * @return paginated search results with relevance scores
     */
    SearchPageResponse<JobSearchResponse> search(JobSearchRequest request);

    /**
     * Returns job title suggestions matching the given query prefix, used for search autocomplete.
     *
     * @param query the partial job title string to match
     * @param limit maximum number of suggestions to return
     * @return list of matching job title strings
     */
    List<String> autocomplete(String query, int limit);
}
