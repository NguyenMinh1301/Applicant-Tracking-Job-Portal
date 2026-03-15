package com.vietrecruit.feature.candidate.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.vietrecruit.feature.candidate.entity.Candidate;

@Repository
public interface CandidateRepository
        extends JpaRepository<Candidate, UUID>, JpaSpecificationExecutor<Candidate> {

    Optional<Candidate> findByUserIdAndDeletedAtIsNull(UUID userId);

    Optional<Candidate> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByUserId(UUID userId);
}
