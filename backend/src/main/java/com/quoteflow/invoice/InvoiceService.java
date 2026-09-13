package com.quoteflow.invoice;

import com.quoteflow.business.Business;
import com.quoteflow.business.BusinessRepository;
import com.quoteflow.customer.Customer;
import com.quoteflow.customer.CustomerRepository;
import com.quoteflow.customer.CustomerStatus;
import com.quoteflow.finance.DiscountType;
import com.quoteflow.finance.FinancialDocumentCalculator;
import com.quoteflow.invoice.dto.CreateInvoiceRequest;
import com.quoteflow.invoice.dto.InvoiceItemRequest;
import com.quoteflow.invoice.dto.InvoiceResponse;
import com.quoteflow.invoice.dto.InvoiceSummaryResponse;
import com.quoteflow.invoice.dto.PagedInvoiceResponse;
import com.quoteflow.invoice.dto.UpdateInvoiceRequest;
import com.quoteflow.payment.PaymentService;
import com.quoteflow.payment.dto.PaymentSummaryResponse;
import com.quoteflow.common.api.DomainApiException;
import com.quoteflow.quotation.DocumentNumberService;
import com.quoteflow.quotation.Quotation;
import com.quoteflow.quotation.QuotationItem;
import com.quoteflow.quotation.QuotationRepository;
import com.quoteflow.quotation.QuotationStatus;
import com.quoteflow.security.AuthenticatedUser;
import com.quoteflow.subscription.EntitlementService;
import com.quoteflow.subscription.FeatureKey;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
public class InvoiceService {

	private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

	public static final int DEFAULT_PAGE_SIZE = 20;
	public static final int MAX_PAGE_SIZE = 100;

	private static final Map<String, String> SORT_FIELDS = Map.of(
			"invoiceNumber", "invoiceNumber",
			"issueDate", "issueDate",
			"dueDate", "dueDate",
			"createdAt", "createdAt",
			"updatedAt", "updatedAt",
			"totalAmount", "totalAmount");

	private final InvoiceRepository invoiceRepository;
	private final QuotationRepository quotationRepository;
	private final CustomerRepository customerRepository;
	private final BusinessRepository businessRepository;
	private final DocumentNumberService documentNumberService;
	private final InvoiceConflictLookup invoiceConflictLookup;
	private final PaymentService paymentService;
	private final EntitlementService entitlementService;

	public InvoiceService(
			InvoiceRepository invoiceRepository,
			QuotationRepository quotationRepository,
			CustomerRepository customerRepository,
			BusinessRepository businessRepository,
			DocumentNumberService documentNumberService,
			InvoiceConflictLookup invoiceConflictLookup,
			PaymentService paymentService,
			EntitlementService entitlementService) {
		this.invoiceRepository = invoiceRepository;
		this.quotationRepository = quotationRepository;
		this.customerRepository = customerRepository;
		this.businessRepository = businessRepository;
		this.documentNumberService = documentNumberService;
		this.invoiceConflictLookup = invoiceConflictLookup;
		this.paymentService = paymentService;
		this.entitlementService = entitlementService;
	}

	@Transactional
	public InvoiceResponse create(AuthenticatedUser principal, CreateInvoiceRequest request) {
		UUID businessId = principal.getBusinessId();
		Business business = requireBusiness(businessId);
		entitlementService.lockAndRequireQuota(businessId, FeatureKey.INVOICES_MONTHLY);
		Customer customer = requireActiveCustomer(request.customerId(), businessId);

		LocalDate issueDate = request.issueDate() != null
				? request.issueDate()
				: todayInBusinessTimezone(business);
		validateDates(issueDate, request.dueDate());

		String currency = request.currency() != null && !request.currency().isBlank()
				? request.currency().trim().toUpperCase(Locale.ROOT)
				: business.getCurrency();

		DiscountType discountType = request.discountType() != null ? request.discountType() : DiscountType.NONE;
		BigDecimal discountValue = request.discountValue() != null ? request.discountValue() : BigDecimal.ZERO;
		BigDecimal taxRate = request.taxRate() != null ? request.taxRate() : BigDecimal.ZERO;

		FinancialDocumentCalculator.CalculationResult calc =
				calculate(request.items(), discountType, discountValue, taxRate);
		DocumentNumberService.AllocatedNumber number = documentNumberService.allocateInvoiceNumber(businessId);

		Invoice invoice = new Invoice(
				business,
				customer,
				number.documentNumber(),
				number.sequenceValue(),
				currency,
				issueDate);
		invoice.setDueDate(request.dueDate());
		invoice.setNotes(blankToNull(request.notes()));
		invoice.setTerms(blankToNull(request.terms()));
		invoice.applyCustomerSnapshot(customer);
		invoice.applyBusinessSnapshot(business);
		invoice.applyCalculation(calc, discountType, discountValue, taxRate);
		invoice.replaceItems(toItems(calc));

		Invoice saved = invoiceRepository.save(invoice);
		log.info("invoice.event=created invoiceId={} businessId={} number={}",
				saved.getId(), businessId, saved.getInvoiceNumber());
		return toResponse(saved, businessId);
	}

