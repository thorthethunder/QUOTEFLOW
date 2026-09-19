package com.quoteflow.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface OutstandingInvoiceCandidate {
	UUID getId();
	String getInvoiceNumber();
	String getCustomerDisplayName();
	String getCustomerEmail();
	String getCurrency();
	LocalDate getDueDate();
	BigDecimal getTotalAmount();
	BigDecimal getAmountPaid();
	BigDecimal getBalanceDue();
}
