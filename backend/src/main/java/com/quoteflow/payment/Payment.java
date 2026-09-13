package com.quoteflow.payment;

import com.quoteflow.business.Business;
import com.quoteflow.common.BaseAuditableEntity;
import com.quoteflow.invoice.Invoice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment extends BaseAuditableEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "business_id", nullable = false, updatable = false)
	private Business business;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "invoice_id", nullable = false, updatable = false)
	private Invoice invoice;

	@Column(name = "receipt_number", nullable = false, length = 40, updatable = false)
	private String receiptNumber;

	@Column(name = "receipt_sequence_value", nullable = false, updatable = false)
	private long receiptSequenceValue;

	@Column(nullable = false, precision = 19, scale = 4, updatable = false)
	private BigDecimal amount;

	@Column(nullable = false, length = 3, updatable = false)
	private String currency;

	@Column(name = "payment_date", nullable = false, updatable = false)
	private LocalDate paymentDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_method", nullable = false, length = 40, updatable = false)
	private PaymentMethod paymentMethod;

	@Column(length = 200, updatable = false)
	private String reference;

	@Column(length = 2000, updatable = false)
	private String notes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentRecordStatus status;

	@Column(name = "invoice_number_snapshot", nullable = false, length = 40, updatable = false)
	private String invoiceNumberSnapshot;

	@Column(name = "invoice_total_at_payment", nullable = false, precision = 19, scale = 4, updatable = false)
	private BigDecimal invoiceTotalAtPayment;

	@Column(name = "previous_paid_amount", nullable = false, precision = 19, scale = 4, updatable = false)
	private BigDecimal previousPaidAmount;

	@Column(name = "remaining_balance_after_payment", nullable = false, precision = 19, scale = 4, updatable = false)
	private BigDecimal remainingBalanceAfterPayment;

	@Column(name = "created_by_user_id", updatable = false)
	private UUID createdByUserId;

	@Column(name = "voided_at")
	private Instant voidedAt;

	@Column(name = "voided_by_user_id")
	private UUID voidedByUserId;

	@Column(name = "void_reason", length = 500)
	private String voidReason;

	@Column(name = "show_quoteflow_branding", nullable = false, updatable = false)
	private boolean showQuoteFlowBranding = true;

	protected Payment() {
	}

	public Payment(
			Business business,
			Invoice invoice,
			String receiptNumber,
			long receiptSequenceValue,
			BigDecimal amount,
			String currency,
			LocalDate paymentDate,
			PaymentMethod paymentMethod,
			String reference,
			String notes,
			String invoiceNumberSnapshot,
			BigDecimal invoiceTotalAtPayment,
			BigDecimal previousPaidAmount,
			BigDecimal remainingBalanceAfterPayment,
			UUID createdByUserId,
			boolean showQuoteFlowBranding) {
		this.id = UUID.randomUUID();
		this.business = business;
		this.invoice = invoice;
		this.receiptNumber = receiptNumber;
		this.receiptSequenceValue = receiptSequenceValue;
		this.amount = amount;
		this.currency = currency;
		this.paymentDate = paymentDate;
		this.paymentMethod = paymentMethod;
		this.reference = reference;
		this.notes = notes;
		this.status = PaymentRecordStatus.RECORDED;
		this.invoiceNumberSnapshot = invoiceNumberSnapshot;
		this.invoiceTotalAtPayment = invoiceTotalAtPayment;
		this.previousPaidAmount = previousPaidAmount;
		this.remainingBalanceAfterPayment = remainingBalanceAfterPayment;
		this.createdByUserId = createdByUserId;
		this.showQuoteFlowBranding = showQuoteFlowBranding;
	}

	public void voidPayment(UUID voidedByUserId, String reason, Instant voidedAt) {
		if (this.status == PaymentRecordStatus.VOIDED) {
			return;
		}
		this.status = PaymentRecordStatus.VOIDED;
		this.voidedByUserId = voidedByUserId;
		this.voidReason = reason;
		this.voidedAt = voidedAt;
	}

	public UUID getId() {
		return id;
	}

	public Business getBusiness() {
		return business;
	}

	public UUID getBusinessId() {
		return business != null ? business.getId() : null;
	}

	public Invoice getInvoice() {
		return invoice;
	}

	public UUID getInvoiceId() {
		return invoice != null ? invoice.getId() : null;
	}

	public String getReceiptNumber() {
		return receiptNumber;
	}

	public long getReceiptSequenceValue() {
		return receiptSequenceValue;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getCurrency() {
		return currency;
	}

	public LocalDate getPaymentDate() {
		return paymentDate;
	}

	public PaymentMethod getPaymentMethod() {
		return paymentMethod;
	}

	public String getReference() {
		return reference;
	}

	public String getNotes() {
		return notes;
	}

	public PaymentRecordStatus getStatus() {
		return status;
	}

	public String getInvoiceNumberSnapshot() {
		return invoiceNumberSnapshot;
	}

	public BigDecimal getInvoiceTotalAtPayment() {
		return invoiceTotalAtPayment;
	}

	public BigDecimal getPreviousPaidAmount() {
		return previousPaidAmount;
	}

	public BigDecimal getRemainingBalanceAfterPayment() {
		return remainingBalanceAfterPayment;
	}

	public UUID getCreatedByUserId() {
		return createdByUserId;
	}

	public Instant getVoidedAt() {
		return voidedAt;
	}

	public UUID getVoidedByUserId() {
		return voidedByUserId;
	}

	public String getVoidReason() {
		return voidReason;
	}

	public boolean isShowQuoteFlowBranding() {
		return showQuoteFlowBranding;
	}
}
