package com.vietrecruit.feature.payment.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.vietrecruit.feature.payment.entity.PaymentTransaction;
import com.vietrecruit.feature.payment.enums.PaymentStatus;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentTransaction> findByOrderCode(Long orderCode);

    boolean existsByOrderCode(Long orderCode);

    List<PaymentTransaction> findByCompanyIdAndStatus(UUID companyId, PaymentStatus status);

    /**
     * Returns all payment transactions in the given status created before the cutoff, used to
     * expire stale pending orders.
     *
     * @param cutoff the timestamp threshold; transactions created before this are returned
     * @param status the payment status to filter by
     * @return list of matching payment transactions
     */
    @Query(
            """
			SELECT pt FROM PaymentTransaction pt
			WHERE pt.status = :status
			AND pt.createdAt < :cutoff
			""")
    List<PaymentTransaction> findExpiredPending(
            @Param("cutoff") Instant cutoff, @Param("status") PaymentStatus status);

    /**
     * Returns payment transactions in the given status created within the window {@code
     * (lowerBound, cutoff)}, used by the reconciliation task to re-query PayOS.
     *
     * @param cutoff upper bound; transactions created before this are included
     * @param lowerBound lower bound; transactions created after this are included
     * @param status the payment status to filter by
     * @return list of matching payment transactions
     */
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

    /**
     * Returns PAID transactions updated after the cutoff whose company has no active subscription,
     * used by the activation recovery task to detect missed webhook activations.
     *
     * @param cutoff only transactions updated after this timestamp are returned
     * @return list of paid transactions without a corresponding active subscription
     */
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
