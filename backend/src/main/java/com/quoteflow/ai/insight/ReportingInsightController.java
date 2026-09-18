package com.quoteflow.ai.insight;

import com.quoteflow.ai.insight.dto.ReportingInsightRequest;
import com.quoteflow.ai.insight.dto.ReportingInsightResponse;
import com.quoteflow.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/ai/insights", produces = MediaType.APPLICATION_JSON_VALUE)
public class ReportingInsightController {

	private final ReportingInsightService reportingInsightService;

	public ReportingInsightController(ReportingInsightService reportingInsightService) {
		this.reportingInsightService = reportingInsightService;
	}

	@PostMapping(path = "/analyze", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ReportingInsightResponse analyze(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody ReportingInsightRequest request) {
		return reportingInsightService.analyze(principal, request);
	}
}
