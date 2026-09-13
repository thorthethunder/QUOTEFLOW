package com.quoteflow.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	Optional<Notification> findByIdAndBusinessId(UUID id, UUID businessId);

	List<Notification> findTop20ByBusinessIdAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
			UUID businessId,
			NotificationReferenceType referenceType,
			UUID referenceId);

	Optional<Notification> findFirstByBusinessIdAndTypeAndReferenceIdAndStatusInOrderByCreatedAtDesc(
			UUID businessId,
			NotificationType type,
			UUID referenceId,
			List<NotificationStatus> statuses);

	Optional<Notification> findByBusinessIdAndIdempotencyKey(UUID businessId, String idempotencyKey);

	@Query(value = """
			SELECT id FROM notifications
			WHERE (
			        (status = 'PENDING' AND next_attempt_at <= :now)
			     OR (status = 'SENDING' AND updated_at < :staleBefore)
			      )
			ORDER BY created_at
			LIMIT :batchSize
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<UUID> claimCandidateIds(
			@Param("now") Instant now,
			@Param("staleBefore") Instant staleBefore,
			@Param("batchSize") int batchSize);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE Notification n
			SET n.status = com.quoteflow.notification.NotificationStatus.SENDING,
			    n.updatedAt = :now
			WHERE n.id = :id AND (
			    n.status = com.quoteflow.notification.NotificationStatus.PENDING
			    OR (n.status = com.quoteflow.notification.NotificationStatus.SENDING AND n.updatedAt < :staleBefore)
			)
			""")
	int claimOne(@Param("id") UUID id, @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE Notification n
			SET n.status = com.quoteflow.notification.NotificationStatus.SENDING,
			    n.updatedAt = :now
			WHERE n.id IN :ids AND (
			    n.status = com.quoteflow.notification.NotificationStatus.PENDING
			    OR (n.status = com.quoteflow.notification.NotificationStatus.SENDING AND n.updatedAt < :staleBefore)
			)
			""")
	int markSending(
			@Param("ids") List<UUID> ids,
			@Param("now") Instant now,
			@Param("staleBefore") Instant staleBefore);
}
