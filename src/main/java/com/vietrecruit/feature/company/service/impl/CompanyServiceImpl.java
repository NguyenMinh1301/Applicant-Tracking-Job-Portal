package com.vietrecruit.feature.company.service.impl;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vietrecruit.common.config.cache.CacheEventPublisher;
import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.common.response.PageResponse;
import com.vietrecruit.common.security.SecurityUtils;
import com.vietrecruit.feature.company.dto.request.CompanyCreateRequest;
import com.vietrecruit.feature.company.dto.request.CompanyUpdateRequest;
import com.vietrecruit.feature.company.dto.response.CompanyMemberResponse;
import com.vietrecruit.feature.company.dto.response.CompanyResponse;
import com.vietrecruit.feature.company.entity.Company;
import com.vietrecruit.feature.company.mapper.CompanyMapper;
import com.vietrecruit.feature.company.mapper.CompanyMemberMapper;
import com.vietrecruit.feature.company.repository.CompanyMemberSpecification;
import com.vietrecruit.feature.company.repository.CompanyRepository;
import com.vietrecruit.feature.company.service.CompanyService;
import com.vietrecruit.feature.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyServiceImpl implements CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;
    private final CompanyMemberMapper companyMemberMapper;
    private final CacheEventPublisher cacheEventPublisher;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CompanyResponse createCompany(UUID userId, CompanyCreateRequest request) {
        var user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> new ApiException(ApiErrorCode.NOT_FOUND, "User not found"));

        if (user.getCompanyId() != null) {
            throw new ApiException(
                    ApiErrorCode.CONFLICT, "User is already associated with a company");
        }

        if (request.getDomain() != null && companyRepository.existsByDomain(request.getDomain())) {
            throw new ApiException(ApiErrorCode.CONFLICT, "Domain is already in use");
        }

        var company = companyMapper.toEntity(request);
        var saved = companyRepository.save(company);

        // Link the company to the user
        user.setCompanyId(saved.getId());
        userRepository.save(user);

        return companyMapper.toResponse(saved);
    }

    @Override
    @org.springframework.cache.annotation.Cacheable(
            value = com.vietrecruit.common.config.cache.CacheNames.COMPANY_DETAIL,
            key = "#companyId")
    public CompanyResponse getCompany(UUID companyId) {
        return companyMapper.toResponse(findActiveCompany(companyId));
    }

    @Override
    @Transactional
    public CompanyResponse updateCompany(UUID companyId, CompanyUpdateRequest request) {
        var company = findActiveCompany(companyId);

        if (request.getDomain() != null
                && companyRepository.existsByDomainAndIdNot(request.getDomain(), companyId)) {
            throw new ApiException(ApiErrorCode.CONFLICT, "Domain is already in use");
        }

        companyMapper.updateEntity(request, company);
        var saved = companyRepository.save(company);
        cacheEventPublisher.publish("company", "updated", companyId, null);
        return companyMapper.toResponse(saved);
    }

    @Override
    public PageResponse<CompanyMemberResponse> getCompanyMembers(
            int page, int size, String roleFilter, String search) {
        UUID companyId = resolveCallerCompanyId();

        int clampedSize = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, clampedSize);

        var spec = CompanyMemberSpecification.buildSpecification(companyId, roleFilter, search);
        var userPage = userRepository.findAll(spec, pageable);

        var memberResponses =
                userPage.getContent().stream().map(companyMemberMapper::toResponse).toList();

        return PageResponse.<CompanyMemberResponse>builder()
                .content(memberResponses)
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .first(userPage.isFirst())
                .last(userPage.isLast())
                .empty(userPage.isEmpty())
                .build();
    }

    private UUID resolveCallerCompanyId() {
        UUID userId = SecurityUtils.getCurrentUserId();
        var user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ApiErrorCode.UNAUTHORIZED, "User not found"));

        if (user.getCompanyId() == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "User has no company");
        }

        return user.getCompanyId();
    }

    private Company findActiveCompany(UUID companyId) {
        return companyRepository
                .findByIdAndDeletedAtIsNull(companyId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "Company not found"));
    }
}
