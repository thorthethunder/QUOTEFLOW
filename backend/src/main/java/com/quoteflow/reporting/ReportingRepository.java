package com.quoteflow.reporting;

import com.quoteflow.finance.FinancialDocumentCalculator;
import com.quoteflow.reporting.dto.CollectionsSeriesPoint;
import com.quoteflow.reporting.dto.CustomerMetricsDto;
import com.quoteflow.reporting.dto.InvoiceMetricsDto;
import com.quoteflow.reporting.dto.MoneyByCurrency;
import com.quoteflow.reporting.dto.PaymentMetricsDto;
import com.quoteflow.reporting.dto.QuotationMetricsDto;
import com.quoteflow.reporting.dto.RecentInvoiceDto;
import com.quoteflow.reporting.dto.RecentPaymentDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only PostgreSQL aggregates for tenant dashboard/reporting.
 * Does not mutate domain entities.
 */
@Repository
public class ReportingRepository {

	private static final int RECENT_LIMIT = 5;

	private final JdbcTemplate jdbcTemplate;

	public ReportingRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public CustomerMetricsDto customerMetrics(UUID businessId, LocalDate from, LocalDate to, String timezone) {
		return jdbcTemplate.queryForObject("""
						SELECT
						  COUNT(*) FILTER (WHERE status = 'ACTIVE') AS active_count,
						  COUNT(*) FILTER (WHERE status = 'ARCHIVED') AS archived_count,
						  COUNT(*) FILTER (
						    WHERE (created_at AT TIME ZONE ?) :: date BETWEEN ? AND ?
						  ) AS new_in_period
						FROM customers
						WHERE business_id = ?
						""",
				(rs, rowNum) -> new CustomerMetricsDto(
						rs.getLong("active_count"),
						rs.getLong("archived_count"),
						rs.getLong("new_in_period")),
				timezone,
				Date.valueOf(from),
				Date.valueOf(to),
				businessId);
	}

	public QuotationMetricsDto quotationMetrics(UUID businessId, LocalDate from, LocalDate to) {
		var counts = jdbcTemplate.queryForObject("""
						SELECT
						  COUNT(*) FILTER (WHERE status = 'DRAFT') AS draft_count,
						  COUNT(*) FILTER (WHERE status = 'SENT') AS sent_count,
						  COUNT(*) FILTER (WHERE status = 'CANCELLED') AS cancelled_count,
						  COUNT(*) FILTER (
						    WHERE status = 'SENT'
						      AND EXISTS (
						        SELECT 1 FROM invoices i
						        WHERE i.source_quotation_id = q.id
						          AND i.business_id = q.business_id
						      )
						  ) AS converted_count
						FROM quotations q
						WHERE q.business_id = ?
						  AND q.issue_date BETWEEN ? AND ?
						""",
				(rs, rowNum) -> new long[] {
						rs.getLong("draft_count"),
						rs.getLong("sent_count"),
						rs.getLong("cancelled_count"),
						rs.getLong("converted_count")
				},
				businessId,
				Date.valueOf(from),
				Date.valueOf(to));

		List<MoneyByCurrency> quoted = moneyByCurrency("""
						SELECT currency, COALESCE(SUM(total_amount), 0) AS amount
						FROM quotations
						WHERE business_id = ?
						  AND status IN ('DRAFT', 'SENT')
						  AND issue_date BETWEEN ? AND ?
						GROUP BY currency
						ORDER BY currency
						""",
				businessId, from, to);

		return new QuotationMetricsDto(
				counts[0], counts[1], counts[2], counts[3], quoted);
	}

