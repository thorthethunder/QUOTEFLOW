package com.quoteflow.customer;

import com.quoteflow.customer.dto.CreateCustomerRequest;
import com.quoteflow.customer.dto.CustomerResponse;
import com.quoteflow.customer.dto.PagedCustomerResponse;
import com.quoteflow.customer.dto.UpdateCustomerRequest;
import com.quoteflow.security.AuthenticatedUser;
import com.quoteflow.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers")
@SecurityRequirement(name = "bearerAuth")
public class CustomerController {

	private final CustomerService customerService;

	public CustomerController(CustomerService customerService) {
		this.customerService = customerService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a customer for the authenticated business")
	public CustomerResponse create(@Valid @RequestBody CreateCustomerRequest request) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return customerService.create(principal, request);
	}

	@GetMapping
	@Operation(summary = "List customers for the authenticated business (paginated, searchable)")
	public PagedCustomerResponse list(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) CustomerStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false, defaultValue = "displayName,asc") String sort) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return customerService.list(principal, q, status, page, size, sort);
	}

	@GetMapping("/{customerId}")
	@Operation(summary = "Get a customer by id (tenant-scoped; unknown/cross-tenant → 404)")
	public CustomerResponse get(@PathVariable UUID customerId) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return customerService.get(principal, customerId);
	}

	@PutMapping("/{customerId}")
	@Operation(summary = "Update a customer (tenant-scoped)")
	public CustomerResponse update(
			@PathVariable UUID customerId,
			@Valid @RequestBody UpdateCustomerRequest request) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return customerService.update(principal, customerId, request);
	}

	@PostMapping("/{customerId}/archive")
	@Operation(summary = "Archive a customer (soft deactivate; no hard delete)")
	public CustomerResponse archive(@PathVariable UUID customerId) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return customerService.archive(principal, customerId);
	}
}
