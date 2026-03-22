package com.vietrecruit.feature.application.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.application.dto.request.OfferRespondRequest;
import com.vietrecruit.feature.application.dto.response.OfferResponse;
import com.vietrecruit.feature.application.entity.Application;
import com.vietrecruit.feature.application.entity.Offer;
import com.vietrecruit.feature.application.enums.ApplicationStatus;
import com.vietrecruit.feature.application.enums.OfferStatus;
import com.vietrecruit.feature.application.mapper.OfferMapper;
import com.vietrecruit.feature.application.repository.ApplicationRepository;
import com.vietrecruit.feature.application.repository.OfferRepository;
import com.vietrecruit.feature.application.service.ApplicationService;
import com.vietrecruit.feature.candidate.entity.Candidate;
import com.vietrecruit.feature.candidate.repository.CandidateRepository;
import com.vietrecruit.feature.job.repository.JobRepository;
import com.vietrecruit.feature.notification.service.NotificationService;
import com.vietrecruit.feature.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class OfferServiceImplTest {

    @Mock private OfferRepository offerRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApplicationService applicationService;
    @Mock private JobRepository jobRepository;
    @Mock private CandidateRepository candidateRepository;
    @Mock private UserRepository userRepository;
    @Mock private OfferMapper offerMapper;
    @Mock private NotificationService notificationService;

    @InjectMocks private OfferServiceImpl offerService;

    private UUID offerId;
    private UUID userId;
    private UUID applicationId;
    private UUID candidateId;
    private Offer sentOffer;
    private Application application;
    private Candidate candidate;

    @BeforeEach
    void setUp() {
        offerId = UUID.randomUUID();
        userId = UUID.randomUUID();
        applicationId = UUID.randomUUID();
        candidateId = UUID.randomUUID();

        sentOffer =
                Offer.builder()
                        .id(offerId)
                        .applicationId(applicationId)
                        .status(OfferStatus.SENT)
                        .baseSalary(BigDecimal.valueOf(30_000_000))
                        .currency("VND")
                        .build();

        application =
                Application.builder()
                        .id(applicationId)
                        .candidateId(candidateId)
                        .jobId(UUID.randomUUID())
                        .status(ApplicationStatus.OFFER)
                        .build();

        candidate = Candidate.builder().id(candidateId).userId(userId).build();
    }

    // ── Scenario 1 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Accept offer when SENT → offer status ACCEPTED, application HIRED")
    void respondToOffer_accept_whenSent_succeeds() {
        var request = OfferRespondRequest.builder().action("ACCEPT").build();
        var expectedResponse = mock(OfferResponse.class);

        when(offerRepository.findByIdAndDeletedAtIsNull(offerId))
                .thenReturn(Optional.of(sentOffer));
        when(applicationRepository.findByIdAndDeletedAtIsNull(applicationId))
                .thenReturn(Optional.of(application));
        when(candidateRepository.findByUserIdAndDeletedAtIsNull(userId))
                .thenReturn(Optional.of(candidate));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(offerMapper.toResponse(any(Offer.class))).thenReturn(expectedResponse);
        // suppress notification side effects
        when(jobRepository.findById(application.getJobId())).thenReturn(Optional.empty());

        var result = offerService.respondToOffer(offerId, userId, request);

        assertNotNull(result);
        assertEquals(OfferStatus.ACCEPTED, sentOffer.getStatus());
        assertEquals(ApplicationStatus.HIRED, application.getStatus());
        verify(applicationService)
                .insertHistory(eq(applicationId), any(), eq(ApplicationStatus.HIRED), any(), any());
    }

    // ── Scenario 2 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Decline offer when SENT → offer status DECLINED, application REJECTED")
    void respondToOffer_decline_whenSent_succeeds() {
        var request = OfferRespondRequest.builder().action("DECLINE").build();

        when(offerRepository.findByIdAndDeletedAtIsNull(offerId))
                .thenReturn(Optional.of(sentOffer));
        when(applicationRepository.findByIdAndDeletedAtIsNull(applicationId))
                .thenReturn(Optional.of(application));
        when(candidateRepository.findByUserIdAndDeletedAtIsNull(userId))
                .thenReturn(Optional.of(candidate));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(offerMapper.toResponse(any(Offer.class))).thenReturn(mock(OfferResponse.class));
        when(jobRepository.findById(application.getJobId())).thenReturn(Optional.empty());

        offerService.respondToOffer(offerId, userId, request);

        assertEquals(OfferStatus.DECLINED, sentOffer.getStatus());
        assertEquals(ApplicationStatus.REJECTED, application.getStatus());
        verify(applicationService)
                .insertHistory(
                        eq(applicationId), any(), eq(ApplicationStatus.REJECTED), any(), any());
    }

    // ── Scenario 3 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Accept offer when already ACCEPTED (not SENT) → OFFER_INVALID_TRANSITION")
    void respondToOffer_acceptWhenAlreadyAccepted_throwsOfferInvalidTransition() {
        sentOffer.setStatus(OfferStatus.ACCEPTED);
        var request = OfferRespondRequest.builder().action("ACCEPT").build();

        when(offerRepository.findByIdAndDeletedAtIsNull(offerId))
                .thenReturn(Optional.of(sentOffer));

        ApiException ex =
                assertThrows(
                        ApiException.class,
                        () -> offerService.respondToOffer(offerId, userId, request));

        assertEquals(ApiErrorCode.OFFER_INVALID_TRANSITION, ex.getErrorCode());
        verify(offerRepository, never()).save(any());
    }

    // ── Scenario 4 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName(
            "Concurrent accept (ObjectOptimisticLockingFailureException) → CONCURRENT_MODIFICATION")
    void respondToOffer_concurrentAccept_throwsConcurrentModification() {
        var request = OfferRespondRequest.builder().action("ACCEPT").build();

        when(offerRepository.findByIdAndDeletedAtIsNull(offerId))
                .thenReturn(Optional.of(sentOffer));
        when(applicationRepository.findByIdAndDeletedAtIsNull(applicationId))
                .thenReturn(Optional.of(application));
        when(candidateRepository.findByUserIdAndDeletedAtIsNull(userId))
                .thenReturn(Optional.of(candidate));
        when(offerRepository.save(any(Offer.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Offer.class, offerId));

        ApiException ex =
                assertThrows(
                        ApiException.class,
                        () -> offerService.respondToOffer(offerId, userId, request));

        assertEquals(ApiErrorCode.CONCURRENT_MODIFICATION, ex.getErrorCode());
        verify(applicationRepository, never()).save(any());
    }

    // ── Caller is not the candidate ─────────────────────────────────────────

    @Test
    @DisplayName("Caller is not the candidate → FORBIDDEN")
    void respondToOffer_callerIsNotCandidate_throwsForbidden() {
        UUID otherCandidateId = UUID.randomUUID();
        Candidate otherCandidate = Candidate.builder().id(otherCandidateId).userId(userId).build();
        var request = OfferRespondRequest.builder().action("ACCEPT").build();

        when(offerRepository.findByIdAndDeletedAtIsNull(offerId))
                .thenReturn(Optional.of(sentOffer));
        when(applicationRepository.findByIdAndDeletedAtIsNull(applicationId))
                .thenReturn(Optional.of(application)); // candidateId = candidateId (different)
        when(candidateRepository.findByUserIdAndDeletedAtIsNull(userId))
                .thenReturn(Optional.of(otherCandidate)); // returns different candidate

        ApiException ex =
                assertThrows(
                        ApiException.class,
                        () -> offerService.respondToOffer(offerId, userId, request));

        assertEquals(ApiErrorCode.FORBIDDEN, ex.getErrorCode());
    }
}
