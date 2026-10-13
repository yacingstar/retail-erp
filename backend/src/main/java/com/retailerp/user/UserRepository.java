package com.retailerp.user;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    List<User> findAllByOrderByFullNameAsc();

    long countByRoleAndEnabledTrue(Role role);

    long countByEnabledTrue();
}
