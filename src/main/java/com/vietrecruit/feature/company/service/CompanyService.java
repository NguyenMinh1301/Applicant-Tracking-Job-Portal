package com.vietrecruit.feature.company.service;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;

import com.vietrecruit.common.response.PageResponse;
import com.vietrecruit.feature.company.dto.request.CompanyCreateRequest;
import com.vietrecruit.feature.company.dto.request.CompanyUpdateRequest;
import com.vietrecruit.feature.company.dto.response.CompanyMemberResponse;
import com.vietrecruit.feature.company.dto.response.CompanyResponse;

public interface CompanyService {

    /**
     * Creates a new company profile associated with the given user.
     *
     * @param userId the user UUID who is registering as a company admin
     * @param request company details including name, industry, and contact information
     * @return the created company response
     */
    CompanyResponse createCompany(UUID userId, CompanyCreateRequest request);

    /**
     * Returns the company profile for the given company UUID.
     *
     * @param companyId the target company's UUID
     * @return the company response
     */
    CompanyResponse getCompany(UUID companyId);

    /**
     * Updates the mutable fields of an existing company profile.
     *
     * @param companyId the target company's UUID
     * @param request updated company fields
     * @return the updated company response
     */
    CompanyResponse updateCompany(UUID companyId, CompanyUpdateRequest request);

    /**
     * Returns a paginated list of all users belonging to the authenticated caller's company.
     *
     * @param page zero-indexed page number
     * @param size page size (max 50)
     * @param roleFilter optional role filter
     * @param search optional search term for fullName or email
     * @return paginated list of company members
     */
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR')")
    PageResponse<CompanyMemberResponse> getCompanyMembers(
            int page, int size, String roleFilter, String search);
}
