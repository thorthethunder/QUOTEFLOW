package com.quoteflow.subscription;

import com.quoteflow.business.Business;
import com.quoteflow.business.BusinessRepository;
import com.quoteflow.customer.CustomerRepository;
import com.quoteflow.customer.CustomerStatus;
import com.quoteflow.invoice.InvoiceRepository;
import com.quoteflow.quotation.QuotationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class UsageService {

	private final CustomerRepository customerRepository;
	private final QuotationRepository quotationRepository;
	private final InvoiceRepository invoiceRepository;
	private final BusinessRepository businessRepository;

	public UsageService(
			CustomerRepository customerRepository,
			QuotationRepository quotationRepository,
			InvoiceRepository invoiceRepository,
			BusinessRepository businessRepository) {
		this.customerRepository = customerRepository;
		this.quotationRepository = quotationRepository;
		this.invoiceRepository = invoiceRepository;
		this.businessRepository = businessRepository;
	}

	public long countActiveCustomers(UUID businessId) {
		return customerRepository.countByBusiness_IdAndStatus(businessId, CustomerStatus.ACTIVE);
	}

	public long countQuotationsInPeriod(UUID businessId, UsagePeriod period) {
		return quotationRepository.countCreatedInPeriod(
				businessId, period.startInclusive(), period.endExclusive());
	}

	public long countInvoicesInPeriod(UUID businessId, UsagePeriod period) {
		return invoiceRepository.countCreatedInPeriod(
				businessId, period.startInclusive(), period.endExclusive());
	}

	public UsagePeriod currentCalendarMonth(UUID businessId) {
		Business business = businessRepository.findById(businessId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Business not available"));
		return UsagePeriod.currentMonth(resolveZone(business.getTimezone()));
	}

	public ZoneId resolveZone(String timezone) {
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