	public InvoiceMetricsDto invoiceMetrics(UUID businessId, LocalDate from, LocalDate to) {
		var counts = jdbcTemplate.queryForObject("""
						SELECT
						  COUNT(*) FILTER (WHERE status = 'SENT') AS sent_count,
						  COUNT(*) FILTER (WHERE status = 'DRAFT') AS draft_count,
						  COUNT(*) FILTER (WHERE status = 'CANCELLED') AS cancelled_count
						FROM invoices
						WHERE business_id = ?
						  AND issue_date BETWEEN ? AND ?
						""",
				(rs, rowNum) -> new long[] {
						rs.getLong("sent_count"),
						rs.getLong("draft_count"),
						rs.getLong("cancelled_count")
				},
				businessId,
				Date.valueOf(from),
				Date.valueOf(to));

		var paymentStates = jdbcTemplate.queryForObject("""
						WITH paid AS (
						  SELECT invoice_id, SUM(amount) AS amount_paid
						  FROM payments
						  WHERE business_id = ?
						    AND status = 'RECORDED'
						  GROUP BY invoice_id
						)
						SELECT
						  COUNT(*) FILTER (WHERE COALESCE(p.amount_paid, 0) = 0) AS unpaid_count,
						  COUNT(*) FILTER (
						    WHERE COALESCE(p.amount_paid, 0) > 0
						      AND COALESCE(p.amount_paid, 0) < i.total_amount
						  ) AS partially_paid_count,
						  COUNT(*) FILTER (
						    WHERE COALESCE(p.amount_paid, 0) >= i.total_amount
						  ) AS paid_count
						FROM invoices i
						LEFT JOIN paid p ON p.invoice_id = i.id
						WHERE i.business_id = ?
						  AND i.status = 'SENT'
						  AND i.issue_date BETWEEN ? AND ?
						""",
				(rs, rowNum) -> new long[] {
						rs.getLong("unpaid_count"),
						rs.getLong("partially_paid_count"),
						rs.getLong("paid_count")
				},
				businessId,
				businessId,
				Date.valueOf(from),
				Date.valueOf(to));

		List<MoneyByCurrency> invoiced = moneyByCurrency("""
						SELECT currency, COALESCE(SUM(total_amount), 0) AS amount
						FROM invoices
						WHERE business_id = ?
						  AND status = 'SENT'
						  AND issue_date BETWEEN ? AND ?
						GROUP BY currency
						ORDER BY currency
						""",
				businessId, from, to);

		List<MoneyByCurrency> outstanding = moneyByCurrency("""
						WITH paid AS (
						  SELECT invoice_id, SUM(amount) AS amount_paid
						  FROM payments
						  WHERE business_id = ?
						    AND status = 'RECORDED'
						  GROUP BY invoice_id
						)
						SELECT i.currency,
						       COALESCE(SUM(i.total_amount - COALESCE(p.amount_paid, 0)), 0) AS amount
						FROM invoices i
						LEFT JOIN paid p ON p.invoice_id = i.id
						WHERE i.business_id = ?
						  AND i.status = 'SENT'
						  AND i.issue_date BETWEEN ? AND ?
						GROUP BY i.currency
						ORDER BY i.currency
						""",
				businessId, businessId, from, to);

		return new InvoiceMetricsDto(
				counts[0],
				counts[1],
				counts[2],
				paymentStates[0],
				paymentStates[1],
				paymentStates[2],
				invoiced,
				outstanding);
	}

	public PaymentMetricsDto paymentMetrics(UUID businessId, LocalDate from, LocalDate to) {
		Long count = jdbcTemplate.queryForObject("""
						SELECT COUNT(*)
						FROM payments
						WHERE business_id = ?
						  AND status = 'RECORDED'
						  AND payment_date BETWEEN ? AND ?
						""",
				Long.class,
				businessId,
				Date.valueOf(from),
				Date.valueOf(to));

		List<MoneyByCurrency> collected = moneyByCurrency("""
						SELECT currency, COALESCE(SUM(amount), 0) AS amount
						FROM payments
						WHERE business_id = ?
						  AND status = 'RECORDED'
						  AND payment_date BETWEEN ? AND ?
						GROUP BY currency
						ORDER BY currency
						""",
				businessId, from, to);

		return new PaymentMetricsDto(count != null ? count : 0L, collected);
	}

	public List<CollectionsSeriesPoint> collectionsSeries(
			UUID businessId, LocalDate from, LocalDate to, String granularity) {
		if ("MONTHLY".equals(granularity)) {
			return jdbcTemplate.query("""
							SELECT date_trunc('month', payment_date)::date AS period_start,
							       currency,
							       COALESCE(SUM(amount), 0) AS amount
							FROM payments
							WHERE business_id = ?
							  AND status = 'RECORDED'
							  AND payment_date BETWEEN ? AND ?
							GROUP BY 1, currency
							ORDER BY 1, currency
							""",
					(rs, rowNum) -> new CollectionsSeriesPoint(
							rs.getDate("period_start").toLocalDate(),
							"MONTHLY",
							rs.getString("currency"),
							scale(rs.getBigDecimal("amount"))),
					businessId,
					Date.valueOf(from),
					Date.valueOf(to));
		}
		return jdbcTemplate.query("""
						SELECT payment_date AS period_start,
						       currency,
						       COALESCE(SUM(amount), 0) AS amount
						FROM payments
						WHERE business_id = ?
						  AND status = 'RECORDED'
						  AND payment_date BETWEEN ? AND ?
						GROUP BY payment_date, currency
						ORDER BY payment_date, currency
						""",
				(rs, rowNum) -> new CollectionsSeriesPoint(
						rs.getDate("period_start").toLocalDate(),
						"DAILY",
						rs.getString("currency"),
						scale(rs.getBigDecimal("amount"))),
				businessId,
				Date.valueOf(from),
				Date.valueOf(to));
	}