	@Transactional
	public InvoiceResponse convertFromQuotation(AuthenticatedUser principal, UUID quotationId) {
		UUID businessId = principal.getBusinessId();
		Business business = requireBusiness(businessId);

		// Serialize plan-sensitive invoice creation with other invoice creates for this tenant.
		entitlementService.lockAndRequireQuota(businessId, FeatureKey.INVOICES_MONTHLY);

		// Serialize concurrent conversions on the same quotation row.
		Quotation quotation = quotationRepository.findDetailByIdAndBusinessIdForUpdate(quotationId, businessId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found"));

		invoiceRepository.findBySourceQuotation_IdAndBusiness_Id(quotationId, businessId)
				.ifPresent(existing -> {
					throw new QuotationAlreadyInvoicedException(existing.getId());
				});

		if (quotation.getStatus() != QuotationStatus.SENT) {
			throw new ResponseStatusException(
					HttpStatus.CONFLICT,
					"Only SENT quotations can be converted to an invoice");
		}

		DiscountType discountType = mapDiscount(quotation.getDiscountType());
		List<FinancialDocumentCalculator.LineInput> lines = new ArrayList<>();
		for (QuotationItem item : quotation.getItems()) {
			lines.add(new FinancialDocumentCalculator.LineInput(
					item.getPosition(),
					item.getDescription(),
					item.getQuantity(),
					item.getUnitPrice()));
		}

		FinancialDocumentCalculator.CalculationResult calc;
		try {
			calc = FinancialDocumentCalculator.calculate(
					lines, discountType, quotation.getDiscountValue(), quotation.getTaxRate());
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}

		assertTotalsMatch(quotation, calc);

		DocumentNumberService.AllocatedNumber number = documentNumberService.allocateInvoiceNumber(businessId);
		Customer customer = quotation.getCustomer();

		Invoice invoice = new Invoice(
				business,
				customer,
				number.documentNumber(),
				number.sequenceValue(),
				quotation.getCurrency(),
				quotation.getIssueDate());
		invoice.setSourceQuotation(quotation);
		LocalDate dueDate = quotation.getValidUntil();
		if (dueDate != null && !dueDate.isBefore(quotation.getIssueDate())) {
			invoice.setDueDate(dueDate);
		}
		invoice.setNotes(quotation.getNotes());
		invoice.setTerms(quotation.getTerms());
		invoice.copyCustomerSnapshotFromQuotation(quotation);
		invoice.copyBusinessSnapshotFromQuotation(quotation);
		invoice.applyCalculation(calc, discountType, quotation.getDiscountValue(), quotation.getTaxRate());
		invoice.replaceItems(toItems(calc));

		try {
			Invoice saved = invoiceRepository.saveAndFlush(invoice);
			log.info("invoice.event=converted invoiceId={} businessId={} quotationId={} number={}",
					saved.getId(), businessId, quotationId, saved.getInvoiceNumber());
			return toResponse(saved, businessId);
		} catch (DataIntegrityViolationException ex) {
			UUID existingId = invoiceConflictLookup.findInvoiceIdBySourceQuotation(quotationId, businessId)
					.orElse(null);
			if (existingId != null) {
				throw new QuotationAlreadyInvoicedException(existingId);
			}
			throw ex;
		}
	}

	@Transactional(readOnly = true)
	public InvoiceResponse get(AuthenticatedUser principal, UUID invoiceId) {
		Invoice invoice = requireDetail(invoiceId, principal.getBusinessId());
		return toResponse(invoice, principal.getBusinessId());
	}

	@Transactional(readOnly = true)
	public PagedInvoiceResponse list(
			AuthenticatedUser principal,
			String q,
			InvoiceStatus status,
			int page,
			int size,
			String sort) {
		Pageable pageable = toPageable(page, size, sort);
		Page<Invoice> result = invoiceRepository.search(
				principal.getBusinessId(),
				status,
				blankToNull(q),
				pageable);
		Map<UUID, PaymentSummaryResponse> summaries =
				paymentService.summariesForInvoices(principal.getBusinessId(), result.getContent());
		return new PagedInvoiceResponse(
				result.getContent().stream()
						.map(inv -> InvoiceSummaryResponse.from(
								inv, summaries.getOrDefault(inv.getId(), PaymentSummaryResponse.unpaid(inv.getTotalAmount()))))
						.toList(),
				result.getNumber(),
				result.getSize(),
				result.getTotalElements(),
				result.getTotalPages());
	}

	@Transactional
	public InvoiceResponse update(AuthenticatedUser principal, UUID invoiceId, UpdateInvoiceRequest request) {
		Invoice invoice = requireDetail(invoiceId, principal.getBusinessId());
		requireDraft(invoice);
		assertVersion(invoice, request.version());

		Customer customer = requireActiveCustomer(request.customerId(), principal.getBusinessId());
		validateDates(request.issueDate(), request.dueDate());

		DiscountType discountType = request.discountType();
		BigDecimal discountValue = request.discountValue();
		BigDecimal taxRate = request.taxRate();
		FinancialDocumentCalculator.CalculationResult calc =
				calculate(request.items(), discountType, discountValue, taxRate);

		Business business = requireBusiness(principal.getBusinessId());
		invoice.setIssueDate(request.issueDate());
		invoice.setDueDate(request.dueDate());
		invoice.setNotes(blankToNull(request.notes()));
		invoice.setTerms(blankToNull(request.terms()));
		invoice.applyCustomerSnapshot(customer);
		invoice.applyBusinessSnapshot(business);
		invoice.applyCalculation(calc, discountType, discountValue, taxRate);
		invoice.replaceItems(toItems(calc));

		try {
			Invoice saved = invoiceRepository.saveAndFlush(invoice);
			log.info("invoice.event=updated invoiceId={} businessId={}", saved.getId(), principal.getBusinessId());
			return toResponse(saved, principal.getBusinessId());
		} catch (ObjectOptimisticLockingFailureException | OptimisticLockException ex) {
			throw conflict("Invoice was modified by another session. Reload and try again.");
		}
	}

	@Transactional
	public InvoiceResponse markSent(AuthenticatedUser principal, UUID invoiceId, Long version) {
		Invoice invoice = requireDetail(invoiceId, principal.getBusinessId());
		requireDraft(invoice);
		if (version != null) {
			assertVersion(invoice, version);
		}
		if (invoice.getItems().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot mark as sent without line items");
		}
		invoice.setStatus(InvoiceStatus.SENT);
		try {
			Invoice saved = invoiceRepository.saveAndFlush(invoice);
			log.info("invoice.event=sent invoiceId={} businessId={}", saved.getId(), principal.getBusinessId());
			return toResponse(saved, principal.getBusinessId());
		} catch (ObjectOptimisticLockingFailureException | OptimisticLockException ex) {
			throw conflict("Invoice was modified by another session. Reload and try again.");
		}
	}

	@Transactional
	public InvoiceResponse cancel(AuthenticatedUser principal, UUID invoiceId, Long version) {
		Invoice invoice = requireDetail(invoiceId, principal.getBusinessId());
		if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
			return toResponse(invoice, principal.getBusinessId());
		}
		if (invoice.getStatus() != InvoiceStatus.DRAFT && invoice.getStatus() != InvoiceStatus.SENT) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice cannot be cancelled from current status");
		}
		if (paymentService.hasActivePayments(invoiceId, principal.getBusinessId())) {
			throw new DomainApiException(
					HttpStatus.CONFLICT,
					"INVOICE_HAS_PAYMENTS",
					"Invoice with active payments cannot be cancelled");
		}
		if (version != null) {
			assertVersion(invoice, version);
		}
		invoice.setStatus(InvoiceStatus.CANCELLED);
		try {
			Invoice saved = invoiceRepository.saveAndFlush(invoice);
			log.info("invoice.event=cancelled invoiceId={} businessId={}", saved.getId(), principal.getBusinessId());
			return toResponse(saved, principal.getBusinessId());
		} catch (ObjectOptimisticLockingFailureException | OptimisticLockException ex) {
			throw conflict("Invoice was modified by another session. Reload and try again.");
		}
	}

	private InvoiceResponse toResponse(Invoice invoice, UUID businessId) {
		return InvoiceResponse.from(invoice, paymentService.summaryForInvoice(invoice, businessId));
	}

	private FinancialDocumentCalculator.CalculationResult calculate(
			List<InvoiceItemRequest> items,
			DiscountType discountType,
			BigDecimal discountValue,
			BigDecimal taxRate) {
		List<FinancialDocumentCalculator.LineInput> lines = new ArrayList<>();
		int position = 0;
		for (InvoiceItemRequest item : items) {
			String description = item.description() == null ? "" : item.description().trim();
			if (description.isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item description is required");
			}
			lines.add(new FinancialDocumentCalculator.LineInput(
					position++, description, item.quantity(), item.unitPrice()));
		}
		try {
			return FinancialDocumentCalculator.calculate(lines, discountType, discountValue, taxRate);
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
	}

	private static List<InvoiceItem> toItems(FinancialDocumentCalculator.CalculationResult calc) {
		return calc.lines().stream()
				.map(line -> new InvoiceItem(
						line.position(),
						line.description(),
						line.quantity(),
						line.unitPrice(),
						line.lineSubtotal()))
				.toList();
	}

	private static void assertTotalsMatch(Quotation quotation, FinancialDocumentCalculator.CalculationResult calc) {
		if (calc.subtotal().compareTo(quotation.getSubtotal()) != 0
				|| calc.discountAmount().compareTo(quotation.getDiscountAmount()) != 0
				|| calc.taxAmount().compareTo(quotation.getTaxAmount()) != 0
				|| calc.totalAmount().compareTo(quotation.getTotalAmount()) != 0) {
			throw new ResponseStatusException(
					HttpStatus.CONFLICT,
					"Quotation financial totals are inconsistent; conversion aborted");
		}
	}

	private static DiscountType mapDiscount(com.quoteflow.quotation.DiscountType type) {
		return switch (type) {
			case NONE -> DiscountType.NONE;
			case PERCENTAGE -> DiscountType.PERCENTAGE;
			case FIXED -> DiscountType.FIXED;
		};
	}

	private Customer requireActiveCustomer(UUID customerId, UUID businessId) {
		Customer customer = customerRepository.findByIdAndBusiness_Id(customerId, businessId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
		if (customer.getStatus() != CustomerStatus.ACTIVE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot use an archived customer");
		}
		return customer;
	}

	private Business requireBusiness(UUID businessId) {
		return businessRepository.findById(businessId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Business not available"));
	}

	private Invoice requireDetail(UUID invoiceId, UUID businessId) {
		return invoiceRepository.findDetailByIdAndBusinessId(invoiceId, businessId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
	}

	private static void requireDraft(Invoice invoice) {
		if (invoice.getStatus() != InvoiceStatus.DRAFT) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT invoices can be edited");
		}
	}

	private static void assertVersion(Invoice invoice, Long version) {
		if (version == null || invoice.getVersion() != version) {
			throw conflict("Invoice was modified by another session. Reload and try again.");
		}
	}

	private static ResponseStatusException conflict(String message) {
		return new ResponseStatusException(HttpStatus.CONFLICT, message);
	}

	private static void validateDates(LocalDate issueDate, LocalDate dueDate) {
		if (issueDate == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "issueDate is required");
		}
		if (dueDate != null && dueDate.isBefore(issueDate)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dueDate must be on or after issueDate");
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
