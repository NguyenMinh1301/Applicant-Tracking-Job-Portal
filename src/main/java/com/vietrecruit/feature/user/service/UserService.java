package com.vietrecruit.feature.user.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.vietrecruit.feature.user.dto.request.UserRequest;
import com.vietrecruit.feature.user.dto.response.UserResponse;

public interface UserService {
    UserResponse create(UserRequest request);

    UserResponse get(Integer id);

    Page<UserResponse> list(Pageable pageable);

    UserResponse update(Integer id, UserRequest request);

    void delete(Integer id);
}
