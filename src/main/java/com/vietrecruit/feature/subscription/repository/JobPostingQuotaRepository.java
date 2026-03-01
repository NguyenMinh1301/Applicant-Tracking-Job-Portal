package com.vietrecruit.feature.subscription.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vietrecruit.feature.subscription.entity.JobPostingQuota;

public interface JobPostingQuotaRepository extends JpaRepository<JobPostingQuota, UUID> {

    Optional<JobPostingQuota> findBySubscriptionId(UUID subscriptionId);
}
