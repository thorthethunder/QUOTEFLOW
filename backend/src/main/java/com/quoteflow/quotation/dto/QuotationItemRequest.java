package com.quoteflow.quotation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record QuotationItemRequest(
		@NotBlank @Size(max = 500) String description,
		@NotNull @DecimalMin(value = "0.0001", inclusive = true) @Digits(integer = 15, fraction = 4) BigDecimal quantity,
		@NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal unitPrice
) {
}
