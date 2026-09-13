package com.quoteflow.quotation;

import com.quoteflow.business.Business;
import com.quoteflow.common.BaseAuditableEntity;
import com.quoteflow.customer.Customer;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quotations")
public class Quotation extends BaseAuditableEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "business_id", nullable = false, updatable = false)
	private Business business;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "customer_id", nullable = false)
	private Customer customer;

	@Column(name = "quotation_number", nullable = false, length = 40, updatable = false)
	private String quotationNumber;

	@Column(name = "sequence_value", nullable = false, updatable = false)
	private long sequenceValue;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private QuotationStatus status;

	@Column(name = "show_quoteflow_branding", nullable = false)
	private boolean showQuoteFlowBranding = true;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(name = "issue_date", nullable = false)
	private LocalDate issueDate;

	@Column(name = "valid_until")
	private LocalDate validUntil;

	@Column(name = "customer_display_name", nullable = false, length = 200)
	private String customerDisplayName;

	@Column(name = "customer_company_name", length = 200)
	private String customerCompanyName;

	@Column(name = "customer_email", length = 320)
	private String customerEmail;

	@Column(name = "customer_phone", length = 40)
	private String customerPhone;

	@Column(name = "customer_address_line1", length = 200)
	private String customerAddressLine1;

	@Column(name = "customer_address_line2", length = 200)
	private String customerAddressLine2;

	@Column(name = "customer_city", length = 100)
	private String customerCity;

	@Column(name = "customer_state_region", length = 100)
	private String customerStateRegion;

	@Column(name = "customer_postal_code", length = 20)
	private String customerPostalCode;

	@Column(name = "customer_country_code", length = 2)
	private String customerCountryCode;

	@Column(name = "customer_tax_id", length = 50)
	private String customerTaxId;

	@Column(name = "business_name", nullable = false, length = 200)
	private String businessName;

	@Column(name = "business_email", length = 320)
	private String businessEmail;

	@Column(name = "business_phone", length = 32)
	private String businessPhone;

	@Column(name = "business_address_line1", length = 200)
	private String businessAddressLine1;

	@Column(name = "business_address_line2", length = 200)
	private String businessAddressLine2;

	@Column(name = "business_city", length = 100)
	private String businessCity;

	@Column(name = "business_state_region", length = 100)
	private String businessStateRegion;

	@Column(name = "business_postal_code", length = 20)
	private String businessPostalCode;

	@Column(name = "business_country_code", length = 2)
	private String businessCountryCode;

	@Column(name = "business_tax_id", length = 50)
	private String businessTaxId;

	@Column(length = 4000)
	private String notes;

	@Column(length = 4000)
	private String terms;

	@Enumerated(EnumType.STRING)
	@Column(name = "discount_type", nullable = false, length = 20)
	private DiscountType discountType;

	@Column(name = "discount_value", nullable = false, precision = 19, scale = 4)
	private BigDecimal discountValue;

	@Column(name = "tax_rate", nullable = false, precision = 9, scale = 4)
	private BigDecimal taxRate;

	@Column(nullable = false, precision = 19, scale = 4)
	private BigDecimal subtotal;

	@Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal discountAmount;

	@Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal taxAmount;

	@Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal totalAmount;

	@Version
	@Column(nullable = false)
	private long version;

	@OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	@OrderBy("position ASC")
	private List<QuotationItem> items = new ArrayList<>();

	protected Quotation() {
	}

	public Quotation(
			Business business,
			Customer customer,
			String quotationNumber,
			long sequenceValue,
			String currency,
			LocalDate issueDate) {
		this.id = UUID.randomUUID();
		this.business = business;
		this.customer = customer;
		this.quotationNumber = quotationNumber;
		this.sequenceValue = sequenceValue;
		this.currency = currency;
		this.issueDate = issueDate;
		this.status = QuotationStatus.DRAFT;
		this.showQuoteFlowBranding = true;
		this.discountType = DiscountType.NONE;
		this.discountValue = BigDecimal.ZERO;
		this.taxRate = BigDecimal.ZERO;
		this.subtotal = BigDecimal.ZERO;
		this.discountAmount = BigDecimal.ZERO;
		this.taxAmount = BigDecimal.ZERO;
		this.totalAmount = BigDecimal.ZERO;
	}

	public void replaceItems(List<QuotationItem> newItems) {
		this.items.clear();
		for (QuotationItem item : newItems) {
			item.setQuotation(this);
			this.items.add(item);
		}
	}

	public void applyCustomerSnapshot(Customer source) {
		this.customer = source;
		this.customerDisplayName = source.getDisplayName();
		this.customerCompanyName = source.getCompanyName();
		this.customerEmail = source.getEmail();
		this.customerPhone = source.getPhone();
		this.customerAddressLine1 = source.getAddressLine1();
		this.customerAddressLine2 = source.getAddressLine2();
		this.customerCity = source.getCity();
		this.customerStateRegion = source.getStateRegion();
		this.customerPostalCode = source.getPostalCode();
		this.customerCountryCode = source.getCountryCode();
		this.customerTaxId = source.getTaxId();
	}

	public void applyBusinessSnapshot(Business source) {
		this.businessName = source.getName();
		this.businessEmail = source.getEmail();
		this.businessPhone = source.getPhone();
		this.businessAddressLine1 = source.getAddressLine1();
		this.businessAddressLine2 = source.getAddressLine2();
		this.businessCity = source.getCity();
		this.businessStateRegion = source.getState();
		this.businessPostalCode = source.getPostalCode();
		this.businessCountryCode = source.getCountryCode();
		this.businessTaxId = source.getTaxIdentificationNumber();
	}

	public void applyCalculation(QuotationCalculator.CalculationResult result, DiscountType type, BigDecimal discountValue, BigDecimal taxRate) {
		this.discountType = type;
		this.discountValue = discountValue;
		this.taxRate = taxRate;
		this.subtotal = result.subtotal();
		this.discountAmount = result.discountAmount();
		this.taxAmount = result.taxAmount();
		this.totalAmount = result.totalAmount();
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

	public Customer getCustomer() {
		return customer;
	}

	public UUID getCustomerId() {
		return customer != null ? customer.getId() : null;
	}

	public String getQuotationNumber() {
		return quotationNumber;
	}

	public long getSequenceValue() {
		return sequenceValue;
	}

	public QuotationStatus getStatus() {
		return status;
	}

	public void setStatus(QuotationStatus status) {
		this.status = status;
	}

	public boolean isShowQuoteFlowBranding() {
		return showQuoteFlowBranding;
	}

	public void setShowQuoteFlowBranding(boolean showQuoteFlowBranding) {
		this.showQuoteFlowBranding = showQuoteFlowBranding;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public LocalDate getIssueDate() {
		return issueDate;
	}

	public void setIssueDate(LocalDate issueDate) {
		this.issueDate = issueDate;
	}

	public LocalDate getValidUntil() {
		return validUntil;
	}

	public void setValidUntil(LocalDate validUntil) {
		this.validUntil = validUntil;
	}

	public String getCustomerDisplayName() {
		return customerDisplayName;
	}

	public String getCustomerCompanyName() {
		return customerCompanyName;
	}

	public String getCustomerEmail() {
		return customerEmail;
	}

	public String getCustomerPhone() {
		return customerPhone;
	}

	public String getCustomerAddressLine1() {
		return customerAddressLine1;
	}

	public String getCustomerAddressLine2() {
		return customerAddressLine2;
	}

	public String getCustomerCity() {
		return customerCity;
	}

	public String getCustomerStateRegion() {
		return customerStateRegion;
	}

	public String getCustomerPostalCode() {
		return customerPostalCode;
	}

	public String getCustomerCountryCode() {
		return customerCountryCode;
	}

	public String getCustomerTaxId() {
		return customerTaxId;
	}

	public String getBusinessName() {
		return businessName;
	}

	public String getBusinessEmail() {
		return businessEmail;
	}

	public String getBusinessPhone() {
		return businessPhone;
	}

	public String getBusinessAddressLine1() {
		return businessAddressLine1;
	}

	public String getBusinessAddressLine2() {
		return businessAddressLine2;
	}

	public String getBusinessCity() {
		return businessCity;
	}

	public String getBusinessStateRegion() {
		return businessStateRegion;
	}

	public String getBusinessPostalCode() {
		return businessPostalCode;
	}

	public String getBusinessCountryCode() {
		return businessCountryCode;
	}

	public String getBusinessTaxId() {
		return businessTaxId;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public String getTerms() {
		return terms;
	}

	public void setTerms(String terms) {
		this.terms = terms;
	}

	public DiscountType getDiscountType() {
		return discountType;
	}

	public BigDecimal getDiscountValue() {
		return discountValue;
	}

	public BigDecimal getTaxRate() {
		return taxRate;
	}

	public BigDecimal getSubtotal() {
		return subtotal;
	}

	public BigDecimal getDiscountAmount() {
		return discountAmount;
	}

	public BigDecimal getTaxAmount() {
		return taxAmount;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public long getVersion() {
		return version;
	}

	public List<QuotationItem> getItems() {
		return items;
	}
}
