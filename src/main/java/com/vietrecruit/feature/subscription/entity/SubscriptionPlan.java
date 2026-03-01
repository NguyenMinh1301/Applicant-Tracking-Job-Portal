package com.vietrecruit.feature.subscription.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "subscription_plans")
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    private String description;

    @Column(name = "max_active_jobs", nullable = false)
    private Integer maxActiveJobs;

    @Column(name = "job_duration_days", nullable = false)
    private Integer jobDurationDays;

    @Builder.Default
    @Column(name = "resume_access")
    private Boolean resumeAccess = false;

    @Builder.Default
    @Column(name = "ai_matching")
    private Boolean aiMatching = false;

    @Builder.Default
    @Column(name = "priority_listing")
    private Boolean priorityListing = false;

    @Column(name = "price_monthly", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceMonthly;

    @Column(name = "price_yearly", precision = 12, scale = 2)
    private BigDecimal priceYearly;

    @Builder.Default
    @Column(length = 10)
    private String currency = "VND";

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
