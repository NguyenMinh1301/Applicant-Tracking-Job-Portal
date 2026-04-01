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

    /**
     * Returns salary aggregate statistics (min, max, median, sample size) for published jobs
     * matching the given category and location UUIDs.
     *
     * @param categoryId optional category UUID filter; null to skip
     * @param locationId optional location UUID filter; null to skip
     * @return salary benchmark projection
     */
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

    /**
     * Returns salary aggregate statistics for published jobs matching the given free-text job title
     * and location name keywords.
     *
     * @param jobTitle partial job title keyword; null to skip title filtering
     * @param locationName partial location name keyword; null to skip location filtering
     * @return salary benchmark projection
     */
    @Query(
            value =
                    """
		SELECT
			MIN(j.min_salary)          AS minSalary,
			MAX(j.max_salary)          AS maxSalary,
			PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY (j.min_salary + j.max_salary) / 2.0) AS medianSalary,
			COUNT(*)                 AS sampleSize
		FROM jobs j
		LEFT JOIN locations l ON j.location_id = l.id
		WHERE (:jobTitle IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', :jobTitle, '%')))
		AND (:locationName IS NULL OR LOWER(l.name) LIKE LOWER(CONCAT('%', :locationName, '%')))
		AND j.status = 'PUBLISHED'
		AND j.deleted_at IS NULL
		AND j.min_salary IS NOT NULL
		AND j.max_salary IS NOT NULL
		""",
            nativeQuery = true)
    SalaryBenchmarkProjection getSalaryBenchmarkByText(
            @Param("jobTitle") String jobTitle, @Param("locationName") String locationName);
}
