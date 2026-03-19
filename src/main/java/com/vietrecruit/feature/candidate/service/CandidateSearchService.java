package com.vietrecruit.feature.candidate.service;

import com.vietrecruit.common.response.SearchPageResponse;
import com.vietrecruit.feature.candidate.dto.request.CandidateSearchRequest;
import com.vietrecruit.feature.candidate.dto.response.CandidateSearchResponse;

public interface CandidateSearchService {

    SearchPageResponse<CandidateSearchResponse> search(CandidateSearchRequest request);
}
