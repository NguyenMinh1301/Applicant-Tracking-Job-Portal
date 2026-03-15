package com.vietrecruit.feature.invitation.service;

import com.vietrecruit.feature.invitation.dto.CreateInvitationRequest;
import com.vietrecruit.feature.invitation.dto.InvitationResponse;

public interface InvitationService {

    InvitationResponse create(CreateInvitationRequest request);
}
