package com.vietrecruit.feature.user.service.impl;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.user.dto.request.UserRequest;
import com.vietrecruit.feature.user.dto.response.AdminUserResponse;
import com.vietrecruit.feature.user.entity.User;
import com.vietrecruit.feature.user.mapper.UserMapper;
import com.vietrecruit.feature.user.repository.UserRepository;
import com.vietrecruit.feature.user.service.AdminUserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public AdminUserResponse create(UserRequest request) {
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new ApiException(ApiErrorCode.USER_EMAIL_CONFLICT);
        }
        User user = userMapper.toEntity(request);
        user = userRepository.save(user);
        return userMapper.toAdminResponse(user);
    }

    @Override
    public AdminUserResponse get(UUID id) {
        User user =
                userRepository
                        .findByIdWithRolesAndPermissions(id)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
        return userMapper.toAdminResponse(user);
    }

    @Override
    public Page<AdminUserResponse> list(Pageable pageable) {
        return userRepository.findAll(pageable).map(userMapper::toAdminResponse);
    }

    @Override
    @Transactional
    public AdminUserResponse update(UUID id, UserRequest request) {
        User user =
                userRepository
                        .findById(id)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));

        if (request.getEmail() != null
                && !user.getEmail().equals(request.getEmail())
                && userRepository.existsByEmail(request.getEmail())) {
            throw new ApiException(ApiErrorCode.USER_EMAIL_CONFLICT);
        }

        userMapper.updateEntity(user, request);
        user = userRepository.save(user);
        return userMapper.toAdminResponse(user);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND);
        }
        userRepository.deleteById(id);
    }
}
