package com.vietrecruit.feature.user.controller;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vietrecruit.common.ApiConstants;
import com.vietrecruit.common.base.BaseController;
import com.vietrecruit.common.response.ApiResponse;
import com.vietrecruit.common.response.ApiSuccessCode;
import com.vietrecruit.common.response.PageResponse;
import com.vietrecruit.feature.user.dto.request.UserRequest;
import com.vietrecruit.feature.user.dto.response.UserResponse;
import com.vietrecruit.feature.user.service.UserService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.User.ROOT)
public class UserController extends BaseController {

    private final UserService userService;

    @PostMapping(ApiConstants.User.CREATE)
    public ApiResponse<UserResponse> create(@Valid @RequestBody UserRequest request) {
        return ApiResponse.success(ApiSuccessCode.USER_CREATE_SUCCESS, userService.create(request));
    }

    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @GetMapping(ApiConstants.User.GET)
    public ApiResponse<UserResponse> get(@PathVariable Integer id) {
        return ApiResponse.success(ApiSuccessCode.USER_FETCH_SUCCESS, userService.get(id));
    }

    @GetMapping
    public ApiResponse<PageResponse<UserResponse>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(
                ApiSuccessCode.USER_LIST_SUCCESS, PageResponse.from(userService.list(pageable)));
    }

    @PutMapping(ApiConstants.User.UPDATE)
    public ApiResponse<UserResponse> update(
            @PathVariable Integer id, @Valid @RequestBody UserRequest request) {
        return ApiResponse.success(
                ApiSuccessCode.USER_UPDATE_SUCCESS, userService.update(id, request));
    }

    @DeleteMapping(ApiConstants.User.DELETE)
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        userService.delete(id);
        return ApiResponse.success(ApiSuccessCode.USER_DELETE_SUCCESS);
    }
}
