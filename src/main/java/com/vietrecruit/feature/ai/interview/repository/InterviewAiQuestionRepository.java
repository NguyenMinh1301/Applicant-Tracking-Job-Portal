package com.vietrecruit.feature.ai.interview.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.vietrecruit.feature.ai.interview.entity.InterviewAiQuestion;

@Repository
public interface InterviewAiQuestionRepository extends JpaRepository<InterviewAiQuestion, UUID> {

    Optional<InterviewAiQuestion> findByInterviewId(UUID interviewId);
}
