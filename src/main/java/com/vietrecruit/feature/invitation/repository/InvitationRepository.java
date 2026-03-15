package com.vietrecruit.feature.invitation.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vietrecruit.feature.invitation.entity.Invitation;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByToken(String token);

    boolean existsByEmailAndCompanyIdAndStatus(String email, UUID companyId, String status);
}
