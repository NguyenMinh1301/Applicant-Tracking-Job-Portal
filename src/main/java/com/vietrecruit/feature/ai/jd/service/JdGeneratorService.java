package com.vietrecruit.feature.ai.jd.service;

import java.util.UUID;

import com.vietrecruit.feature.ai.jd.dto.ApplyDescriptionRequest;
import com.vietrecruit.feature.ai.jd.dto.JdGenerateRequest;
import com.vietrecruit.feature.ai.jd.dto.JdGenerateResponse;

public interface JdGeneratorService {

    /** Generates a complete, bias-free job description from minimal inputs. Does not persist. */
    JdGenerateResponse generate(JdGenerateRequest request, UUID companyId);

    /** Applies a previously generated description to an existing job posting. */
    void applyDescription(UUID jobId, UUID companyId, ApplyDescriptionRequest request);
}
