package com.quoteflow.quotation.dto;

import com.quoteflow.quotation.DiscountType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateQuotationRequest(
		@NotNull UUID customerId,
		LocalDate issueDate,
		LocalDate validUntil,
		@Pattern(regexp = "^[A-Z]{3}$") String currency,
		@NotNull DiscountType discountType,
		@NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal discountValue,
		@NotNull @DecimalMin("0.0") @Digits(integer = 5, fraction = 4) BigDecimal taxRate,
		@Size(max = 4000) String notes,
		@Size(max = 4000) String terms,
		@NotEmpty @Size(max = 100) List<@Valid QuotationItemRequest> items,
		Long version
) {
}
