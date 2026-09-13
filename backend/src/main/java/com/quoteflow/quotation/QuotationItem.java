package com.quoteflow.quotation;

import com.quoteflow.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "quotation_items")
public class QuotationItem extends BaseAuditableEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "quotation_id", nullable = false)
	private Quotation quotation;

	@Column(nullable = false)
	private int position;

	@Column(nullable = false, length = 500)
	private String description;

	@Column(nullable = false, precision = 19, scale = 4)
	private BigDecimal quantity;

	@Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
	private BigDecimal unitPrice;

	@Column(name = "line_subtotal", nullable = false, precision = 19, scale = 4)
	private BigDecimal lineSubtotal;

	protected QuotationItem() {
	}

	public QuotationItem(int position, String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineSubtotal) {
		this.id = UUID.randomUUID();
		this.position = position;
		this.description = description;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
		this.lineSubtotal = lineSubtotal;
	}

	void setQuotation(Quotation quotation) {
		this.quotation = quotation;
	}

	public UUID getId() {
		return id;
	}

	public Quotation getQuotation() {
		return quotation;
	}

	public int getPosition() {
		return position;
	}

	public String getDescription() {
		return description;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public BigDecimal getLineSubtotal() {
		return lineSubtotal;
	}
}
