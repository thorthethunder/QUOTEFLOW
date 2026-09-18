package com.quoteflow.reporting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quoteflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class DashboardReportingIntegrationTest extends PostgresIntegrationTest {

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(springSecurity())
				.build();
		jdbcTemplate.update("DELETE FROM payments");
		jdbcTemplate.update("DELETE FROM invoice_items");
		jdbcTemplate.update("DELETE FROM invoices");
		jdbcTemplate.update("DELETE FROM quotation_items");
		jdbcTemplate.update("DELETE FROM quotations");
		jdbcTemplate.update("DELETE FROM document_sequences");
		jdbcTemplate.update("DELETE FROM customers");
		jdbcTemplate.update("DELETE FROM refresh_tokens");
		jdbcTemplate.update("DELETE FROM ai_action_proposals");
		jdbcTemplate.update("DELETE FROM app_users");
		jdbcTemplate.update("DELETE FROM subscriptions");
		jdbcTemplate.update("DELETE FROM notifications");
		jdbcTemplate.update("DELETE FROM businesses");
	}

	@Test
	void financialFixtureAggregatesMatchExpectedSemantics() throws Exception {
		String token = register("dash+" + UUID.randomUUID() + "@example.com", "Dash Co");
		String customerId = createCustomer(token, "Cust");
		LocalDate mid = LocalDate.of(2026, 3, 15);
		LocalDate from = LocalDate.of(2026, 3, 1);
		LocalDate to = LocalDate.of(2026, 3, 31);

		// Invoice A ₹1000 SENT, payment ₹400 → PARTIALLY_PAID, outstanding 600
		JsonNode invA = createSentInvoice(token, customerId, "1000.00", "INR", mid);
		recordPayment(token, invA.get("id").asText(), "400.00", mid);

		// Invoice B ₹500 SENT, fully paid
		JsonNode invB = createSentInvoice(token, customerId, "500.00", "INR", mid);
		recordPayment(token, invB.get("id").asText(), "500.00", mid);

		// Cancelled Invoice C ₹300 — excluded from invoiced/outstanding
		JsonNode invC = createSentInvoice(token, customerId, "300.00", "INR", mid);
		mockMvc.perform(post("/api/v1/invoices/" + invC.get("id").asText() + "/cancel")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());

		// DRAFT invoice — not in financial receivable totals
		createDraftInvoice(token, customerId, "999.00", "INR", mid);

		JsonNode summary = getSummary(token, from, to);
		assertThat(summary.get("invoices").get("sentCount").asLong()).isEqualTo(2);
		assertThat(summary.get("invoices").get("draftCount").asLong()).isEqualTo(1);
		assertThat(summary.get("invoices").get("cancelledCount").asLong()).isEqualTo(1);
		assertThat(summary.get("invoices").get("unpaidCount").asLong()).isEqualTo(0);
		assertThat(summary.get("invoices").get("partiallyPaidCount").asLong()).isEqualTo(1);
		assertThat(summary.get("invoices").get("paidCount").asLong()).isEqualTo(1);
		assertMoney(summary.get("invoices").get("invoicedAmountByCurrency"), "INR", "1500.00");
		assertMoney(summary.get("invoices").get("outstandingAmountByCurrency"), "INR", "600.00");
		assertMoney(summary.get("payments").get("collectedAmountByCurrency"), "INR", "900.00");
		assertThat(summary.get("payments").get("recordedCount").asLong()).isEqualTo(2);
		assertThat(summary.get("customers").get("activeCount").asLong()).isEqualTo(1);
	}

	@Test
	void voidedPaymentExcludedFromCollected() throws Exception {
		String token = register("voiddash+" + UUID.randomUUID() + "@example.com", "Void Dash");
		String customerId = createCustomer(token, "Cust");
		LocalDate day = LocalDate.of(2026, 4, 10);
		JsonNode inv = createSentInvoice(token, customerId, "1000.00", "INR", day);
		JsonNode pay = recordPayment(token, inv.get("id").asText(), "400.00", day);

		mockMvc.perform(post("/api/v1/payments/" + pay.get("id").asText() + "/void")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("reason", "mistake"))))
				.andExpect(status().isOk());

		JsonNode summary = getSummary(token, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
		assertThat(summary.get("payments").get("recordedCount").asLong()).isEqualTo(0);
		assertThat(summary.get("payments").get("collectedAmountByCurrency")).isEmpty();
		assertThat(summary.get("invoices").get("unpaidCount").asLong()).isEqualTo(1);
		assertMoney(summary.get("invoices").get("outstandingAmountByCurrency"), "INR", "1000.00");
	}

	@Test
	void unpaidPartialPaidStatesAndMixedCurrencyNeverCombined() throws Exception {
		String token = register("fx+" + UUID.randomUUID() + "@example.com", "FX Co");
		String customerId = createCustomer(token, "Cust");
		LocalDate day = LocalDate.of(2026, 5, 5);

		createSentInvoice(token, customerId, "100.00", "INR", day); // unpaid
		JsonNode inrPartial = createSentInvoice(token, customerId, "1000.00", "INR", day);
		recordPayment(token, inrPartial.get("id").asText(), "400.00", day);
		JsonNode usd = createSentInvoice(token, customerId, "200.00", "USD", day);
		recordPayment(token, usd.get("id").asText(), "200.00", day);

		JsonNode summary = getSummary(token, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
		assertThat(summary.get("invoices").get("unpaidCount").asLong()).isEqualTo(1);
		assertThat(summary.get("invoices").get("partiallyPaidCount").asLong()).isEqualTo(1);
		assertThat(summary.get("invoices").get("paidCount").asLong()).isEqualTo(1);

		JsonNode invoiced = summary.get("invoices").get("invoicedAmountByCurrency");
		assertThat(invoiced).hasSize(2);
		assertMoney(invoiced, "INR", "1100.00");
		assertMoney(invoiced, "USD", "200.00");

		JsonNode collected = summary.get("payments").get("collectedAmountByCurrency");
		assertThat(collected).hasSize(2);
		assertMoney(collected, "INR", "400.00");
		assertMoney(collected, "USD", "200.00");

		// No single combined total field exists
		assertThat(summary.get("invoices").has("invoicedAmount")).isFalse();
		assertThat(summary.get("payments").has("collectedAmount")).isFalse();
	}

	@Test
	void dateBoundariesInclusiveAndOutsideExcluded() throws Exception {
		String token = register("bounds+" + UUID.randomUUID() + "@example.com", "Bounds");
		String customerId = createCustomer(token, "Cust");
		LocalDate from = LocalDate.of(2026, 6, 10);
		LocalDate to = LocalDate.of(2026, 6, 20);

		createSentInvoice(token, customerId, "10.00", "INR", from.minusDays(1));
		createSentInvoice(token, customerId, "20.00", "INR", from);
		createSentInvoice(token, customerId, "30.00", "INR", to);
		createSentInvoice(token, customerId, "40.00", "INR", to.plusDays(1));

		JsonNode summary = getSummary(token, from, to);
		assertThat(summary.get("invoices").get("sentCount").asLong()).isEqualTo(2);
		assertMoney(summary.get("invoices").get("invoicedAmountByCurrency"), "INR", "50.00");
	}

	@Test
	void paymentDateDrivesCollectionsNotCreatedAt() throws Exception {
		String token = register("pdate+" + UUID.randomUUID() + "@example.com", "PDate");
		String customerId = createCustomer(token, "Cust");
		LocalDate issue = LocalDate.of(2026, 7, 1);
		JsonNode inv = createSentInvoice(token, customerId, "100.00", "INR", issue);
		// Record with payment_date outside July — should not appear in July collections
		recordPayment(token, inv.get("id").asText(), "100.00", LocalDate.of(2026, 8, 1));

		JsonNode july = getSummary(token, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
		assertThat(july.get("payments").get("recordedCount").asLong()).isEqualTo(0);
		assertThat(july.get("payments").get("collectedAmountByCurrency")).isEmpty();
		// Outstanding still current for invoices issued in July
		assertMoney(july.get("invoices").get("outstandingAmountByCurrency"), "INR", "0.00");

		JsonNode august = getSummary(token, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
		assertMoney(august.get("payments").get("collectedAmountByCurrency"), "INR", "100.00");
	}

	@Test
	void crossTenantDashboardIsolation() throws Exception {
		String tokenA = register("a+" + UUID.randomUUID() + "@example.com", "Biz A");
		String tokenB = register("b+" + UUID.randomUUID() + "@example.com", "Biz B");
		String custA = createCustomer(tokenA, "A Cust");
		String custB = createCustomer(tokenB, "B Cust");
		LocalDate day = LocalDate.of(2026, 9, 1);
		JsonNode invA = createSentInvoice(tokenA, custA, "1000.00", "INR", day);
		recordPayment(tokenA, invA.get("id").asText(), "1000.00", day);
		JsonNode invB = createSentInvoice(tokenB, custB, "99999.00", "INR", day);
		recordPayment(tokenB, invB.get("id").asText(), "99999.00", day);

		JsonNode summaryA = getSummary(tokenA, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
		assertMoney(summaryA.get("invoices").get("invoicedAmountByCurrency"), "INR", "1000.00");
		assertMoney(summaryA.get("payments").get("collectedAmountByCurrency"), "INR", "1000.00");
		assertThat(summaryA.get("customers").get("activeCount").asLong()).isEqualTo(1);
		String body = summaryA.toString();
		assertThat(body).doesNotContain("99999");
		assertThat(body).doesNotContain("Biz B");
	}

	@Test
	void invalidDateRangeRejected() throws Exception {
		String token = register("bad+" + UUID.randomUUID() + "@example.com", "Bad");
		mockMvc.perform(get("/api/v1/dashboard/summary")
						.param("from", "2026-05-10")
						.param("to", "2026-05-01")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
	}

	@Test
	void quotationsNotAddedToInvoiceTotals() throws Exception {
		String token = register("q+" + UUID.randomUUID() + "@example.com", "Q Co");
		String customerId = createCustomer(token, "Cust");
		LocalDate day = LocalDate.of(2026, 2, 10);

		Map<String, Object> quote = new HashMap<>();
		quote.put("customerId", customerId);
		quote.put("issueDate", day.toString());
		quote.put("discountType", "NONE");
		quote.put("discountValue", 0);
		quote.put("taxRate", 0);
		quote.put("items", List.of(Map.of("description", "Q", "quantity", 1, "unitPrice", 5000)));
		JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/v1/quotations")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(quote)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
		mockMvc.perform(post("/api/v1/quotations/" + created.get("id").asText() + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());

		createSentInvoice(token, customerId, "100.00", "INR", day);

		JsonNode summary = getSummary(token, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
		assertThat(summary.get("quotations").get("sentCount").asLong()).isEqualTo(1);
		assertMoney(summary.get("quotations").get("quotedAmountByCurrency"), "INR", "5000.00");
		assertMoney(summary.get("invoices").get("invoicedAmountByCurrency"), "INR", "100.00");
	}

	@Test
	void anonymousDenied() throws Exception {
		mockMvc.perform(get("/api/v1/dashboard/summary"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void defaultPeriodUsesBusinessTimezoneMonth() throws Exception {
		String token = register("tz+" + UUID.randomUUID() + "@example.com", "TZ Co");
		JsonNode summary = objectMapper.readTree(mockMvc.perform(get("/api/v1/dashboard/summary")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.timezone").value("Asia/Kolkata"))
				.andExpect(jsonPath("$.defaultPeriodLabel").value("this_month"))
				.andReturn().getResponse().getContentAsString());
		LocalDate from = LocalDate.parse(summary.get("from").asText());
		LocalDate to = LocalDate.parse(summary.get("to").asText());
		assertThat(from.getDayOfMonth()).isEqualTo(1);
		assertThat(to).isEqualTo(from.withDayOfMonth(from.lengthOfMonth()));
	}

	private JsonNode getSummary(String token, LocalDate from, LocalDate to) throws Exception {
		return objectMapper.readTree(mockMvc.perform(get("/api/v1/dashboard/summary")
						.param("from", from.toString())
						.param("to", to.toString())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
	}

	private void assertMoney(JsonNode array, String currency, String expected) {
		BigDecimal found = null;
		for (JsonNode row : array) {
			if (currency.equals(row.get("currency").asText())) {
				found = row.get("amount").decimalValue();
				break;
			}
		}
		assertThat(found).as("currency " + currency).isNotNull();
		assertThat(found).isEqualByComparingTo(expected);
	}

	private String register(String email, String businessName) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"businessName", businessName,
								"firstName", "Test",
								"lastName", "User",
								"email", email,
								"password", "passphrase-long-enough",
								"timezone", "Asia/Kolkata",
								"currency", "INR"))))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
	}

	private String createCustomer(String token, String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/customers")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("displayName", name))))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private JsonNode createDraftInvoice(
			String token, String customerId, String unitPrice, String currency, LocalDate issueDate) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("customerId", customerId);
		body.put("issueDate", issueDate.toString());
		body.put("currency", currency);
		body.put("discountType", "NONE");
		body.put("discountValue", 0);
		body.put("taxRate", 0);
		body.put("items", List.of(Map.of(
				"description", "Service",
				"quantity", 1,
				"unitPrice", new BigDecimal(unitPrice))));
		return objectMapper.readTree(mockMvc.perform(post("/api/v1/invoices")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}

	private JsonNode createSentInvoice(
			String token, String customerId, String unitPrice, String currency, LocalDate issueDate) throws Exception {
		JsonNode created = createDraftInvoice(token, customerId, unitPrice, currency, issueDate);
		return objectMapper.readTree(mockMvc.perform(post("/api/v1/invoices/" + created.get("id").asText() + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
	}

	private JsonNode recordPayment(String token, String invoiceId, String amount, LocalDate paymentDate) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("amount", new BigDecimal(amount));
		body.put("paymentMethod", "CASH");
		body.put("paymentDate", paymentDate.toString());
		return objectMapper.readTree(mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}
}
