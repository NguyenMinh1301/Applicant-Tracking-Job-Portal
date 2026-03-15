package com.vietrecruit.feature.invitation.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vietrecruit.common.ApiConstants;
import com.vietrecruit.common.base.BaseController;
import com.vietrecruit.common.enums.ApiSuccessCode;
import com.vietrecruit.common.response.ApiResponse;
import com.vietrecruit.feature.invitation.dto.CreateInvitationRequest;
import com.vietrecruit.feature.invitation.dto.InvitationResponse;
import com.vietrecruit.feature.invitation.service.InvitationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.Invitation.ROOT)
@Tag(name = "Invitation Service", description = "Team member invitation endpoints")
public class InvitationController extends BaseController {

    private final InvitationService invitationService;

    @Operation(
            summary = "Create Invitation",
            description = "Invites an HR or INTERVIEWER to join the company")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Invitation created successfully")
    @PreAuthorize("hasAuthority('USER:MANAGE')")
    @PostMapping
    public ResponseEntity<ApiResponse<InvitationResponse>> create(
            @Valid @RequestBody CreateInvitationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                ApiSuccessCode.INVITATION_CREATE_SUCCESS,
                                invitationService.create(request)));
    }
}
