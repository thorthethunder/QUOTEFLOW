package com.quoteflow.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<AppUser, UUID> {

	Optional<AppUser> findByEmail(String email);

	boolean existsByEmail(String email);

	@Query("select u from AppUser u join fetch u.business where u.email = :email")
	Optional<AppUser> findByEmailWithBusiness(@Param("email") String email);

	@Query("select u from AppUser u join fetch u.business where u.id = :id")
	Optional<AppUser> findByIdWithBusiness(@Param("id") UUID id);

	List<AppUser> findByBusiness_Id(UUID businessId);
}