	public List<RecentInvoiceDto> recentInvoices(UUID businessId) {
		return jdbcTemplate.query("""
						WITH paid AS (
						  SELECT invoice_id, SUM(amount) AS amount_paid
						  FROM payments
						  WHERE business_id = ?
						    AND status = 'RECORDED'
						  GROUP BY invoice_id
						)
						SELECT i.id,
						       i.invoice_number,
						       i.customer_display_name,
						       i.issue_date,
						       i.status,
						       i.currency,
						       i.total_amount,
						       CASE
						         WHEN i.status <> 'SENT' THEN NULL
						         WHEN COALESCE(p.amount_paid, 0) = 0 THEN 'UNPAID'
						         WHEN COALESCE(p.amount_paid, 0) >= i.total_amount THEN 'PAID'
						         ELSE 'PARTIALLY_PAID'
						       END AS payment_state
						FROM invoices i
						LEFT JOIN paid p ON p.invoice_id = i.id
						WHERE i.business_id = ?
						ORDER BY i.created_at DESC
						LIMIT ?
						""",
				(rs, rowNum) -> new RecentInvoiceDto(
						(UUID) rs.getObject("id"),
						rs.getString("invoice_number"),
						rs.getString("customer_display_name"),
						rs.getDate("issue_date").toLocalDate(),
						rs.getString("status"),
						rs.getString("payment_state"),
						rs.getString("currency"),
						scale(rs.getBigDecimal("total_amount"))),
				businessId,
				businessId,
				RECENT_LIMIT);
	}

	public List<RecentPaymentDto> recentPayments(UUID businessId) {
		return jdbcTemplate.query("""
						SELECT p.id,
						       p.receipt_number,
						       p.invoice_number_snapshot AS invoice_number,
						       p.invoice_id,
						       p.payment_date,
						       p.payment_method,
						       p.currency,
						       p.amount
						FROM payments p
						WHERE p.business_id = ?
						  AND p.status = 'RECORDED'
						ORDER BY p.payment_date DESC, p.created_at DESC
						LIMIT ?
						""",
				(rs, rowNum) -> new RecentPaymentDto(
						(UUID) rs.getObject("id"),
						rs.getString("receipt_number"),
						rs.getString("invoice_number"),
						(UUID) rs.getObject("invoice_id"),
						rs.getDate("payment_date").toLocalDate(),
						rs.getString("payment_method"),
						rs.getString("currency"),
						scale(rs.getBigDecimal("amount"))),
				businessId,
				RECENT_LIMIT);
	}

	private List<MoneyByCurrency> moneyByCurrency(String sql, UUID businessId, LocalDate from, LocalDate to) {
		return jdbcTemplate.query(
				sql,
				(rs, rowNum) -> new MoneyByCurrency(
						rs.getString("currency"),
						scale(rs.getBigDecimal("amount"))),
				businessId,
				Date.valueOf(from),
				Date.valueOf(to));
	}

	private List<MoneyByCurrency> moneyByCurrency(
			String sql, UUID businessId, UUID businessId2, LocalDate from, LocalDate to) {
		return jdbcTemplate.query(
				sql,
				(rs, rowNum) -> new MoneyByCurrency(
						rs.getString("currency"),
						scale(rs.getBigDecimal("amount"))),
				businessId,
				businessId2,
				Date.valueOf(from),
				Date.valueOf(to));
	}

	private static BigDecimal scale(BigDecimal value) {
		if (value == null) {
			return BigDecimal.ZERO.setScale(
					FinancialDocumentCalculator.MONEY_SCALE, FinancialDocumentCalculator.ROUNDING);
		}
		return value.setScale(FinancialDocumentCalculator.MONEY_SCALE, FinancialDocumentCalculator.ROUNDING);
	}
}
