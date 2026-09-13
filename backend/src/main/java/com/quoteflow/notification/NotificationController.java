package com.quoteflow.notification;

import com.quoteflow.security.AuthenticatedUser;
import com.quoteflow.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@GetMapping("/{notificationId}")
	@Operation(summary = "Get a notification by id (tenant-scoped)")
	public NotificationResponse get(@PathVariable UUID notificationId) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return notificationService.get(principal.getBusinessId(), notificationId);
	}
}
