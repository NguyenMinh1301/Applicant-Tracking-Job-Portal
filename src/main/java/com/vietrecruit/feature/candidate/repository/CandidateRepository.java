package com.vietrecruit.feature.candidate.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.vietrecruit.feature.candidate.entity.Candidate;

@Repository
public interface CandidateRepository
        extends JpaRepository<Candidate, UUID>, JpaSpecificationExecutor<Candidate> {

    Optional<Candidate> findByUserIdAndDeletedAtIsNull(UUID userId);

    Optional<Candidate> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByUserId(UUID userId);

    /**
     * Finds open-to-work candidates matching the given skill list at the database level. The skill
     * overlap check uses {@code unnest + IN} to avoid post-pagination filtering that would
     * under-return results when the first page contains no skill-matching rows.
     */
    @Query(
            value =
                    """
					SELECT * FROM candidates c
					WHERE c.deleted_at IS NULL
					AND c.is_open_to_work = true
					AND (:desiredPositionPattern IS NULL
						OR lower(c.desired_position) LIKE :desiredPositionPattern)
					AND (:minYearsExperience IS NULL
						OR c.years_of_experience >= :minYearsExperience)
					AND EXISTS (
						SELECT 1 FROM unnest(c.skills) AS skill
						WHERE lower(skill) IN (:requiredSkills)
					)
					LIMIT :limit
					""",
            nativeQuery = true)
    List<Candidate> findBySkillsFilter(
            @Param("desiredPositionPattern") String desiredPositionPattern,
            @Param("minYearsExperience") Short minYearsExperience,
            @Param("requiredSkills") List<String> requiredSkills,
            @Param("limit") int limit);

    /**
     * Same as {@link #findBySkillsFilter} but scoped to candidates who have applied to the given
     * company (identified by their IDs).
     */
    @Query(
            value =
                    """
					SELECT * FROM candidates c
					WHERE c.deleted_at IS NULL
					AND c.id IN (:applicantIds)
					AND (:desiredPositionPattern IS NULL
						OR lower(c.desired_position) LIKE :desiredPositionPattern)
					AND (:minYearsExperience IS NULL
						OR c.years_of_experience >= :minYearsExperience)
					AND EXISTS (
						SELECT 1 FROM unnest(c.skills) AS skill
						WHERE lower(skill) IN (:requiredSkills)
					)
					LIMIT :limit
					""",
            nativeQuery = true)
    List<Candidate> findBySkillsFilterForCompany(
            @Param("desiredPositionPattern") String desiredPositionPattern,
            @Param("minYearsExperience") Short minYearsExperience,
            @Param("requiredSkills") List<String> requiredSkills,
            @Param("applicantIds") List<UUID> applicantIds,
            @Param("limit") int limit);
}
