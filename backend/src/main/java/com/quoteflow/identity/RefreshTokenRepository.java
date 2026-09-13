package com.quoteflow.identity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from RefreshToken r join fetch r.user u join fetch u.business where r.tokenHash = :tokenHash")
	Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
