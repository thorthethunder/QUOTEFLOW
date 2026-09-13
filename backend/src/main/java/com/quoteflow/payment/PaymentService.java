package com.quoteflow.payment;

import com.quoteflow.business.Business;
import com.quoteflow.business.BusinessRepository;
import com.quoteflow.common.api.DomainApiException;
import com.quoteflow.finance.FinancialDocumentCalculator;
import com.quoteflow.invoice.Invoice;
import com.quoteflow.invoice.InvoiceRepository;
import com.quoteflow.invoice.InvoiceStatus;
import com.quoteflow.payment.dto.CreatePaymentRequest;
import com.quoteflow.payment.dto.InvoicePaymentsResponse;
import com.quoteflow.payment.dto.PaymentResponse;
import com.quoteflow.payment.dto.PaymentSummaryResponse;
import com.quoteflow.payment.dto.VoidPaymentRequest;
import com.quoteflow.quotation.DocumentNumberService;
import com.quoteflow.security.AuthenticatedUser;
import com.quoteflow.subscription.EntitlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

	private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

	private final PaymentRepository paymentRepository;
	private final InvoiceRepository invoiceRepository;
	private final BusinessRepository businessRepository;
	private final DocumentNumberService documentNumberService;
	private final EntitlementService entitlementService;

	public PaymentService(
			PaymentRepository paymentRepository,
			InvoiceRepository invoiceRepository,
			BusinessRepository businessRepository,
			DocumentNumberService documentNumberService,
			EntitlementService entitlementService) {
		this.paymentRepository = paymentRepository;
		this.invoiceRepository = invoiceRepository;
		this.businessRepository = businessRepository;
		this.documentNumberService = documentNumberService;
		this.entitlementService = entitlementService;
	}

	@Transactional
	public PaymentResponse record(AuthenticatedUser principal, UUID invoiceId, CreatePaymentRequest request) {
		UUID businessId = principal.getBusinessId();
		Business business = businessRepository.findById(businessId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Business not available"));

		Invoice invoice = invoiceRepository.findByIdAndBusinessIdForUpdate(invoiceId, businessId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));

		requirePayable(invoice);

		BigDecimal amount = normalizeMoney(request.amount());
		if (amount.signum() <= 0) {
			throw new DomainApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Payment amount must be greater than zero");
		}

		PaymentSummaryCalculator.PaymentSummary current = summarizeInvoice(invoice, businessId);
		if (amount.compareTo(current.balanceDue()) > 0) {
			throw new DomainApiException(
					HttpStatus.CONFLICT,
					"PAYMENT_EXCEEDS_BALANCE",
					"Payment amount exceeds remaining balance due",
					Map.of("balanceDue", current.balanceDue()));
		}

		LocalDate paymentDate = request.paymentDate() != null
				? request.paymentDate()
				: todayInBusinessTimezone(business);

		DocumentNumberService.AllocatedNumber receipt = documentNumberService.allocateReceiptNumber(businessId);
		BigDecimal remaining = current.balanceDue().subtract(amount)
				.setScale(FinancialDocumentCalculator.MONEY_SCALE, FinancialDocumentCalculator.ROUNDING);

		Payment payment = new Payment(
				business,
				invoice,
				receipt.documentNumber(),
				receipt.sequenceValue(),
				amount,
				invoice.getCurrency(),
				paymentDate,
				request.paymentMethod(),
				blankToNull(request.reference()),
				blankToNull(request.notes()),
				invoice.getInvoiceNumber(),
				invoice.getTotalAmount().setScale(
						FinancialDocumentCalculator.MONEY_SCALE, FinancialDocumentCalculator.ROUNDING),
				current.amountPaid(),
				remaining,
				principal.getUserId(),
				entitlementService.showQuoteFlowBranding(businessId));

		Payment saved = paymentRepository.saveAndFlush(payment);
		PaymentSummaryCalculator.PaymentSummary after = summarizeInvoice(invoice, businessId);
		log.info("payment.event=recorded paymentId={} businessId={} invoiceId={} receipt={}",
				saved.getId(), businessId, invoiceId, saved.getReceiptNumber());
		return PaymentResponse.from(saved, PaymentSummaryResponse.from(after));
	}

	@Transactional(readOnly = true)
	public InvoicePaymentsResponse listForInvoice(AuthenticatedUser principal, UUID invoiceId) {
		UUID businessId = principal.getBusinessId();
		Invoice invoice = invoiceRepository.findDetailByIdAndBusinessId(invoiceId, businessId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
		List<Payment> payments = paymentRepository.findByInvoiceOrdered(invoiceId, businessId);
		PaymentSummaryCalculator.PaymentSummary summary = summarizeInvoice(invoice, businessId);
		return new InvoicePaymentsResponse(
				PaymentSummaryResponse.from(summary),
				payments.stream().map(PaymentResponse::from).toList());
	}

	@Transactional(readOnly = true)
	public PaymentResponse get(AuthenticatedUser principal, UUID paymentId) {
		Payment payment = paymentRepository.findDetailByIdAndBusinessId(paymentId, principal.getBusinessId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
		PaymentSummaryCalculator.PaymentSummary summary =
				summarizeInvoice(payment.getInvoice(), principal.getBusinessId());
		return PaymentResponse.from(payment, PaymentSummaryResponse.from(summary));
	}

	@Transactional
	public PaymentResponse voidPayment(AuthenticatedUser principal, UUID paymentId, VoidPaymentRequest request) {
		UUID businessId = principal.getBusinessId();
		Payment payment = paymentRepository.findDetailByIdAndBusinessId(paymentId, businessId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));

		Invoice invoice = invoiceRepository.findByIdAndBusinessIdForUpdate(payment.getInvoiceId(), businessId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));

		// Re-read payment under the invoice lock so concurrent voids serialize.
		Payment locked = paymentRepository.findByIdAndBusiness_Id(paymentId, businessId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));

		if (locked.getStatus() == PaymentRecordStatus.VOIDED) {
			throw new DomainApiException(
					HttpStatus.CONFLICT,
					"PAYMENT_ALREADY_VOIDED",
					"Payment is already voided");
		}

		locked.voidPayment(principal.getUserId(), blankToNull(request != null ? request.reason() : null), Instant.now());
		Payment saved = paymentRepository.saveAndFlush(locked);
		PaymentSummaryCalculator.PaymentSummary after = summarizeInvoice(invoice, businessId);
		log.info("payment.event=voided paymentId={} businessId={} invoiceId={}",
				saved.getId(), businessId, invoice.getId());
		return PaymentResponse.from(saved, PaymentSummaryResponse.from(after));
	}

	@Transactional(readOnly = true)
	public PaymentSummaryResponse summaryForInvoice(Invoice invoice, UUID businessId) {
		return PaymentSummaryResponse.from(summarizeInvoice(invoice, businessId));
	}

	@Transactional(readOnly = true)
	public Map<UUID, PaymentSummaryResponse> summariesForInvoices(UUID businessId, List<Invoice> invoices) {
		if (invoices.isEmpty()) {
			return Map.of();
		}
		List<UUID> ids = invoices.stream().map(Invoice::getId).toList();
		Map<UUID, BigDecimal> paidByInvoice = new java.util.HashMap<>();
		for (Object[] row : paymentRepository.sumRecordedByInvoiceIds(businessId, ids)) {
			paidByInvoice.put((UUID) row[0], (BigDecimal) row[1]);
		}
		Map<UUID, PaymentSummaryResponse> result = new java.util.HashMap<>();
		for (Invoice invoice : invoices) {
			BigDecimal paid = paidByInvoice.getOrDefault(invoice.getId(), BigDecimal.ZERO);
			List<BigDecimal> amounts = paid.signum() == 0
					? List.of()
					: List.of(paid.setScale(FinancialDocumentCalculator.MONEY_SCALE, FinancialDocumentCalculator.ROUNDING));
			// sum query already aggregates; pass as single synthetic amount when > 0
			PaymentSummaryCalculator.PaymentSummary summary = paid.signum() == 0
					? PaymentSummaryCalculator.summarize(invoice.getTotalAmount(), List.of())
					: PaymentSummaryCalculator.summarize(invoice.getTotalAmount(), amounts);
			result.put(invoice.getId(), PaymentSummaryResponse.from(summary));
		}
		return result;
	}

	public boolean hasActivePayments(UUID invoiceId, UUID businessId) {
		return paymentRepository.countRecordedByInvoice(invoiceId, businessId) > 0;
	}

	private PaymentSummaryCalculator.PaymentSummary summarizeInvoice(Invoice invoice, UUID businessId) {
		BigDecimal paid = paymentRepository.sumRecordedAmount(invoice.getId(), businessId);
		if (paid == null) {
			paid = BigDecimal.ZERO;
		}
		paid = paid.setScale(FinancialDocumentCalculator.MONEY_SCALE, FinancialDocumentCalculator.ROUNDING);
		if (paid.signum() == 0) {
			return PaymentSummaryCalculator.summarize(invoice.getTotalAmount(), List.of());
		}
		return PaymentSummaryCalculator.summarize(invoice.getTotalAmount(), List.of(paid));
	}

	private static void requirePayable(Invoice invoice) {
		if (invoice.getStatus() != InvoiceStatus.SENT) {
			throw new DomainApiException(
					HttpStatus.CONFLICT,
					"INVOICE_NOT_PAYABLE",
					"Only SENT invoices can receive payments");
		}
	}

	private static BigDecimal normalizeMoney(BigDecimal amount) {
		return amount.setScale(FinancialDocumentCalculator.MONEY_SCALE, FinancialDocumentCalculator.ROUNDING);
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
}
