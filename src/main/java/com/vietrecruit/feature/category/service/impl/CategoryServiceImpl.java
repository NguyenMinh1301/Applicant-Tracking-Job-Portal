package com.vietrecruit.feature.category.service.impl;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vietrecruit.common.config.cache.CacheEventPublisher;
import com.vietrecruit.common.config.cache.CacheNames;
import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.category.dto.request.CategoryRequest;
import com.vietrecruit.feature.category.dto.response.CategoryResponse;
import com.vietrecruit.feature.category.entity.Category;
import com.vietrecruit.feature.category.mapper.CategoryMapper;
import com.vietrecruit.feature.category.repository.CategoryRepository;
import com.vietrecruit.feature.category.service.CategoryService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final CacheEventPublisher cacheEventPublisher;

    @Override
    @Transactional
    public CategoryResponse createCategory(UUID companyId, UUID userId, CategoryRequest request) {
        if (categoryRepository.existsByCompanyIdAndName(companyId, request.getName())) {
            throw new ApiException(ApiErrorCode.CONFLICT, "Category with this name already exists");
        }

        var category = categoryMapper.toEntity(request);
        category.setCompanyId(companyId);
        category.setCreatedBy(userId);
        category.setUpdatedBy(userId);
        var saved = categoryRepository.save(category);
        cacheEventPublisher.publish("category", "created", saved.getId(), companyId);
        return categoryMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(
            UUID companyId, UUID categoryId, UUID userId, CategoryRequest request) {
        var category = findByIdAndCompany(categoryId, companyId);

        if (categoryRepository.existsByCompanyIdAndNameAndIdNot(
                companyId, request.getName(), categoryId)) {
            throw new ApiException(ApiErrorCode.CONFLICT, "Category with this name already exists");
        }

        categoryMapper.updateEntity(request, category);
        category.setUpdatedBy(userId);
        var saved = categoryRepository.save(category);
        cacheEventPublisher.publish("category", "updated", saved.getId(), companyId);
        return categoryMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(
            value = CacheNames.CATEGORY_DETAIL,
            key = "#companyId + '::' + #categoryId")
    public CategoryResponse getCategory(UUID companyId, UUID categoryId) {
        return categoryMapper.toResponse(findByIdAndCompany(categoryId, companyId));
    }

    @Override
    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(
            value = CacheNames.CATEGORY_LIST,
            key = "#companyId")
    public Page<CategoryResponse> listCategories(UUID companyId, Pageable pageable) {
        return categoryRepository
                .findByCompanyId(companyId, pageable)
                .map(categoryMapper::toResponse);
    }

    @Override
    @Transactional
    public void deleteCategory(UUID companyId, UUID categoryId) {
        var category = findByIdAndCompany(categoryId, companyId);
        try {
            categoryRepository.delete(category);
            categoryRepository.flush();
            cacheEventPublisher.publish("category", "deleted", categoryId, companyId);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(
                    ApiErrorCode.BAD_REQUEST,
                    "Cannot delete category — it is referenced by existing jobs");
        }
    }

    private Category findByIdAndCompany(UUID categoryId, UUID companyId) {
        return categoryRepository
                .findByIdAndCompanyId(categoryId, companyId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "Category not found"));
    }
}
