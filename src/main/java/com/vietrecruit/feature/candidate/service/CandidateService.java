package com.vietrecruit.feature.candidate.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.vietrecruit.feature.candidate.dto.request.CandidateUpdateRequest;
import com.vietrecruit.feature.candidate.dto.response.CandidateProfileResponse;
import com.vietrecruit.feature.candidate.dto.response.CandidateSearchResult;
import com.vietrecruit.feature.candidate.dto.response.CvUploadResponse;
import com.vietrecruit.feature.candidate.entity.Candidate;

public interface CandidateService {

    CandidateProfileResponse getProfile(UUID userId);

    CandidateProfileResponse updateProfile(UUID userId, CandidateUpdateRequest request);

    CvUploadResponse uploadCv(UUID userId, MultipartFile file);

    void deleteCv(UUID userId);

    CandidateProfileResponse getById(UUID candidateId);

    Optional<Candidate> findActiveCandidateById(UUID candidateId);

    List<CandidateSearchResult> searchCandidates(
            String skills, String desiredPosition, Short minYearsExperience, int limit);
}
