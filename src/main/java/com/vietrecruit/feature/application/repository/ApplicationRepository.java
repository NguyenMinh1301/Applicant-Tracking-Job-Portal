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

@Repository
public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    Optional<Application> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByJobIdAndCandidateId(UUID jobId, UUID candidateId);

    @Query(
            nativeQuery = true,
            value =
                    "SELECT a.* FROM applications a JOIN jobs j ON a.job_id = j.id "
                            + "WHERE j.company_id = :companyId AND a.deleted_at IS NULL "
                            + "AND (:jobId IS NULL OR a.job_id = :jobId) "
                            + "AND (CAST(:status AS application_status) IS NULL OR a.status = CAST(:status AS application_status)) "
                            + "ORDER BY a.created_at DESC",
            countQuery =
                    "SELECT COUNT(*) FROM applications a JOIN jobs j ON a.job_id = j.id "
                            + "WHERE j.company_id = :companyId AND a.deleted_at IS NULL "
                            + "AND (:jobId IS NULL OR a.job_id = :jobId) "
                            + "AND (CAST(:status AS application_status) IS NULL OR a.status = CAST(:status AS application_status))")
    Page<Application> findByCompanyFiltered(
            @Param("companyId") UUID companyId,
            @Param("jobId") UUID jobId,
            @Param("status") String status,
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

    @Query(
            "SELECT DISTINCT a.candidateId FROM Application a JOIN Job j ON a.jobId = j.id "
                    + "WHERE j.companyId = :companyId AND a.deletedAt IS NULL")
    List<UUID> findCandidateIdsByCompanyId(@Param("companyId") UUID companyId);
}
