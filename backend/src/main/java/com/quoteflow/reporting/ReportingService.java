package com.quoteflow.reporting;

import com.quoteflow.business.Business;
import com.quoteflow.business.BusinessRepository;
import com.quoteflow.common.api.DomainApiException;
import com.quoteflow.reporting.dto.DashboardSummaryResponse;
import com.quoteflow.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class ReportingService {

	/** Inclusive max span for dashboard aggregates (days). */
	static final long MAX_RANGE_DAYS = 3660; // ~10 years

	/** Daily series when range length (inclusive) is at most this many days. */
	static final long DAILY_SERIES_MAX_DAYS = 92;

	private final ReportingRepository reportingRepository;
	private final BusinessRepository businessRepository;

	public ReportingService(ReportingRepository reportingRepository, BusinessRepository businessRepository) {
		this.reportingRepository = reportingRepository;
		this.businessRepository = businessRepository;
	}

	@Transactional(readOnly = true)
	public DashboardSummaryResponse summary(AuthenticatedUser user, LocalDate from, LocalDate to) {
		UUID businessId = user.getBusinessId();
		Business business = businessRepository.findById(businessId)
				.orElseThrow(() -> new DomainApiException(
						HttpStatus.NOT_FOUND, "BUSINESS_NOT_FOUND", "Business not found"));

		ZoneId zone = resolveZone(business.getTimezone());
		LocalDate today = LocalDate.now(zone);
		String defaultLabel = "custom";

		if (from == null && to == null) {
			YearMonth month = YearMonth.from(today);
			from = month.atDay(1);
			to = month.atEndOfMonth();
			defaultLabel = "this_month";
		} else if (from == null || to == null) {
			throw new DomainApiException(
					HttpStatus.BAD_REQUEST,
					"INVALID_DATE_RANGE",
					"Both from and to must be provided together, or omit both for the current business month");
		} else {
			defaultLabel = "custom";
		}

		if (from.isAfter(to)) {
			throw new DomainApiException(
					HttpStatus.BAD_REQUEST,
					"INVALID_DATE_RANGE",
					"from must be on or before to");
		}

		long inclusiveDays = ChronoUnit.DAYS.between(from, to) + 1;
		if (inclusiveDays > MAX_RANGE_DAYS) {
			throw new DomainApiException(
					HttpStatus.BAD_REQUEST,
					"INVALID_DATE_RANGE",
					"Date range exceeds maximum of " + MAX_RANGE_DAYS + " days");
		}

		String granularity = inclusiveDays <= DAILY_SERIES_MAX_DAYS ? "DAILY" : "MONTHLY";
		String timezone = business.getTimezone();

		return new DashboardSummaryResponse(
				from,
				to,
				timezone,
				defaultLabel,
				reportingRepository.customerMetrics(businessId, from, to, timezone),
				reportingRepository.quotationMetrics(businessId, from, to),
				reportingRepository.invoiceMetrics(businessId, from, to),
				reportingRepository.paymentMetrics(businessId, from, to),
				reportingRepository.collectionsSeries(businessId, from, to, granularity),
				reportingRepository.recentInvoices(businessId),
				reportingRepository.recentPayments(businessId));
	}

	public static ZoneId resolveZone(String timezone) {
		if (timezone == null || timezone.isBlank()) {
			return ZoneId.of("UTC");
		}
		try {
			return ZoneId.of(timezone.trim());
		} catch (DateTimeException ex) {
			return ZoneId.of("UTC");
		}
	}
}
