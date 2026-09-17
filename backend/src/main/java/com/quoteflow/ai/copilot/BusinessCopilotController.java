package com.quoteflow.ai.copilot;

import com.quoteflow.ai.copilot.dto.BusinessCopilotRequest;
import com.quoteflow.ai.copilot.dto.BusinessCopilotResponse;
import com.quoteflow.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/ai", produces = MediaType.APPLICATION_JSON_VALUE)
public class BusinessCopilotController {

	private final BusinessCopilotService businessCopilotService;

	public BusinessCopilotController(BusinessCopilotService businessCopilotService) {
		this.businessCopilotService = businessCopilotService;
	}

	@PostMapping(path = "/copilot/ask", consumes = MediaType.APPLICATION_JSON_VALUE)
	public BusinessCopilotResponse ask(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody BusinessCopilotRequest request) {
		return businessCopilotService.ask(principal, request);
	}
}
