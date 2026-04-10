package com.vietrecruit.feature.company.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import com.vietrecruit.feature.user.entity.Role;
import com.vietrecruit.feature.user.entity.User;

public class CompanyMemberSpecification {

    private CompanyMemberSpecification() {}

    public static Specification<User> buildSpecification(
            UUID companyId, String roleFilter, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("companyId"), companyId));

            if (roleFilter != null && !roleFilter.isBlank()) {
                Join<User, Role> rolesJoin = root.join("roles");
                predicates.add(cb.equal(rolesJoin.get("code"), roleFilter));
            }

            if (search != null && !search.isBlank()) {
                String searchPattern = "%" + search.toLowerCase() + "%";
                Predicate namePredicate = cb.like(cb.lower(root.get("fullName")), searchPattern);
                Predicate emailPredicate = cb.like(cb.lower(root.get("email")), searchPattern);
                predicates.add(cb.or(namePredicate, emailPredicate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
