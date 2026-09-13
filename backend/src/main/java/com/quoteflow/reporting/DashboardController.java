package com.quoteflow.reporting;

import com.quoteflow.reporting.dto.DashboardSummaryResponse;
import com.quoteflow.security.AuthenticatedUser;
import com.quoteflow.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

	private final ReportingService reportingService;

	public DashboardController(ReportingService reportingService) {
		this.reportingService = reportingService;
	}

	@GetMapping("/summary")
	@Operation(summary = "Tenant dashboard summary (period aggregates + recent activity)")
	public DashboardSummaryResponse summary(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return reportingService.summary(principal, from, to);
	}
}
