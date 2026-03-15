package com.vietrecruit.feature.invitation.service.impl;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.enums.EmailSenderAlias;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.common.security.SecurityUtils;
import com.vietrecruit.feature.invitation.dto.CreateInvitationRequest;
import com.vietrecruit.feature.invitation.dto.InvitationResponse;
import com.vietrecruit.feature.invitation.entity.Invitation;
import com.vietrecruit.feature.invitation.repository.InvitationRepository;
import com.vietrecruit.feature.invitation.service.InvitationService;
import com.vietrecruit.feature.notification.dto.EmailRequest;
import com.vietrecruit.feature.notification.service.NotificationService;
import com.vietrecruit.feature.user.entity.User;
import com.vietrecruit.feature.user.repository.RoleRepository;
import com.vietrecruit.feature.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvitationServiceImpl implements InvitationService {

    private static final Set<String> INVITABLE_ROLES = Set.of("HR", "INTERVIEWER");
    private static final long INVITATION_TTL_DAYS = 7;
    private static final int TOKEN_BYTES = 32;

    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final NotificationService notificationService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${spring.application.frontend-url}")
    private String frontendBaseUrl;

    @Override
    @Transactional
    public InvitationResponse create(CreateInvitationRequest request) {
        String roleCode = request.getRole().trim().toUpperCase();
        if (!INVITABLE_ROLES.contains(roleCode)) {
            throw new ApiException(ApiErrorCode.INVALID_INVITATION_ROLE);
        }

        roleRepository
                .findByCode(roleCode)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        ApiErrorCode.INTERNAL_ERROR,
                                        "Role not found: " + roleCode));

        java.util.UUID currentUserId = SecurityUtils.getCurrentUserId();
        User inviter =
                userRepository
                        .findById(currentUserId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.UNAUTHORIZED));

        if (inviter.getCompanyId() == null) {
            throw new ApiException(
                    ApiErrorCode.FORBIDDEN, "You must belong to a company to send invitations");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApiException(ApiErrorCode.USER_EMAIL_CONFLICT);
        }

        String token = generateToken();
        Instant expiresAt = Instant.now().plus(INVITATION_TTL_DAYS, ChronoUnit.DAYS);

        Invitation invitation =
                Invitation.builder()
                        .companyId(inviter.getCompanyId())
                        .email(request.getEmail())
                        .role(roleCode)
                        .token(token)
                        .status("PENDING")
                        .expiresAt(expiresAt)
                        .createdBy(currentUserId)
                        .createdAt(Instant.now())
                        .build();

        invitationRepository.save(invitation);

        String inviteLink = String.format("%s/register/invite?token=%s", frontendBaseUrl, token);

        notificationService.send(
                new EmailRequest(
                        List.of(request.getEmail()),
                        EmailSenderAlias.AUTHENTICATION,
                        "You've Been Invited to Join VietRecruit",
                        null,
                        "team-invitation",
                        Map.of(
                                "inviterName", inviter.getFullName(),
                                "role", roleCode,
                                "inviteLink", inviteLink)));

        log.info(
                "Invitation created: email={}, role={}, companyId={}, invitedBy={}",
                request.getEmail(),
                roleCode,
                inviter.getCompanyId(),
                currentUserId);

        return InvitationResponse.builder()
                .invitationId(invitation.getId())
                .expiresAt(expiresAt)
                .build();
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }
}
