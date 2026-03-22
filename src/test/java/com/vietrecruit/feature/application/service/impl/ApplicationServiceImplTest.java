package com.vietrecruit.feature.application.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
import com.vietrecruit.feature.application.dto.request.ApplicationStatusUpdateRequest;
import com.vietrecruit.feature.application.dto.response.ApplicationResponse;
import com.vietrecruit.feature.application.entity.Application;
import com.vietrecruit.feature.application.enums.ApplicationStatus;
import com.vietrecruit.feature.application.mapper.ApplicationMapper;
import com.vietrecruit.feature.application.repository.ApplicationRepository;
import com.vietrecruit.feature.application.repository.ApplicationStatusHistoryRepository;
import com.vietrecruit.feature.candidate.repository.CandidateRepository;
import com.vietrecruit.feature.job.repository.JobRepository;
import com.vietrecruit.feature.notification.service.NotificationService;
import com.vietrecruit.feature.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceImplTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApplicationStatusHistoryRepository historyRepository;
    @Mock private JobRepository jobRepository;
    @Mock private CandidateRepository candidateRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationMapper applicationMapper;
    @Mock private NotificationService notificationService;

    @InjectMocks private ApplicationServiceImpl applicationService;

    private UUID applicationId;
    private UUID companyId;
    private UUID userId;
    private Application application;

    @BeforeEach
    void setUp() {
        applicationId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        userId = UUID.randomUUID();
        application =
                Application.builder()
                        .id(applicationId)
                        .jobId(UUID.randomUUID())
                        .candidateId(UUID.randomUUID())
                        .status(ApplicationStatus.NEW)
                        .build();
    }

    // ── Scenario 1 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid transition NEW → SCREENING succeeds")
    void updateStatus_validTransition_newToScreening_succeeds() {
        var request =
                ApplicationStatusUpdateRequest.builder()
                        .status(ApplicationStatus.SCREENING)
                        .notes("Moving to screening")
                        .build();

        when(applicationRepository.findByIdAndCompanyId(applicationId, companyId))
                .thenReturn(Optional.of(application));
        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        var response = mock(ApplicationResponse.class);
        when(applicationMapper.toResponse(any(Application.class))).thenReturn(response);
        when(jobRepository.findById(application.getJobId())).thenReturn(Optional.empty());
        when(candidateRepository.findByIdAndDeletedAtIsNull(application.getCandidateId()))
                .thenReturn(Optional.empty());

        var result = applicationService.updateStatus(applicationId, companyId, userId, request);

        assertNotNull(result);
        assertEquals(ApplicationStatus.SCREENING, application.getStatus());
        verify(applicationRepository).save(application);
        verify(historyRepository).save(any());
    }

    // ── Scenario 2 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Invalid transition NEW → OFFER throws APPLICATION_INVALID_TRANSITION")
    void updateStatus_invalidTransition_newToOffer_throwsApiException() {
        var request =
                ApplicationStatusUpdateRequest.builder().status(ApplicationStatus.OFFER).build();

        when(applicationRepository.findByIdAndCompanyId(applicationId, companyId))
                .thenReturn(Optional.of(application));

        ApiException ex =
                assertThrows(
                        ApiException.class,
                        () ->
                                applicationService.updateStatus(
                                        applicationId, companyId, userId, request));

        assertEquals(ApiErrorCode.APPLICATION_INVALID_TRANSITION, ex.getErrorCode());
        verify(applicationRepository, never()).save(any());
    }

    // ── Scenario 3 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Invalid transition NEW → HIRED throws APPLICATION_INVALID_TRANSITION")
    void updateStatus_invalidTransition_newToHired_throwsApiException() {
        var request =
                ApplicationStatusUpdateRequest.builder().status(ApplicationStatus.HIRED).build();

        when(applicationRepository.findByIdAndCompanyId(applicationId, companyId))
                .thenReturn(Optional.of(application));

        ApiException ex =
                assertThrows(
                        ApiException.class,
                        () ->
                                applicationService.updateStatus(
                                        applicationId, companyId, userId, request));

        assertEquals(ApiErrorCode.APPLICATION_INVALID_TRANSITION, ex.getErrorCode());
        verify(applicationRepository, never()).save(any());
    }

    // ── Scenario 4 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName(
            "Concurrent update (ObjectOptimisticLockingFailureException) → CONCURRENT_MODIFICATION")
    void updateStatus_concurrentUpdate_throwsConcurrentModification() {
        var request =
                ApplicationStatusUpdateRequest.builder()
                        .status(ApplicationStatus.SCREENING)
                        .build();

        when(applicationRepository.findByIdAndCompanyId(applicationId, companyId))
                .thenReturn(Optional.of(application));
        when(applicationRepository.save(any(Application.class)))
                .thenThrow(
                        new ObjectOptimisticLockingFailureException(
                                Application.class, applicationId));

        ApiException ex =
                assertThrows(
                        ApiException.class,
                        () ->
                                applicationService.updateStatus(
                                        applicationId, companyId, userId, request));

        assertEquals(ApiErrorCode.CONCURRENT_MODIFICATION, ex.getErrorCode());
    }

    // ── Scenario 5 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Wrong company ID → APPLICATION_NOT_FOUND (access control via repository query)")
    void updateStatus_wrongCompany_throwsApplicationNotFound() {
        UUID otherCompany = UUID.randomUUID();
        var request =
                ApplicationStatusUpdateRequest.builder()
                        .status(ApplicationStatus.SCREENING)
                        .build();

        when(applicationRepository.findByIdAndCompanyId(applicationId, otherCompany))
                .thenReturn(Optional.empty());

        ApiException ex =
                assertThrows(
                        ApiException.class,
                        () ->
                                applicationService.updateStatus(
                                        applicationId, otherCompany, userId, request));

        assertEquals(ApiErrorCode.APPLICATION_NOT_FOUND, ex.getErrorCode());
        verify(applicationRepository, never()).save(any());
    }

    // ── SCREENING → INTERVIEW valid transition ──────────────────────────────

    @Test
    @DisplayName("Valid transition SCREENING → INTERVIEW succeeds")
    void updateStatus_validTransition_screeningToInterview_succeeds() {
        application.setStatus(ApplicationStatus.SCREENING);
        var request =
                ApplicationStatusUpdateRequest.builder()
                        .status(ApplicationStatus.INTERVIEW)
                        .build();

        when(applicationRepository.findByIdAndCompanyId(applicationId, companyId))
                .thenReturn(Optional.of(application));
        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(applicationMapper.toResponse(any(Application.class)))
                .thenReturn(mock(ApplicationResponse.class));
        when(jobRepository.findById(application.getJobId())).thenReturn(Optional.empty());
        when(candidateRepository.findByIdAndDeletedAtIsNull(application.getCandidateId()))
                .thenReturn(Optional.empty());

        applicationService.updateStatus(applicationId, companyId, userId, request);

        assertEquals(ApplicationStatus.INTERVIEW, application.getStatus());
    }

    // ── REJECTED is always a valid target from NEW/SCREENING/INTERVIEW ─────

    @Test
    @DisplayName("Valid transition NEW → REJECTED succeeds")
    void updateStatus_validTransition_newToRejected_succeeds() {
        var request =
                ApplicationStatusUpdateRequest.builder().status(ApplicationStatus.REJECTED).build();

        when(applicationRepository.findByIdAndCompanyId(applicationId, companyId))
                .thenReturn(Optional.of(application));
        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(applicationMapper.toResponse(any(Application.class)))
                .thenReturn(mock(ApplicationResponse.class));
        when(jobRepository.findById(application.getJobId())).thenReturn(Optional.empty());
        when(candidateRepository.findByIdAndDeletedAtIsNull(application.getCandidateId()))
                .thenReturn(Optional.empty());

        applicationService.updateStatus(applicationId, companyId, userId, request);

        assertEquals(ApplicationStatus.REJECTED, application.getStatus());
    }
}
