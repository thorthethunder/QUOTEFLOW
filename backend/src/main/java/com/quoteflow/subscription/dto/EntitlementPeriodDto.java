package com.quoteflow.subscription.dto;

import java.time.LocalDate;

public record EntitlementPeriodDto(
		LocalDate from,
		LocalDate to,
		String timezone
) {
}
