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

    @Query(
            "SELECT es FROM EmployerSubscription es JOIN FETCH es.plan "
                    + "WHERE es.companyId = :companyId AND es.status = 'ACTIVE'")
    Optional<EmployerSubscription> findActiveByCompanyId(@Param("companyId") UUID companyId);

    boolean existsByCompanyIdAndStatus(UUID companyId, SubscriptionStatus status);

    @Query(
            "SELECT es FROM EmployerSubscription es "
                    + "WHERE es.status = 'ACTIVE' AND es.expiresAt < :now")
    List<EmployerSubscription> findExpiredActiveSubscriptions(@Param("now") Instant now);
}
