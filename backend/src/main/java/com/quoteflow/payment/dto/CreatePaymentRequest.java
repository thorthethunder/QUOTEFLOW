package com.quoteflow.payment.dto;

import com.quoteflow.payment.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreatePaymentRequest(
		@NotNull @DecimalMin(value = "0.01", inclusive = true) @Digits(integer = 15, fraction = 4) BigDecimal amount,
		LocalDate paymentDate,
		@NotNull PaymentMethod paymentMethod,
		@Size(max = 200) String reference,
		@Size(max = 2000) String notes
) {
}
