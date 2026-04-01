package com.vietrecruit.feature.user.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.vietrecruit.feature.user.dto.request.UserRequest;
import com.vietrecruit.feature.user.dto.response.AdminUserResponse;

public interface AdminUserService {

    /**
     * Creates a new admin-managed user account with the specified role and credentials.
     *
     * @param request user creation payload including email, role, and initial password
     * @return the created user response
     */
    AdminUserResponse create(UserRequest request);

    /**
     * Returns a single user account by its UUID.
     *
     * @param id the target user's UUID
     * @return the user response
     */
    AdminUserResponse get(UUID id);

    /**
     * Returns a paginated list of all user accounts.
     *
     * @param pageable pagination and sort parameters
     * @return page of user responses
     */
    Page<AdminUserResponse> list(Pageable pageable);

    /**
     * Updates the mutable fields of an existing user account.
     *
     * @param id the target user's UUID
     * @param request updated user fields
     * @return the updated user response
     */
    AdminUserResponse update(UUID id, UserRequest request);

    /**
     * Permanently deletes a user account by its UUID.
     *
     * @param id the target user's UUID
     */
    void delete(UUID id);
}
