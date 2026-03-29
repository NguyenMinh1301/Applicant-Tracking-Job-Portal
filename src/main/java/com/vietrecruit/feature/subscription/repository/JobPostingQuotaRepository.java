package com.vietrecruit.feature.subscription.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vietrecruit.feature.subscription.entity.JobPostingQuota;

public interface JobPostingQuotaRepository extends JpaRepository<JobPostingQuota, UUID> {

    Optional<JobPostingQuota> findBySubscriptionId(UUID subscriptionId);

    /**
     * Atomically increment active job count only if under the limit. Returns 1 if successful, 0 if
     * quota exceeded.
     */
    @Modifying(clearAutomatically = true)
    @Query(
            "UPDATE JobPostingQuota q SET q.jobsActive = q.jobsActive + 1, "
                    + "q.jobsPosted = q.jobsPosted + 1 "
                    + "WHERE q.subscription.id = :subscriptionId "
                    + "AND (q.jobsActive < :maxActiveJobs OR :maxActiveJobs = -1)")
    int atomicIncrementIfUnderLimit(
            @Param("subscriptionId") UUID subscriptionId,
            @Param("maxActiveJobs") int maxActiveJobs);

    /**
     * Atomically decrement active job count, clamping at zero. Returns 1 if the row was found and
     * updated, 0 otherwise.
     */
    @Modifying(clearAutomatically = true)
    @Query(
            "UPDATE JobPostingQuota q SET q.jobsActive = GREATEST(0, q.jobsActive - 1) "
                    + "WHERE q.subscription.id = :subscriptionId")
    int atomicDecrementActiveJobs(@Param("subscriptionId") UUID subscriptionId);
}
