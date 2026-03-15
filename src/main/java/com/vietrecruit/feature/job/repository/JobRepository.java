package com.vietrecruit.feature.job.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vietrecruit.feature.job.entity.Job;
import com.vietrecruit.feature.job.enums.JobStatus;

public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

    long countByCompanyIdAndStatus(UUID companyId, JobStatus status);

    Page<Job> findByCompanyIdAndDeletedAtIsNull(UUID companyId, Pageable pageable);

    Optional<Job> findByIdAndStatusAndDeletedAtIsNull(UUID id, JobStatus status);

    List<Job> findAllByIdInAndDeletedAtIsNull(List<UUID> ids);

    @Query(
            value =
                    """
			SELECT
				MIN(min_salary)          AS minSalary,
				MAX(max_salary)          AS maxSalary,
				PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY (min_salary + max_salary) / 2.0) AS medianSalary,
				COUNT(*)                 AS sampleSize
			FROM jobs
			WHERE (:category IS NULL OR category_id = CAST(:category AS uuid))
			AND (:location IS NULL OR location_id = CAST(:location AS uuid))
			AND status = 'PUBLISHED'
			AND deleted_at IS NULL
			AND min_salary IS NOT NULL
			AND max_salary IS NOT NULL
			""",
            nativeQuery = true)
    SalaryBenchmarkProjection getSalaryBenchmark(
            @Param("category") UUID categoryId, @Param("location") UUID locationId);
}
