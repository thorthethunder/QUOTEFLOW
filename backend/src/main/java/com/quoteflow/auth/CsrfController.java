package com.quoteflow.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Ensures the browser receives an XSRF-TOKEN cookie before cookie-authenticated refresh/logout.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class CsrfController {

	@GetMapping("/csrf")
	public ResponseEntity<Map<String, String>> csrf(HttpServletRequest request) {
		CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
		if (token == null) {
			token = (CsrfToken) request.getAttribute("_csrf");
		}
		String value = token != null ? token.getToken() : "";
		return ResponseEntity.ok(Map.of("headerName", "X-XSRF-TOKEN", "parameterName", "_csrf", "token", value));
	}
}
