package com.vietrecruit.feature.company.service;

import com.vietrecruit.common.response.SearchPageResponse;
import com.vietrecruit.feature.company.dto.request.CompanySearchRequest;
import com.vietrecruit.feature.company.dto.response.CompanySearchResponse;

public interface CompanySearchService {

    SearchPageResponse<CompanySearchResponse> search(CompanySearchRequest request);
}
