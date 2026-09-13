package com.quoteflow.subscription;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Business-timezone calendar month as half-open Instant range [start, end).
 */
public record UsagePeriod(LocalDate from, LocalDate to, Instant startInclusive, Instant endExclusive, String timezone) {

	public static UsagePeriod currentMonth(ZoneId zone) {
		YearMonth month = YearMonth.now(zone);
		return of(month, zone);
	}

	public static UsagePeriod of(YearMonth month, ZoneId zone) {
		LocalDate from = month.atDay(1);
		LocalDate to = month.atEndOfMonth();
		Instant start = from.atStartOfDay(zone).toInstant();
		Instant end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
		return new UsagePeriod(from, to, start, end, zone.getId());
	}
}
