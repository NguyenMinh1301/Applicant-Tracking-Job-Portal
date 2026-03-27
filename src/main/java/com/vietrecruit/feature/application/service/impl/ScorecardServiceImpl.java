package com.vietrecruit.feature.application.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.application.dto.request.ScorecardCreateRequest;
import com.vietrecruit.feature.application.dto.response.ScorecardResponse;
import com.vietrecruit.feature.application.entity.Interview;
import com.vietrecruit.feature.application.entity.Scorecard;
import com.vietrecruit.feature.application.enums.InterviewStatus;
import com.vietrecruit.feature.application.mapper.ScorecardMapper;
import com.vietrecruit.feature.application.repository.ApplicationRepository;
import com.vietrecruit.feature.application.repository.InterviewRepository;
import com.vietrecruit.feature.application.repository.ScorecardRepository;
import com.vietrecruit.feature.application.service.ScorecardService;
import com.vietrecruit.feature.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScorecardServiceImpl implements ScorecardService {

    private final ScorecardRepository scorecardRepository;
    private final InterviewRepository interviewRepository;
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final ScorecardMapper scorecardMapper;

    @Override
    @Transactional
    public ScorecardResponse submitScorecard(
            UUID interviewId, UUID userId, ScorecardCreateRequest request) {

        var interview =
                interviewRepository
                        .findByIdAndDeletedAtIsNull(interviewId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.INTERVIEW_NOT_FOUND));

        // Validate interview status
        validateInterviewReady(interview);

        // Validate caller is assigned to this interview
        boolean isAssigned =
                interview.getInterviewers().stream().anyMatch(u -> u.getId().equals(userId));
        if (!isAssigned) {
            throw new ApiException(ApiErrorCode.SCORECARD_NOT_ELIGIBLE);
        }

        // Check for duplicate
        if (scorecardRepository.existsByInterviewIdAndInterviewerId(interviewId, userId)) {
            throw new ApiException(ApiErrorCode.SCORECARD_DUPLICATE);
        }

        var scorecard =
                Scorecard.builder()
                        .interviewId(interviewId)
                        .interviewerId(userId)
                        .skillScore(request.getSkillScore())
                        .attitudeScore(request.getAttitudeScore())
                        .englishScore(request.getEnglishScore())
                        .result(request.getResult())
                        .comments(request.getComments())
                        .createdBy(userId)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();

        try {
            scorecard = scorecardRepository.save(scorecard);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ApiException(ApiErrorCode.SCORECARD_DUPLICATE);
        }

        // Refresh to get generated average_score
        scorecard = scorecardRepository.findById(scorecard.getId()).orElse(scorecard);

        return enrichResponse(scorecard);
    }

    @Override
    public List<ScorecardResponse> listScorecards(UUID interviewId, UUID companyId) {
        var interview =
                interviewRepository
                        .findByIdAndDeletedAtIsNull(interviewId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.INTERVIEW_NOT_FOUND));

        // Verify company ownership via interview → application → job
        applicationRepository
                .findByIdAndCompanyId(interview.getApplicationId(), companyId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.APPLICATION_NOT_FOUND));

        return scorecardRepository.findByInterviewId(interviewId).stream()
                .map(this::enrichResponse)
                .toList();
    }

    private void validateInterviewReady(Interview interview) {
        if (interview.getStatus() != InterviewStatus.COMPLETED) {
            throw new ApiException(ApiErrorCode.SCORECARD_INTERVIEW_NOT_READY);
        }
    }

    private ScorecardResponse enrichResponse(Scorecard scorecard) {
        var resp = scorecardMapper.toResponse(scorecard);
        userRepository
                .findById(scorecard.getInterviewerId())
                .ifPresent(u -> resp.setInterviewerName(u.getFullName()));
        return resp;
    }
}
