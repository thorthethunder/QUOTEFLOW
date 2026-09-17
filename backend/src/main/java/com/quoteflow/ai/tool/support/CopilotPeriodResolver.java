package com.quoteflow.ai.tool.support;

import com.quoteflow.business.Business;
import com.quoteflow.business.BusinessRepository;
import com.quoteflow.common.api.DomainApiException;
import com.quoteflow.reporting.ReportingService;
import com.quoteflow.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.UUID;

/**
 * Resolves semantic date periods using the business timezone (authoritative).
 */
@Component
public class CopilotPeriodResolver {

	public enum Period {
		THIS_MONTH,
		LAST_MONTH,
		THIS_WEEK,
		TODAY,
		CUSTOM
	}

	private final BusinessRepository businessRepository;

	public CopilotPeriodResolver(BusinessRepository businessRepository) {
		this.businessRepository = businessRepository;
	}

	public ResolvedPeriod resolve(
			AuthenticatedUser principal,
			String periodRaw,
			LocalDate from,
			LocalDate to) {
		UUID businessId = principal.getBusinessId();
		Business business = businessRepository.findById(businessId)
				.orElseThrow(() -> new DomainApiException(
						HttpStatus.NOT_FOUND, "BUSINESS_NOT_FOUND", "Business not found"));
		ZoneId zone = ReportingService.resolveZone(business.getTimezone());
		LocalDate today = LocalDate.now(zone);
		Period period = parsePeriod(periodRaw);

		return switch (period) {
			case THIS_MONTH -> new ResolvedPeriod(
					today.with(TemporalAdjusters.firstDayOfMonth()),
					today.with(TemporalAdjusters.lastDayOfMonth()),
					business.getTimezone(),
					"this_month");
			case LAST_MONTH -> {
				LocalDate firstThis = today.with(TemporalAdjusters.firstDayOfMonth());
				LocalDate lastPrev = firstThis.minusDays(1);
				yield new ResolvedPeriod(
						lastPrev.with(TemporalAdjusters.firstDayOfMonth()),
						lastPrev,
						business.getTimezone(),
						"last_month");
			}
			case THIS_WEEK -> {
				LocalDate start = today.with(java.time.DayOfWeek.MONDAY);
				yield new ResolvedPeriod(start, today, business.getTimezone(), "this_week");
			}
			case TODAY -> new ResolvedPeriod(today, today, business.getTimezone(), "today");
			case CUSTOM -> {
				if (from == null || to == null) {
					throw new DomainApiException(
							HttpStatus.BAD_REQUEST,
							"AI_TOOL_ARGS_INVALID",
							"CUSTOM period requires from and to dates");
				}
				if (from.isAfter(to)) {
					throw new DomainApiException(
							HttpStatus.BAD_REQUEST,
							"AI_TOOL_ARGS_INVALID",
							"from must be on or before to");
				}
				long days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
				if (days > 366) {
					throw new DomainApiException(
							HttpStatus.BAD_REQUEST,
							"AI_TOOL_ARGS_INVALID",
							"Date range exceeds maximum of 366 days");
				}
				yield new ResolvedPeriod(from, to, business.getTimezone(), "custom");
			}
		};
	}

	private static Period parsePeriod(String raw) {
		if (raw == null || raw.isBlank()) {
			return Period.THIS_MONTH;
		}
		try {
			return Period.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			throw new DomainApiException(
					HttpStatus.BAD_REQUEST,
					"AI_TOOL_ARGS_INVALID",
					"period must be one of THIS_MONTH, LAST_MONTH, THIS_WEEK, TODAY, CUSTOM");
		}
	}

	public record ResolvedPeriod(LocalDate from, LocalDate to, String timezone, String label) {
	}
}
