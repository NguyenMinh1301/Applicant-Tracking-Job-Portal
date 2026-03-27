package com.vietrecruit.feature.payment.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.vietrecruit.feature.payment.entity.PaymentTransaction;
import com.vietrecruit.feature.payment.enums.PaymentStatus;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByOrderCode(Long orderCode);

    Optional<PaymentTransaction> findByCompanyIdAndStatus(UUID companyId, PaymentStatus status);

    @Query(
            """
			SELECT pt FROM PaymentTransaction pt
			WHERE pt.status = :status
			AND pt.createdAt < :cutoff
			""")
    List<PaymentTransaction> findExpiredPending(
            @Param("cutoff") Instant cutoff, @Param("status") PaymentStatus status);

    @Query(
            """
			SELECT pt FROM PaymentTransaction pt
			WHERE pt.status = :status
			AND pt.createdAt < :cutoff
			AND pt.createdAt > :lowerBound
			""")
    List<PaymentTransaction> findStalePending(
            @Param("cutoff") Instant cutoff,
            @Param("lowerBound") Instant lowerBound,
            @Param("status") PaymentStatus status);

    @Query(
            value =
                    """
			SELECT pt.* FROM payment_transactions pt
			WHERE pt.status = 'PAID'
			AND pt.updated_at > :cutoff
			AND pt.company_id NOT IN (
				SELECT es.company_id FROM employer_subscriptions es
				WHERE es.status = 'ACTIVE'
			)
			""",
            nativeQuery = true)
    List<PaymentTransaction> findPaidWithoutActiveSubscription(@Param("cutoff") Instant cutoff);
}
