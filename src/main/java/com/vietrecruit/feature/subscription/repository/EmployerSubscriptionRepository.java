package com.vietrecruit.feature.subscription.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vietrecruit.feature.subscription.entity.EmployerSubscription;
import com.vietrecruit.feature.subscription.enums.SubscriptionStatus;

public interface EmployerSubscriptionRepository extends JpaRepository<EmployerSubscription, UUID> {

    /**
     * Returns the subscription for the given company with the plan eagerly loaded, filtered by
     * status.
     *
     * @param companyId the company's UUID
     * @param status the required subscription status (typically ACTIVE)
     * @return Optional containing the subscription with plan, or empty if not found
     */
    @Query(
            "SELECT es FROM EmployerSubscription es JOIN FETCH es.plan "
                    + "WHERE es.companyId = :companyId AND es.status = :status")
    Optional<EmployerSubscription> findActiveByCompanyId(
            @Param("companyId") UUID companyId, @Param("status") SubscriptionStatus status);

    boolean existsByCompanyIdAndStatus(UUID companyId, SubscriptionStatus status);

    /**
     * Returns all subscriptions whose status matches the given value and whose expiry is in the
     * past, used by the scheduled expiration job.
     *
     * @param now the current timestamp used as the expiry boundary
     * @param status the status to filter by (typically ACTIVE)
     * @return list of expired subscriptions
     */
    @Query(
            "SELECT es FROM EmployerSubscription es "
                    + "WHERE es.status = :status AND es.expiresAt < :now")
    List<EmployerSubscription> findExpiredActiveSubscriptions(
            @Param("now") Instant now, @Param("status") SubscriptionStatus status);
}
