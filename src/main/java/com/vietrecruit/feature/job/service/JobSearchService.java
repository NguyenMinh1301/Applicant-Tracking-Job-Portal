package com.vietrecruit.feature.job.service;

import java.util.List;

import com.vietrecruit.common.response.SearchPageResponse;
import com.vietrecruit.feature.job.dto.request.JobSearchRequest;
import com.vietrecruit.feature.job.dto.response.JobSearchResponse;

public interface JobSearchService {

    SearchPageResponse<JobSearchResponse> search(JobSearchRequest request);

    List<String> autocomplete(String query, int limit);
}
