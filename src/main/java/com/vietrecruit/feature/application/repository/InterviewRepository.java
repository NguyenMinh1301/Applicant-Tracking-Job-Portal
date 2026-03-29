package com.vietrecruit.feature.application.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.vietrecruit.feature.application.entity.Interview;
import com.vietrecruit.feature.application.enums.InterviewStatus;

@Repository
public interface InterviewRepository extends JpaRepository<Interview, UUID> {

    Optional<Interview> findByIdAndDeletedAtIsNull(UUID id);

    List<Interview> findByApplicationIdAndDeletedAtIsNull(UUID applicationId);

    boolean existsByApplicationIdAndStatusAndDeletedAtIsNull(
            UUID applicationId, InterviewStatus status);

    @Query(
            "SELECT i FROM Interview i JOIN i.interviewers u "
                    + "WHERE u.id = :userId AND i.deletedAt IS NULL")
    List<Interview> findByInterviewerUserId(@Param("userId") UUID userId);
}
