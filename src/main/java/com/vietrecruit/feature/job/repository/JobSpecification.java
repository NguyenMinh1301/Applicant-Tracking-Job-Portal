package com.vietrecruit.feature.job.repository;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.vietrecruit.feature.job.entity.Job;
import com.vietrecruit.feature.job.enums.JobStatus;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JobSpecification {

    public static Specification<Job> isPublished() {
        return (root, query, cb) ->
                cb.and(
                        cb.equal(root.get("status"), JobStatus.PUBLISHED),
                        cb.isNull(root.get("deletedAt")));
    }

    public static Specification<Job> hasCategoryId(UUID categoryId) {
        if (categoryId == null) return Specification.where(null);
        return (root, query, cb) -> cb.equal(root.get("categoryId"), categoryId);
    }

    public static Specification<Job> hasLocationId(UUID locationId) {
        if (locationId == null) return Specification.where(null);
        return (root, query, cb) -> cb.equal(root.get("locationId"), locationId);
    }

    public static Specification<Job> titleContains(String keyword) {
        if (keyword == null || keyword.isBlank()) return Specification.where(null);
        return (root, query, cb) -> {
            String escaped = escapeLikePattern(keyword.toLowerCase());
            return cb.like(cb.lower(root.get("title")), "%" + escaped + "%", '\\');
        };
    }

    private static String escapeLikePattern(String input) {
        return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
