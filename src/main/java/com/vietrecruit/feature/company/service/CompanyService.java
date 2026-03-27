package com.vietrecruit.feature.company.service;

import java.util.UUID;

import com.vietrecruit.feature.company.dto.request.CompanyCreateRequest;
import com.vietrecruit.feature.company.dto.request.CompanyUpdateRequest;
import com.vietrecruit.feature.company.dto.response.CompanyResponse;

public interface CompanyService {

    CompanyResponse createCompany(UUID userId, CompanyCreateRequest request);

    CompanyResponse getCompany(UUID companyId);

    CompanyResponse updateCompany(UUID companyId, CompanyUpdateRequest request);
}
