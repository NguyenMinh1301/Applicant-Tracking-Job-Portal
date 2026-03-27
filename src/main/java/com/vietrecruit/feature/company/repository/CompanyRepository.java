package com.vietrecruit.feature.company.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vietrecruit.feature.company.entity.Company;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    Optional<Company> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByDomain(String domain);

    boolean existsByDomainAndIdNot(String domain, UUID id);
}
