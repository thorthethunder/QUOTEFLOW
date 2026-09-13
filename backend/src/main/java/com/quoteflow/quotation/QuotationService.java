package com.quoteflow.quotation;

import com.quoteflow.business.Business;
import com.quoteflow.business.BusinessRepository;
import com.quoteflow.customer.Customer;
import com.quoteflow.customer.CustomerRepository;
import com.quoteflow.customer.CustomerStatus;
import com.quoteflow.invoice.InvoiceRepository;
import com.quoteflow.quotation.dto.CreateQuotationRequest;
import com.quoteflow.quotation.dto.PagedQuotationResponse;
import com.quoteflow.quotation.dto.QuotationItemRequest;
import com.quoteflow.quotation.dto.QuotationResponse;
import com.quoteflow.quotation.dto.QuotationSummaryResponse;
import com.quoteflow.quotation.dto.UpdateQuotationRequest;
import com.quoteflow.security.AuthenticatedUser;
import com.quoteflow.subscription.EntitlementService;
import com.quoteflow.subscription.FeatureKey;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class QuotationService {

	private static final Logger log = LoggerFactory.getLogger(QuotationService.class);

	public static final int DEFAULT_PAGE_SIZE = 20;
	public static final int MAX_PAGE_SIZE = 100;

	private static final Map<String, String> SORT_FIELDS = Map.of(
			"quotationNumber", "quotationNumber",
			"issueDate", "issueDate",
			"validUntil", "validUntil",
			"createdAt", "createdAt",
			"updatedAt", "updatedAt",
			"totalAmount", "totalAmount");

	private final QuotationRepository quotationRepository;
	private final CustomerRepository customerRepository;
	private final BusinessRepository businessRepository;
	private final DocumentNumberService documentNumberService;
	private final InvoiceRepository invoiceRepository;
	private final EntitlementService entitlementService;

	public QuotationService(
			QuotationRepository quotationRepository,
			CustomerRepository customerRepository,
			BusinessRepository businessRepository,
			DocumentNumberService documentNumberService,
			InvoiceRepository invoiceRepository,
			EntitlementService entitlementService) {
		this.quotationRepository = quotationRepository;
		this.customerRepository = customerRepository;
		this.businessRepository = businessRepository;
		this.documentNumberService = documentNumberService;
		this.invoiceRepository = invoiceRepository;
		this.entitlementService = entitlementService;
	}

	@Transactional
	public QuotationResponse create(AuthenticatedUser principal, CreateQuotationRequest request) {
		UUID businessId = principal.getBusinessId();
		Business business = businessRepository.findById(businessId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Business not available"));

		entitlementService.lockAndRequireQuota(businessId, FeatureKey.QUOTATIONS_MONTHLY);

		Customer customer = requireActiveCustomer(request.customerId(), businessId);
		LocalDate issueDate = request.issueDate() != null
				? request.issueDate()
				: todayInBusinessTimezone(business);
		validateDates(issueDate, request.validUntil());

		String currency = request.currency() != null && !request.currency().isBlank()
				? request.currency().trim().toUpperCase(Locale.ROOT)
				: business.getCurrency();

		DiscountType discountType = request.discountType() != null ? request.discountType() : DiscountType.NONE;
		BigDecimal discountValue = request.discountValue() != null ? request.discountValue() : BigDecimal.ZERO;
		BigDecimal taxRate = request.taxRate() != null ? request.taxRate() : BigDecimal.ZERO;

		QuotationCalculator.CalculationResult calc = calculate(request.items(), discountType, discountValue, taxRate);
		DocumentNumberService.AllocatedNumber number = documentNumberService.allocateQuotationNumber(businessId);

		Quotation quotation = new Quotation(
				business,
				customer,
				number.documentNumber(),
				number.sequenceValue(),
				currency,
				issueDate);
		quotation.setValidUntil(request.validUntil());
		quotation.setNotes(blankToNull(request.notes()));
		quotation.setTerms(blankToNull(request.terms()));
		quotation.applyCustomerSnapshot(customer);
		quotation.applyBusinessSnapshot(business);
		quotation.setShowQuoteFlowBranding(entitlementService.showQuoteFlowBranding(businessId));
		quotation.applyCalculation(calc, discountType, discountValue, taxRate);
		quotation.replaceItems(toItems(calc));

		Quotation saved = quotationRepository.save(quotation);
		log.info("quotation.event=created quotationId={} businessId={} number={}",
				saved.getId(), businessId, saved.getQuotationNumber());
		return toResponse(saved, businessId);
	}

	@Transactional(readOnly = true)
	public QuotationResponse get(AuthenticatedUser principal, UUID quotationId) {
		Quotation quotation = requireDetail(quotationId, principal.getBusinessId());
		return toResponse(quotation, principal.getBusinessId());
	}

	@Transactional(readOnly = true)
	public PagedQuotationResponse list(
			AuthenticatedUser principal,
			String q,
			QuotationStatus status,
			int page,
			int size,
			String sort) {
		Pageable pageable = toPageable(page, size, sort);
		Page<Quotation> result = quotationRepository.search(
				principal.getBusinessId(),
				status,
				blankToNull(q),
				pageable);
		return new PagedQuotationResponse(
				result.getContent().stream().map(QuotationSummaryResponse::from).toList(),
				result.getNumber(),
				result.getSize(),
				result.getTotalElements(),
				result.getTotalPages());
	}

	@Transactional
	public QuotationResponse update(AuthenticatedUser principal, UUID quotationId, UpdateQuotationRequest request) {
		Quotation quotation = requireDetail(quotationId, principal.getBusinessId());
		requireDraft(quotation);
		assertVersion(quotation, request.version());

		Customer customer = requireActiveCustomer(request.customerId(), principal.getBusinessId());
		validateDates(request.issueDate(), request.validUntil());

		DiscountType discountType = request.discountType();
		BigDecimal discountValue = request.discountValue();
		BigDecimal taxRate = request.taxRate();
		QuotationCalculator.CalculationResult calc = calculate(request.items(), discountType, discountValue, taxRate);

		quotation.setIssueDate(request.issueDate());
		quotation.setValidUntil(request.validUntil());
		quotation.setNotes(blankToNull(request.notes()));
		quotation.setTerms(blankToNull(request.terms()));
		quotation.applyCustomerSnapshot(customer);
		Business business = businessRepository.findById(principal.getBusinessId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Business not available"));
		quotation.applyBusinessSnapshot(business);
		quotation.applyCalculation(calc, discountType, discountValue, taxRate);
		quotation.replaceItems(toItems(calc));

		try {
			Quotation saved = quotationRepository.saveAndFlush(quotation);
			log.info("quotation.event=updated quotationId={} businessId={}", saved.getId(), principal.getBusinessId());
			return toResponse(saved, principal.getBusinessId());
		} catch (ObjectOptimisticLockingFailureException | OptimisticLockException ex) {
			throw conflict("Quotation was modified by another session. Reload and try again.");
		}
	}

	@Transactional
	public QuotationResponse markSent(AuthenticatedUser principal, UUID quotationId, Long version) {
		Quotation quotation = requireDetail(quotationId, principal.getBusinessId());
		requireDraft(quotation);
		if (version != null) {
			assertVersion(quotation, version);
		}
		if (quotation.getItems().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot mark as sent without line items");
		}
		Business business = businessRepository.findById(principal.getBusinessId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Business not available"));
		quotation.applyBusinessSnapshot(business);
		// Freeze branding entitlement at SEND (historical PDF snapshot).
		quotation.setShowQuoteFlowBranding(entitlementService.showQuoteFlowBranding(principal.getBusinessId()));
		quotation.setStatus(QuotationStatus.SENT);
		try {
			Quotation saved = quotationRepository.saveAndFlush(quotation);
			log.info("quotation.event=sent quotationId={} businessId={}", saved.getId(), principal.getBusinessId());
			return toResponse(saved, principal.getBusinessId());
		} catch (ObjectOptimisticLockingFailureException | OptimisticLockException ex) {
			throw conflict("Quotation was modified by another session. Reload and try again.");
		}
	}

	@Transactional
	public QuotationResponse cancel(AuthenticatedUser principal, UUID quotationId, Long version) {
		Quotation quotation = requireDetail(quotationId, principal.getBusinessId());
		if (quotation.getStatus() == QuotationStatus.CANCELLED) {
			return toResponse(quotation, principal.getBusinessId());
		}
		if (quotation.getStatus() != QuotationStatus.DRAFT && quotation.getStatus() != QuotationStatus.SENT) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Quotation cannot be cancelled from current status");
		}
		if (version != null) {
			assertVersion(quotation, version);
		}
		quotation.setStatus(QuotationStatus.CANCELLED);
		try {
			Quotation saved = quotationRepository.saveAndFlush(quotation);
			log.info("quotation.event=cancelled quotationId={} businessId={}", saved.getId(), principal.getBusinessId());
			return toResponse(saved, principal.getBusinessId());
		} catch (ObjectOptimisticLockingFailureException | OptimisticLockException ex) {
			throw conflict("Quotation was modified by another session. Reload and try again.");
		}
	}

	private QuotationResponse toResponse(Quotation quotation, UUID businessId) {
		return invoiceRepository.findBySourceQuotation_IdAndBusiness_Id(quotation.getId(), businessId)
				.map(invoice -> QuotationResponse.from(quotation, invoice.getId(), invoice.getInvoiceNumber()))
				.orElseGet(() -> QuotationResponse.from(quotation));
	}

	private QuotationCalculator.CalculationResult calculate(
			List<QuotationItemRequest> items,
			DiscountType discountType,
			BigDecimal discountValue,
			BigDecimal taxRate) {
		List<QuotationCalculator.LineInput> lines = new ArrayList<>();
		int position = 0;
		for (QuotationItemRequest item : items) {
			String description = item.description() == null ? "" : item.description().trim();
			if (description.isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item description is required");
			}
			lines.add(new QuotationCalculator.LineInput(position++, description, item.quantity(), item.unitPrice()));
		}
		try {
			return QuotationCalculator.calculate(lines, discountType, discountValue, taxRate);
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
	}

	private static List<QuotationItem> toItems(QuotationCalculator.CalculationResult calc) {
		return calc.lines().stream()
				.map(line -> new QuotationItem(
						line.position(),
						line.description(),
						line.quantity(),
						line.unitPrice(),
						line.lineSubtotal()))
				.toList();
	}

	private Customer requireActiveCustomer(UUID customerId, UUID businessId) {
		Customer customer = customerRepository.findByIdAndBusiness_Id(customerId, businessId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
		if (customer.getStatus() != CustomerStatus.ACTIVE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot use an archived customer");
		}
		return customer;
	}

	private Quotation requireDetail(UUID quotationId, UUID businessId) {
		return quotationRepository.findDetailByIdAndBusinessId(quotationId, businessId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found"));
	}

	private static void requireDraft(Quotation quotation) {
		if (quotation.getStatus() != QuotationStatus.DRAFT) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT quotations can be edited");
		}
	}

	private static void assertVersion(Quotation quotation, Long version) {
		if (version == null || quotation.getVersion() != version) {
			throw conflict("Quotation was modified by another session. Reload and try again.");
		}
	}

	private static ResponseStatusException conflict(String message) {
		return new ResponseStatusException(HttpStatus.CONFLICT, message);
	}

	private static void validateDates(LocalDate issueDate, LocalDate validUntil) {
		if (issueDate == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "issueDate is required");
		}
		if (validUntil != null && validUntil.isBefore(issueDate)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "validUntil must be on or after issueDate");
		}
	}

	private static LocalDate todayInBusinessTimezone(Business business) {
		try {
			return LocalDate.now(ZoneId.of(business.getTimezone()));
		} catch (Exception ex) {
			return LocalDate.now(ZoneId.of("UTC"));
		}
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
		Sort springSort = Sort.by(Sort.Direction.DESC, "createdAt");
		if (sort != null && !sort.isBlank()) {
			String[] parts = sort.split(",");
			String mapped = SORT_FIELDS.get(parts[0].trim());
			if (mapped == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort field");
			}
			Sort.Direction direction = Sort.Direction.ASC;
			if (parts.length > 1) {
				String dir = parts[1].trim().toLowerCase(Locale.ROOT);
				if (!Set.of("asc", "desc").contains(dir)) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort direction");
				}
				direction = Sort.Direction.fromString(dir);
			}
			springSort = Sort.by(direction, mapped);
		}
		return PageRequest.of(page, pageSize, springSort);
	}
}
