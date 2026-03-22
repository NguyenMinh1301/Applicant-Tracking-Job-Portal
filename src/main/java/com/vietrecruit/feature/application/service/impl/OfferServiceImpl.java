package com.vietrecruit.feature.application.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.enums.EmailSenderAlias;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.application.dto.request.OfferCreateRequest;
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
import com.vietrecruit.feature.application.service.OfferService;
import com.vietrecruit.feature.candidate.repository.CandidateRepository;
import com.vietrecruit.feature.job.repository.JobRepository;
import com.vietrecruit.feature.notification.dto.EmailRequest;
import com.vietrecruit.feature.notification.service.NotificationService;
import com.vietrecruit.feature.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OfferServiceImpl implements OfferService {

    private final OfferRepository offerRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationService applicationService;
    private final JobRepository jobRepository;
    private final CandidateRepository candidateRepository;
    private final UserRepository userRepository;
    private final OfferMapper offerMapper;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public OfferResponse createOffer(
            UUID applicationId, UUID companyId, UUID createdBy, OfferCreateRequest request) {

        var application =
                applicationRepository
                        .findByIdAndCompanyId(applicationId, companyId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.APPLICATION_NOT_FOUND));

        if (application.getStatus() != ApplicationStatus.OFFER) {
            throw new ApiException(ApiErrorCode.OFFER_APPLICATION_NOT_READY);
        }

        // Check for existing active offer
        var activeStatuses = List.of(OfferStatus.DRAFT, OfferStatus.SENT);
        if (offerRepository.existsByApplicationIdAndStatusInAndDeletedAtIsNull(
                applicationId, activeStatuses)) {
            throw new ApiException(ApiErrorCode.OFFER_ALREADY_EXISTS);
        }

        var offer = offerMapper.toEntity(request);
        offer.setApplicationId(applicationId);
        offer.setStatus(OfferStatus.DRAFT);
        offer.setCreatedBy(createdBy);

        offer = offerRepository.save(offer);

        return offerMapper.toResponse(offer);
    }

    @Override
    public List<OfferResponse> listOffers(UUID applicationId, UUID userId) {
        var application =
                applicationRepository
                        .findByIdAndDeletedAtIsNull(applicationId)
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

        var offers = offerRepository.findByApplicationIdAndDeletedAtIsNull(applicationId);

        if (!isHr) {
            // Candidate: only show SENT and later
            boolean isCandidate =
                    candidateRepository
                            .findByUserIdAndDeletedAtIsNull(userId)
                            .map(c -> c.getId().equals(application.getCandidateId()))
                            .orElse(false);

            if (!isCandidate) {
                throw new ApiException(
                        ApiErrorCode.FORBIDDEN, "You do not have access to these offers");
            }

            offers = offers.stream().filter(o -> o.getStatus() != OfferStatus.DRAFT).toList();
        }

        return offers.stream().map(offerMapper::toResponse).toList();
    }

    @Override
    public OfferResponse getOffer(UUID offerId, UUID userId) {
        var offer =
                offerRepository
                        .findByIdAndDeletedAtIsNull(offerId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.OFFER_NOT_FOUND));

        var application =
                applicationRepository
                        .findByIdAndDeletedAtIsNull(offer.getApplicationId())
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

        if (!isHr) {
            boolean isCandidate =
                    candidateRepository
                            .findByUserIdAndDeletedAtIsNull(userId)
                            .map(c -> c.getId().equals(application.getCandidateId()))
                            .orElse(false);

            if (!isCandidate) {
                throw new ApiException(
                        ApiErrorCode.FORBIDDEN, "You do not have access to this offer");
            }

            if (offer.getStatus() == OfferStatus.DRAFT) {
                throw new ApiException(ApiErrorCode.OFFER_NOT_FOUND);
            }
        }

        return offerMapper.toResponse(offer);
    }

    @Override
    @Transactional
    public OfferResponse sendOffer(UUID offerId, UUID companyId, UUID userId) {
        var offer =
                offerRepository
                        .findByIdAndDeletedAtIsNull(offerId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.OFFER_NOT_FOUND));

        var application =
                applicationRepository
                        .findByIdAndCompanyId(offer.getApplicationId(), companyId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.APPLICATION_NOT_FOUND));

        if (offer.getStatus() != OfferStatus.DRAFT) {
            throw new ApiException(
                    ApiErrorCode.OFFER_INVALID_TRANSITION, "Only DRAFT offers can be sent");
        }

        offer.setStatus(OfferStatus.SENT);
        offer.setUpdatedBy(userId);
        offer = offerRepository.save(offer);

        sendOfferReceivedNotification(offer, application);

        return offerMapper.toResponse(offer);
    }

    @Override
    @Transactional
    public OfferResponse respondToOffer(UUID offerId, UUID userId, OfferRespondRequest request) {

        var offer =
                offerRepository
                        .findByIdAndDeletedAtIsNull(offerId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.OFFER_NOT_FOUND));

        if (offer.getStatus() != OfferStatus.SENT) {
            throw new ApiException(
                    ApiErrorCode.OFFER_INVALID_TRANSITION, "Only SENT offers can be responded to");
        }

        var application =
                applicationRepository
                        .findByIdAndDeletedAtIsNull(offer.getApplicationId())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.APPLICATION_NOT_FOUND));

        // Verify caller is the candidate
        var candidate =
                candidateRepository
                        .findByUserIdAndDeletedAtIsNull(userId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ApiErrorCode.FORBIDDEN,
                                                "Only the candidate can respond to an offer"));

        if (!candidate.getId().equals(application.getCandidateId())) {
            throw new ApiException(
                    ApiErrorCode.FORBIDDEN, "You are not the candidate for this application");
        }

        boolean isAccept = "ACCEPT".equals(request.getAction());

        offer.setStatus(isAccept ? OfferStatus.ACCEPTED : OfferStatus.DECLINED);
        offer.setUpdatedBy(userId);
        try {
            offer = offerRepository.save(offer);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            throw new ApiException(ApiErrorCode.CONCURRENT_MODIFICATION);
        }

        // Atomically update application status
        var oldStatus = application.getStatus();
        var newStatus = isAccept ? ApplicationStatus.HIRED : ApplicationStatus.REJECTED;
        application.setStatus(newStatus);
        try {
            applicationRepository.save(application);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            throw new ApiException(ApiErrorCode.CONCURRENT_MODIFICATION);
        }

        applicationService.insertHistory(
                application.getId(),
                oldStatus,
                newStatus,
                userId,
                isAccept ? "Offer accepted" : "Offer declined");

        sendOfferRespondedNotification(offer, application, candidate, isAccept);

        return offerMapper.toResponse(offer);
    }

    @Override
    @Transactional
    public void deleteOffer(UUID offerId, UUID companyId) {
        var offer =
                offerRepository
                        .findByIdAndDeletedAtIsNull(offerId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.OFFER_NOT_FOUND));

        applicationRepository
                .findByIdAndCompanyId(offer.getApplicationId(), companyId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.APPLICATION_NOT_FOUND));

        if (offer.getStatus() != OfferStatus.DRAFT) {
            throw new ApiException(
                    ApiErrorCode.OFFER_INVALID_TRANSITION, "Only DRAFT offers can be deleted");
        }

        offer.setDeletedAt(Instant.now());
        offerRepository.save(offer);
    }

    // ── Notifications ──────────────────────────────────────────────────

    private void sendOfferReceivedNotification(Offer offer, Application application) {
        try {
            var candidate =
                    candidateRepository
                            .findByIdAndDeletedAtIsNull(application.getCandidateId())
                            .orElse(null);
            if (candidate == null) return;

            var user = userRepository.findById(candidate.getUserId()).orElse(null);
            if (user == null || user.getEmail() == null) return;

            var job = jobRepository.findById(application.getJobId()).orElse(null);
            var jobTitle = job != null ? job.getTitle() : "a position";

            notificationService.send(
                    new EmailRequest(
                            List.of(user.getEmail()),
                            EmailSenderAlias.NOTIFICATION,
                            "You Have Received an Offer — " + jobTitle,
                            null,
                            "offer-received",
                            Map.of(
                                    "candidateName", user.getFullName(),
                                    "jobTitle", jobTitle,
                                    "baseSalary", offer.getBaseSalary().toPlainString(),
                                    "currency", offer.getCurrency())));
        } catch (Exception e) {
            log.warn("Failed to send offer received notification", e);
        }
    }

    private void sendOfferRespondedNotification(
            Offer offer,
            Application application,
            com.vietrecruit.feature.candidate.entity.Candidate candidate,
            boolean accepted) {
        try {
            var job = jobRepository.findById(application.getJobId()).orElse(null);
            if (job == null || job.getCreatedBy() == null) return;

            var hrUser = userRepository.findById(job.getCreatedBy()).orElse(null);
            if (hrUser == null || hrUser.getEmail() == null) return;

            var candidateUser =
                    candidate != null
                            ? userRepository.findById(candidate.getUserId()).orElse(null)
                            : null;
            var candidateName =
                    candidateUser != null ? candidateUser.getFullName() : "The candidate";

            notificationService.send(
                    new EmailRequest(
                            List.of(hrUser.getEmail()),
                            EmailSenderAlias.NOTIFICATION,
                            "Offer "
                                    + (accepted ? "Accepted" : "Declined")
                                    + " — "
                                    + job.getTitle(),
                            null,
                            "offer-responded",
                            Map.of(
                                    "hrName",
                                    hrUser.getFullName(),
                                    "candidateName",
                                    candidateName,
                                    "jobTitle",
                                    job.getTitle(),
                                    "action",
                                    accepted ? "accepted" : "declined")));
        } catch (Exception e) {
            log.warn("Failed to send offer responded notification", e);
        }
    }
}
