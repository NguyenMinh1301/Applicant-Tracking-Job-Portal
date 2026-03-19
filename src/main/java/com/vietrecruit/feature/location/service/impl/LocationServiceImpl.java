package com.vietrecruit.feature.location.service.impl;

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
import com.vietrecruit.feature.location.dto.request.LocationRequest;
import com.vietrecruit.feature.location.dto.response.LocationResponse;
import com.vietrecruit.feature.location.entity.Location;
import com.vietrecruit.feature.location.mapper.LocationMapper;
import com.vietrecruit.feature.location.repository.LocationRepository;
import com.vietrecruit.feature.location.service.LocationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private final LocationRepository locationRepository;
    private final LocationMapper locationMapper;
    private final CacheEventPublisher cacheEventPublisher;

    @Override
    @Transactional
    public LocationResponse createLocation(UUID companyId, UUID userId, LocationRequest request) {
        if (locationRepository.existsByCompanyIdAndName(companyId, request.getName())) {
            throw new ApiException(ApiErrorCode.CONFLICT, "Location with this name already exists");
        }

        var location = locationMapper.toEntity(request);
        location.setCompanyId(companyId);
        location.setCreatedBy(userId);
        location.setUpdatedBy(userId);
        var saved = locationRepository.save(location);
        cacheEventPublisher.publish("location", "created", saved.getId(), companyId);
        return locationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public LocationResponse updateLocation(
            UUID companyId, UUID locationId, UUID userId, LocationRequest request) {
        var location = findByIdAndCompany(locationId, companyId);

        if (locationRepository.existsByCompanyIdAndNameAndIdNot(
                companyId, request.getName(), locationId)) {
            throw new ApiException(ApiErrorCode.CONFLICT, "Location with this name already exists");
        }

        locationMapper.updateEntity(request, location);
        location.setUpdatedBy(userId);
        var saved = locationRepository.save(location);
        cacheEventPublisher.publish("location", "updated", saved.getId(), companyId);
        return locationMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(
            value = CacheNames.LOCATION_DETAIL,
            key = "#companyId + '::' + #locationId")
    public LocationResponse getLocation(UUID companyId, UUID locationId) {
        return locationMapper.toResponse(findByIdAndCompany(locationId, companyId));
    }

    @Override
    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(
            value = CacheNames.LOCATION_LIST,
            key = "#companyId")
    public Page<LocationResponse> listLocations(UUID companyId, Pageable pageable) {
        return locationRepository
                .findByCompanyId(companyId, pageable)
                .map(locationMapper::toResponse);
    }

    @Override
    @Transactional
    public void deleteLocation(UUID companyId, UUID locationId) {
        var location = findByIdAndCompany(locationId, companyId);
        try {
            locationRepository.delete(location);
            locationRepository.flush();
            cacheEventPublisher.publish("location", "deleted", locationId, companyId);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(
                    ApiErrorCode.BAD_REQUEST,
                    "Cannot delete location — it is referenced by existing jobs");
        }
    }

    private Location findByIdAndCompany(UUID locationId, UUID companyId) {
        return locationRepository
                .findByIdAndCompanyId(locationId, companyId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "Location not found"));
    }
}
