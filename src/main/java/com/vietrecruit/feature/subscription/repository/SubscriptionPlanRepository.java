package com.vietrecruit.feature.subscription.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vietrecruit.feature.subscription.entity.SubscriptionPlan;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    List<SubscriptionPlan> findAllByIsActiveTrue();

    Optional<SubscriptionPlan> findByCode(String code);
}
