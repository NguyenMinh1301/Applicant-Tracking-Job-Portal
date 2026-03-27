package com.vietrecruit.feature.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.vietrecruit.feature.user.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query(
            "SELECT DISTINCT u FROM User u "
                    + "LEFT JOIN FETCH u.roles r "
                    + "LEFT JOIN FETCH r.permissions "
                    + "WHERE u.id = :id")
    Optional<User> findByIdWithRolesAndPermissions(@Param("id") UUID id);

    @Query("SELECT u.emailVerified FROM User u WHERE u.id = :id")
    Boolean findEmailVerifiedById(@Param("id") UUID id);
}
