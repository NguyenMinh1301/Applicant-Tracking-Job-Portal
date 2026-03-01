package com.vietrecruit.feature.subscription.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.vietrecruit.feature.subscription.dto.response.PlanResponse;
import com.vietrecruit.feature.subscription.dto.response.QuotaResponse;
import com.vietrecruit.feature.subscription.dto.response.SubscriptionResponse;
import com.vietrecruit.feature.subscription.entity.EmployerSubscription;
import com.vietrecruit.feature.subscription.entity.JobPostingQuota;
import com.vietrecruit.feature.subscription.entity.SubscriptionPlan;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

    PlanResponse toPlanResponse(SubscriptionPlan plan);

    @Mapping(source = "plan.name", target = "planName")
    @Mapping(source = "plan.code", target = "planCode")
    SubscriptionResponse toSubscriptionResponse(EmployerSubscription subscription);

    @Mapping(source = "quota.jobsActive", target = "jobsActive")
    @Mapping(source = "quota.jobsPosted", target = "jobsPosted")
    @Mapping(source = "quota.cycleStart", target = "cycleStart")
    @Mapping(source = "quota.cycleEnd", target = "cycleEnd")
    @Mapping(source = "plan.maxActiveJobs", target = "maxActiveJobs")
    QuotaResponse toQuotaResponse(JobPostingQuota quota, SubscriptionPlan plan);
}
