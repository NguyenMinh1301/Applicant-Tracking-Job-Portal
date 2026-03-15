package com.vietrecruit.feature.application.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.vietrecruit.feature.application.entity.Application;
import com.vietrecruit.feature.application.enums.ApplicationStatus;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    Optional<Application> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByJobIdAndCandidateId(UUID jobId, UUID candidateId);

    @Query(
            "SELECT a FROM Application a JOIN Job j ON a.jobId = j.id "
                    + "WHERE j.companyId = :companyId AND a.deletedAt IS NULL "
                    + "AND (:jobId IS NULL OR a.jobId = :jobId) "
                    + "AND (:status IS NULL OR a.status = :status)")
    Page<Application> findByCompanyFiltered(
            @Param("companyId") UUID companyId,
            @Param("jobId") UUID jobId,
            @Param("status") ApplicationStatus status,
            Pageable pageable);

    @Query(
            "SELECT a FROM Application a JOIN Candidate c ON a.candidateId = c.id "
                    + "WHERE c.userId = :userId AND a.deletedAt IS NULL")
    Page<Application> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    List<Application> findByJobIdAndDeletedAtIsNull(UUID jobId);

    List<Application> findByJobIdAndAiScoreIsNullAndDeletedAtIsNull(UUID jobId);

    @Query(
            "SELECT a FROM Application a JOIN Job j ON a.jobId = j.id "
                    + "WHERE a.id = :id AND j.companyId = :companyId AND a.deletedAt IS NULL")
    Optional<Application> findByIdAndCompanyId(
            @Param("id") UUID id, @Param("companyId") UUID companyId);
}
