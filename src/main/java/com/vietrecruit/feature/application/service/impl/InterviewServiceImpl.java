package com.vietrecruit.feature.application.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.enums.EmailSenderAlias;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.application.dto.request.InterviewCreateRequest;
import com.vietrecruit.feature.application.dto.request.InterviewStatusUpdateRequest;
import com.vietrecruit.feature.application.dto.response.InterviewResponse;
import com.vietrecruit.feature.application.entity.Interview;
import com.vietrecruit.feature.application.enums.ApplicationStatus;
import com.vietrecruit.feature.application.enums.InterviewStatus;
import com.vietrecruit.feature.application.mapper.InterviewMapper;
import com.vietrecruit.feature.application.repository.ApplicationRepository;
import com.vietrecruit.feature.application.repository.InterviewRepository;
import com.vietrecruit.feature.application.service.InterviewService;
import com.vietrecruit.feature.candidate.repository.CandidateRepository;
import com.vietrecruit.feature.job.repository.JobRepository;
import com.vietrecruit.feature.notification.dto.EmailRequest;
import com.vietrecruit.feature.notification.service.NotificationService;
import com.vietrecruit.feature.user.entity.User;
import com.vietrecruit.feature.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewServiceImpl implements InterviewService {

    private static final Map<InterviewStatus, Set<InterviewStatus>> ALLOWED_TRANSITIONS =
            Map.of(
                    InterviewStatus.SCHEDULED,
                            Set.of(InterviewStatus.COMPLETED, InterviewStatus.CANCELED),
                    InterviewStatus.COMPLETED, Set.of(),
                    InterviewStatus.CANCELED, Set.of());

    private final InterviewRepository interviewRepository;
    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final CandidateRepository candidateRepository;
    private final InterviewMapper interviewMapper;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public InterviewResponse scheduleInterview(
            UUID applicationId, UUID companyId, UUID createdBy, InterviewCreateRequest request) {

        var application =
                applicationRepository
                        .findByIdAndCompanyId(applicationId, companyId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.APPLICATION_NOT_FOUND));

        if (application.getStatus() != ApplicationStatus.INTERVIEW) {
            throw new ApiException(ApiErrorCode.INTERVIEW_INVALID_STATUS);
        }

        var job =
                jobRepository
                        .findById(application.getJobId())
                        .orElseThrow(
                                () -> new ApiException(ApiErrorCode.NOT_FOUND, "Job not found"));

        // Validate all interviewers
        Set<User> interviewers = new HashSet<>();
        for (UUID interviewerId : request.getInterviewerIds()) {
            var interviewer =
                    userRepository
                            .findById(interviewerId)
                            .orElseThrow(
                                    () ->
                                            new ApiException(
                                                    ApiErrorCode.INTERVIEW_INVALID_INTERVIEWER,
                                                    "Interviewer not found: " + interviewerId));

            if (interviewer.getCompanyId() == null
                    || !interviewer.getCompanyId().equals(job.getCompanyId())) {
                throw new ApiException(
                        ApiErrorCode.INTERVIEW_INVALID_INTERVIEWER,
                        "Interviewer does not belong to the same company: "
                                + interviewer.getFullName());
            }
            interviewers.add(interviewer);
        }

        var interview =
                Interview.builder()
                        .applicationId(applicationId)
                        .title(request.getTitle())
                        .scheduledAt(request.getScheduledAt())
                        .durationMinutes(
                                request.getDurationMinutes() != null
                                        ? request.getDurationMinutes()
                                        : 60)
                        .locationOrLink(request.getLocationOrLink())
                        .interviewType(request.getInterviewType())
                        .status(InterviewStatus.SCHEDULED)
                        .createdBy(createdBy)
                        .interviewers(interviewers)
                        .build();

        interview = interviewRepository.save(interview);

        sendInterviewScheduledNotifications(interview, interviewers, application, job);

        return interviewMapper.toResponse(interview);
    }

    @Override
    public List<InterviewResponse> listInterviews(UUID applicationId, UUID companyId) {
        applicationRepository
                .findByIdAndCompanyId(applicationId, companyId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.APPLICATION_NOT_FOUND));

        return interviewRepository.findByApplicationIdAndDeletedAtIsNull(applicationId).stream()
                .map(interviewMapper::toResponse)
                .toList();
    }

    @Override
    public InterviewResponse getInterview(UUID interviewId, UUID userId) {
        var interview =
                interviewRepository
                        .findByIdWithInterviewers(interviewId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.INTERVIEW_NOT_FOUND));

        var application =
                applicationRepository
                        .findByIdAndDeletedAtIsNull(interview.getApplicationId())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.APPLICATION_NOT_FOUND));

        var user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> new ApiException(ApiErrorCode.NOT_FOUND, "User not found"));

        var job = jobRepository.findById(application.getJobId()).orElse(null);

        boolean isHr =
                user.getCompanyId() != null
                        && job != null
                        && user.getCompanyId().equals(job.getCompanyId());
        boolean isAssignedInterviewer =
                interview.getInterviewers().stream().anyMatch(u -> u.getId().equals(userId));
        boolean isCandidate =
                candidateRepository
                        .findByUserIdAndDeletedAtIsNull(userId)
                        .map(c -> c.getId().equals(application.getCandidateId()))
                        .orElse(false);

        if (!isHr && !isAssignedInterviewer && !isCandidate) {
            throw new ApiException(
                    ApiErrorCode.FORBIDDEN, "You do not have access to this interview");
        }

        return interviewMapper.toResponse(interview);
    }

    @Override
    @Transactional
    public InterviewResponse updateInterviewStatus(
            UUID interviewId, UUID companyId, UUID userId, InterviewStatusUpdateRequest request) {

        var interview =
                interviewRepository
                        .findByIdAndDeletedAtIsNull(interviewId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.INTERVIEW_NOT_FOUND));

        // Verify company ownership via application → job
        applicationRepository
                .findByIdAndCompanyId(interview.getApplicationId(), companyId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.APPLICATION_NOT_FOUND));

        // Validate status transition
        if (!ALLOWED_TRANSITIONS
                .getOrDefault(interview.getStatus(), Set.of())
                .contains(request.getStatus())) {
            throw new ApiException(ApiErrorCode.INTERVIEW_INVALID_STATUS_TRANSITION);
        }

        interview.setStatus(request.getStatus());
        interview.setUpdatedBy(userId);
        interview = interviewRepository.save(interview);

        return interviewMapper.toResponse(interview);
    }

    // ── Notifications ──────────────────────────────────────────────────

    private void sendInterviewScheduledNotifications(
            Interview interview,
            Set<User> interviewers,
            com.vietrecruit.feature.application.entity.Application application,
            com.vietrecruit.feature.job.entity.Job job) {

        // Notify each interviewer
        for (User interviewer : interviewers) {
            try {
                if (interviewer.getEmail() != null) {
                    notificationService.send(
                            new EmailRequest(
                                    List.of(interviewer.getEmail()),
                                    EmailSenderAlias.NOTIFICATION,
                                    "Interview Assignment — " + interview.getTitle(),
                                    null,
                                    "interview-scheduled",
                                    Map.of(
                                            "interviewerName", interviewer.getFullName(),
                                            "interviewTitle", interview.getTitle(),
                                            "jobTitle", job.getTitle(),
                                            "scheduledAt", interview.getScheduledAt().toString(),
                                            "durationMinutes",
                                                    interview.getDurationMinutes().toString())));
                }
            } catch (Exception e) {
                log.warn(
                        "Failed to send interview notification to interviewer {}",
                        interviewer.getId(),
                        e);
            }
        }

        // Notify candidate
        try {
            var candidate =
                    candidateRepository.findByIdAndDeletedAtIsNull(application.getCandidateId());
            if (candidate.isPresent()) {
                var user = userRepository.findById(candidate.get().getUserId()).orElse(null);
                if (user != null && user.getEmail() != null) {
                    notificationService.send(
                            new EmailRequest(
                                    List.of(user.getEmail()),
                                    EmailSenderAlias.NOTIFICATION,
                                    "Interview Scheduled — " + job.getTitle(),
                                    null,
                                    "interview-scheduled-candidate",
                                    Map.of(
                                            "candidateName", user.getFullName(),
                                            "interviewTitle", interview.getTitle(),
                                            "jobTitle", job.getTitle(),
                                            "scheduledAt", interview.getScheduledAt().toString(),
                                            "durationMinutes",
                                                    interview.getDurationMinutes().toString(),
                                            "locationOrLink",
                                                    interview.getLocationOrLink() != null
                                                            ? interview.getLocationOrLink()
                                                            : "TBD")));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to send interview notification to candidate", e);
        }
    }
}
