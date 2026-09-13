package com.quoteflow.customer;

import com.quoteflow.business.Business;
import com.quoteflow.business.BusinessRepository;
import com.quoteflow.common.EmailNormalizer;
import com.quoteflow.customer.dto.CreateCustomerRequest;
import com.quoteflow.customer.dto.CustomerResponse;
import com.quoteflow.customer.dto.CustomerSummaryResponse;
import com.quoteflow.customer.dto.PagedCustomerResponse;
import com.quoteflow.customer.dto.UpdateCustomerRequest;
import com.quoteflow.security.AuthenticatedUser;
import com.quoteflow.subscription.EntitlementService;
import com.quoteflow.subscription.FeatureKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CustomerService {

	private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

	public static final int DEFAULT_PAGE_SIZE = 20;
	public static final int MAX_PAGE_SIZE = 100;

	private static final Map<String, String> SORT_FIELDS = Map.of(
			"displayName", "displayName",
			"createdAt", "createdAt",
			"updatedAt", "updatedAt");

	private static final Set<String> SORT_DIRECTIONS = Set.of("asc", "desc");

	private final CustomerRepository customerRepository;
	private final BusinessRepository businessRepository;
	private final EntitlementService entitlementService;

	public CustomerService(
			CustomerRepository customerRepository,
			BusinessRepository businessRepository,
			EntitlementService entitlementService) {
		this.customerRepository = customerRepository;
		this.businessRepository = businessRepository;
		this.entitlementService = entitlementService;
	}

	@Transactional
	public CustomerResponse create(AuthenticatedUser principal, CreateCustomerRequest request) {
		UUID businessId = principal.getBusinessId();
		Business business = businessRepository.findById(businessId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Business not available"));

		entitlementService.lockAndRequireQuota(businessId, FeatureKey.CUSTOMERS);

		Customer customer = new Customer(business, requireDisplayName(request.displayName()));
		applyMutableFields(
				customer,
				request.email(),
				request.phone(),
				request.companyName(),
				request.addressLine1(),
				request.addressLine2(),
				request.city(),
				request.stateRegion(),
				request.postalCode(),
				request.countryCode(),
				request.taxId(),
				request.notes());

		Customer saved = customerRepository.save(customer);
		log.info("customer.event=created customerId={} businessId={}", saved.getId(), businessId);
		return CustomerResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public CustomerResponse get(AuthenticatedUser principal, UUID customerId) {
		Customer customer = requireTenantCustomer(customerId, principal.getBusinessId());
		return CustomerResponse.from(customer);
	}

	@Transactional(readOnly = true)
	public PagedCustomerResponse list(
			AuthenticatedUser principal,
			String q,
			CustomerStatus status,
			int page,
			int size,
			String sort) {
		Pageable pageable = toPageable(page, size, sort);
		String query = blankToNull(q);
		CustomerStatus effectiveStatus = status != null ? status : CustomerStatus.ACTIVE;

		Page<Customer> result = customerRepository.search(
				principal.getBusinessId(),
				effectiveStatus,
				query,
				pageable);

		return new PagedCustomerResponse(
				result.getContent().stream().map(CustomerSummaryResponse::from).toList(),
				result.getNumber(),
				result.getSize(),
				result.getTotalElements(),
				result.getTotalPages());
	}

	@Transactional
	public CustomerResponse update(AuthenticatedUser principal, UUID customerId, UpdateCustomerRequest request) {
		Customer customer = requireTenantCustomer(customerId, principal.getBusinessId());
		customer.setDisplayName(requireDisplayName(request.displayName()));
		applyMutableFields(
				customer,
				request.email(),
				request.phone(),
				request.companyName(),
				request.addressLine1(),
				request.addressLine2(),
				request.city(),
				request.stateRegion(),
				request.postalCode(),
				request.countryCode(),
				request.taxId(),
				request.notes());

		Customer saved = customerRepository.save(customer);
		log.info("customer.event=updated customerId={} businessId={}", saved.getId(), principal.getBusinessId());
		return CustomerResponse.from(saved);
	}

	@Transactional
	public CustomerResponse archive(AuthenticatedUser principal, UUID customerId) {
		Customer customer = requireTenantCustomer(customerId, principal.getBusinessId());
		if (customer.getStatus() == CustomerStatus.ARCHIVED) {
			return CustomerResponse.from(customer);
		}
		customer.setStatus(CustomerStatus.ARCHIVED);
		Customer saved = customerRepository.save(customer);
		log.info("customer.event=archived customerId={} businessId={}", saved.getId(), principal.getBusinessId());
		return CustomerResponse.from(saved);
	}

	private Customer requireTenantCustomer(UUID customerId, UUID businessId) {
		return customerRepository.findByIdAndBusiness_Id(customerId, businessId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
	}

	private static String requireDisplayName(String displayName) {
		String trimmed = displayName == null ? "" : displayName.trim();
		if (trimmed.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName is required");
		}
		return trimmed;
	}

	private static void applyMutableFields(
			Customer customer,
			String email,
			String phone,
			String companyName,
			String addressLine1,
			String addressLine2,
			String city,
			String stateRegion,
			String postalCode,
			String countryCode,
			String taxId,
			String notes) {
		String normalizedEmail = blankToNull(email);
		customer.setEmail(normalizedEmail == null ? null : EmailNormalizer.normalize(normalizedEmail));
		customer.setPhone(blankToNull(phone));
		customer.setCompanyName(blankToNull(companyName));
		customer.setAddressLine1(blankToNull(addressLine1));
		customer.setAddressLine2(blankToNull(addressLine2));
		customer.setCity(blankToNull(city));
		customer.setStateRegion(blankToNull(stateRegion));
		customer.setPostalCode(blankToNull(postalCode));
		String country = blankToNull(countryCode);
		customer.setCountryCode(country == null ? null : country.toUpperCase(Locale.ROOT));
		customer.setTaxId(blankToNull(taxId));
		customer.setNotes(blankToNull(notes));
	}

	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	static Pageable toPageable(int page, int size, String sort) {
		if (page < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0");
		}
		int pageSize = size <= 0 ? DEFAULT_PAGE_SIZE : size;
		if (pageSize > MAX_PAGE_SIZE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be <= " + MAX_PAGE_SIZE);
		}

		Sort springSort = Sort.by(Sort.Direction.ASC, "displayName");
		if (sort != null && !sort.isBlank()) {
			String[] parts = sort.split(",");
			String fieldKey = parts[0].trim();
			String mapped = SORT_FIELDS.get(fieldKey);
			if (mapped == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort field");
			}
			Sort.Direction direction = Sort.Direction.ASC;
			if (parts.length > 1) {
				String dir = parts[1].trim().toLowerCase(Locale.ROOT);
				if (!SORT_DIRECTIONS.contains(dir)) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort direction");
				}
				direction = Sort.Direction.fromString(dir);
			}
			springSort = Sort.by(direction, mapped);
		}
		return PageRequest.of(page, pageSize, springSort);
	}
}
