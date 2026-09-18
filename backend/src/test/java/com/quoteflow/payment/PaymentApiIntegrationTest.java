package com.quoteflow.payment;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class PaymentApiIntegrationTest extends PostgresIntegrationTest {

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
	void partialFullOverpayVoidAndCanonicalMoney() throws Exception {
		String token = register("pay+" + UUID.randomUUID() + "@example.com", "Original Consulting");
		String customerId = createCustomer(token, "Alice Original");
		JsonNode invoice = createSentCanonicalInvoice(token, customerId);
		String invoiceId = invoice.get("id").asText();

		mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(paymentBody("100.00", "CASH")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.receiptNumber").value("RCP-000001"))
				.andExpect(jsonPath("$.amount").value(100.00))
				.andExpect(jsonPath("$.previousPaidAmount").value(0.00))
				.andExpect(jsonPath("$.remainingBalanceAfterPayment").value(165.50))
				.andExpect(jsonPath("$.invoicePaymentSummary.amountPaid").value(100.00))
				.andExpect(jsonPath("$.invoicePaymentSummary.balanceDue").value(165.50))
				.andExpect(jsonPath("$.invoicePaymentSummary.paymentState").value("PARTIALLY_PAID"));

		mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(paymentBody("200.00", "UPI_MANUAL")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PAYMENT_EXCEEDS_BALANCE"));

		JsonNode second = objectMapper.readTree(mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(paymentBody("165.50", "BANK_TRANSFER")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.receiptNumber").value("RCP-000002"))
				.andExpect(jsonPath("$.previousPaidAmount").value(100.00))
				.andExpect(jsonPath("$.remainingBalanceAfterPayment").value(0.00))
				.andExpect(jsonPath("$.invoicePaymentSummary.paymentState").value("PAID"))
				.andReturn()
				.getResponse()
				.getContentAsString());

		mockMvc.perform(get("/api/v1/invoices/" + invoiceId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SENT"))
				.andExpect(jsonPath("$.paymentSummary.paymentState").value("PAID"))
				.andExpect(jsonPath("$.paymentSummary.amountPaid").value(265.50));

		mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/cancel")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVOICE_HAS_PAYMENTS"));

		String paymentId = second.get("id").asText();
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/void")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("reason", "Entered wrong amount"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("VOIDED"))
				.andExpect(jsonPath("$.invoicePaymentSummary.paymentState").value("PARTIALLY_PAID"))
				.andExpect(jsonPath("$.invoicePaymentSummary.amountPaid").value(100.00));

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/void")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_VOIDED"));
	}

	@Test
	void draftAndCancelledNotPayable() throws Exception {
		String token = register("np+" + UUID.randomUUID() + "@example.com", "NP Co");
		String customerId = createCustomer(token, "Cust");
		JsonNode draft = createInvoice(token, customerId, false);
		mockMvc.perform(post("/api/v1/invoices/" + draft.get("id").asText() + "/payments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(paymentBody("10.00", "CASH")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVOICE_NOT_PAYABLE"));

		JsonNode sent = createInvoice(token, customerId, true);
		mockMvc.perform(post("/api/v1/invoices/" + sent.get("id").asText() + "/cancel")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/invoices/" + sent.get("id").asText() + "/payments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(paymentBody("10.00", "CASH")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVOICE_NOT_PAYABLE"));
	}

	@Test
	void concurrentOverpaymentCreatesAtMostBalance() throws Exception {
		String token = register("race+" + UUID.randomUUID() + "@example.com", "Race Co");
		String customerId = createCustomer(token, "Cust");
		JsonNode invoice = createInvoice(token, customerId, true, "100.00");
		String invoiceId = invoice.get("id").asText();

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger created = new AtomicInteger();
		AtomicInteger conflicts = new AtomicInteger();
		List<Future<?>> futures = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			futures.add(pool.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				MvcResult result = mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
								.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
								.contentType(MediaType.APPLICATION_JSON)
								.content(paymentBody("80.00", "CASH")))
						.andReturn();
				int statusCode = result.getResponse().getStatus();
				if (statusCode == 201) {
					created.incrementAndGet();
				} else if (statusCode == 409) {
					conflicts.incrementAndGet();
					assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText())
							.isEqualTo("PAYMENT_EXCEEDS_BALANCE");
				} else {
					throw new IllegalStateException("Unexpected status " + statusCode);
				}
				return null;
			}));
		}
		start.countDown();
		for (Future<?> f : futures) {
			f.get(30, TimeUnit.SECONDS);
		}
		pool.shutdownNow();
		assertThat(created.get()).isEqualTo(1);
		assertThat(conflicts.get()).isEqualTo(1);
		BigDecimal paid = jdbcTemplate.queryForObject(
				"SELECT COALESCE(SUM(amount),0) FROM payments WHERE invoice_id = ?::uuid AND status = 'RECORDED'",
				BigDecimal.class,
				UUID.fromString(invoiceId));
		assertThat(paid).isEqualByComparingTo("80.00");
	}

	@Test
	void complementaryConcurrencyReachesPaid() throws Exception {
		String token = register("comp+" + UUID.randomUUID() + "@example.com", "Comp Co");
		String customerId = createCustomer(token, "Cust");
		JsonNode invoice = createInvoice(token, customerId, true, "100.00");
		String invoiceId = invoice.get("id").asText();

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> futures = List.of(
				pool.submit(() -> {
					start.await(5, TimeUnit.SECONDS);
					return mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
									.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
									.contentType(MediaType.APPLICATION_JSON)
									.content(paymentBody("60.00", "CASH")))
							.andReturn().getResponse().getStatus();
				}),
				pool.submit(() -> {
					start.await(5, TimeUnit.SECONDS);
					return mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
									.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
									.contentType(MediaType.APPLICATION_JSON)
									.content(paymentBody("40.00", "CASH")))
							.andReturn().getResponse().getStatus();
				}));
		start.countDown();
		assertThat(futures.get(0).get(30, TimeUnit.SECONDS)).isEqualTo(201);
		assertThat(futures.get(1).get(30, TimeUnit.SECONDS)).isEqualTo(201);
		pool.shutdownNow();

		mockMvc.perform(get("/api/v1/invoices/" + invoiceId + "/payments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.summary.amountPaid").value(100.00))
				.andExpect(jsonPath("$.summary.balanceDue").value(0.00))
				.andExpect(jsonPath("$.summary.paymentState").value("PAID"));
	}

	@Test
	void receiptNumberingAndSnapshotsAndPdf() throws Exception {
		String tokenB = register("rcpB+" + UUID.randomUUID() + "@example.com", "Biz B");
		String customerB = createCustomer(tokenB, "B");
		JsonNode invoiceB = createInvoice(tokenB, customerB, true, "1000.00");

		ExecutorService pool = Executors.newFixedThreadPool(8);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<String>> futures = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			futures.add(pool.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				MvcResult result = mockMvc.perform(post("/api/v1/invoices/" + invoiceB.get("id").asText() + "/payments")
								.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
								.contentType(MediaType.APPLICATION_JSON)
								.content(paymentBody("10.00", "CASH")))
						.andExpect(status().isCreated())
						.andReturn();
				return objectMapper.readTree(result.getResponse().getContentAsString()).get("receiptNumber").asText();
			}));
		}
		start.countDown();
		Set<String> numbers = futures.stream().map(f -> {
			try {
				return f.get(30, TimeUnit.SECONDS);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}).collect(java.util.stream.Collectors.toSet());
		pool.shutdownNow();
		assertThat(numbers).hasSize(8);

		String tokenHist = register("hist+" + UUID.randomUUID() + "@example.com", "Original Consulting");
		String custHist = createCustomer(tokenHist, "Alice Original");
		JsonNode inv1000 = createInvoice(tokenHist, custHist, true, "1000.00");
		String inv1000Id = inv1000.get("id").asText();
		JsonNode a = objectMapper.readTree(mockMvc.perform(post("/api/v1/invoices/" + inv1000Id + "/payments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenHist)
						.contentType(MediaType.APPLICATION_JSON)
						.content(paymentBody("300.00", "CASH")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.receiptNumber").value("RCP-000001"))
				.andExpect(jsonPath("$.previousPaidAmount").value(0.00))
				.andExpect(jsonPath("$.remainingBalanceAfterPayment").value(700.00))
				.andReturn().getResponse().getContentAsString());
		JsonNode b = objectMapper.readTree(mockMvc.perform(post("/api/v1/invoices/" + inv1000Id + "/payments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenHist)
						.contentType(MediaType.APPLICATION_JSON)
						.content(paymentBody("200.00", "CASH")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.previousPaidAmount").value(300.00))
				.andExpect(jsonPath("$.remainingBalanceAfterPayment").value(500.00))
				.andReturn().getResponse().getContentAsString());

		jdbcTemplate.update("UPDATE customers SET display_name = ? WHERE id = ?::uuid",
				"Alice Updated", UUID.fromString(custHist));
		jdbcTemplate.update(
				"UPDATE businesses SET name = ? WHERE id = (SELECT business_id FROM invoices WHERE id = ?::uuid)",
				"New Consulting",
				UUID.fromString(inv1000Id));

		MvcResult pdf = mockMvc.perform(get("/api/v1/payments/" + a.get("id").asText() + "/receipt.pdf")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenHist))
				.andExpect(status().isOk())
				.andReturn();
		String text = extractPdfText(pdf.getResponse().getContentAsByteArray());
		assertThat(text).contains("RCP-000001");
		assertThat(text).contains("Alice Original");
		assertThat(text).contains("Original Consulting");
		assertThat(text).contains("INV-000001");
		assertThat(text).doesNotContain("Alice Updated");
		assertThat(text).doesNotContain("New Consulting");

		mockMvc.perform(get("/api/v1/payments/" + a.get("id").asText())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenHist))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.remainingBalanceAfterPayment").value(700.00));
		mockMvc.perform(get("/api/v1/payments/" + b.get("id").asText())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenHist))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.remainingBalanceAfterPayment").value(500.00));

		mockMvc.perform(post("/api/v1/payments/" + a.get("id").asText() + "/void")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenHist)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());
		String voidText = extractPdfText(mockMvc.perform(get("/api/v1/payments/" + a.get("id").asText() + "/receipt.pdf")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenHist))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray());
		assertThat(voidText).contains("VOIDED");
	}

	@Test
	void crossTenantIsolation() throws Exception {
		String tokenA = register("a+" + UUID.randomUUID() + "@example.com", "A Co");
		String tokenB = register("b+" + UUID.randomUUID() + "@example.com", "B Co");
		String custA = createCustomer(tokenA, "A");
		String custB = createCustomer(tokenB, "B");
		JsonNode invA = createInvoice(tokenA, custA, true, "100.00");
		JsonNode invB = createInvoice(tokenB, custB, true, "100.00");
		JsonNode payB = objectMapper.readTree(mockMvc.perform(post("/api/v1/invoices/" + invB.get("id").asText() + "/payments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
						.contentType(MediaType.APPLICATION_JSON)
						.content(paymentBody("10.00", "CASH")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());

		mockMvc.perform(post("/api/v1/invoices/" + invB.get("id").asText() + "/payments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content(paymentBody("10.00", "CASH")))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/invoices/" + invB.get("id").asText() + "/payments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/payments/" + payB.get("id").asText())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/payments/" + payB.get("id").asText() + "/void")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/payments/" + payB.get("id").asText() + "/receipt.pdf")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/payments/" + payB.get("id").asText() + "/receipt.pdf"))
				.andExpect(status().isUnauthorized());
		assertThat(invA.get("id").asText()).isNotBlank();
	}

	@Test
	void ignoresMassAssignmentFields() throws Exception {
		String token = register("mass+" + UUID.randomUUID() + "@example.com", "Mass Co");
		String customerId = createCustomer(token, "Cust");
		JsonNode invoice = createInvoice(token, customerId, true, "100.00");
		Map<String, Object> body = new HashMap<>();
		body.put("amount", 25);
		body.put("paymentMethod", "CASH");
		body.put("receiptNumber", "HACKED");
		body.put("currency", "USD");
		body.put("status", "VOIDED");
		body.put("businessId", UUID.randomUUID().toString());
		body.put("amountPaid", 1);
		body.put("balanceDue", 1);
		body.put("paymentState", "PAID");
		mockMvc.perform(post("/api/v1/invoices/" + invoice.get("id").asText() + "/payments")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.receiptNumber").value("RCP-000001"))
				.andExpect(jsonPath("$.currency").value("INR"))
				.andExpect(jsonPath("$.status").value("RECORDED"))
				.andExpect(jsonPath("$.amount").value(25.00));
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

	private JsonNode createSentCanonicalInvoice(String token, String customerId) throws Exception {
		Map<String, Object> payload = new HashMap<>();
		payload.put("customerId", customerId);
		payload.put("discountType", "PERCENTAGE");
		payload.put("discountValue", 10);
		payload.put("taxRate", 18);
		payload.put("items", List.of(
				Map.of("description", "A", "quantity", 2, "unitPrice", 100),
				Map.of("description", "B", "quantity", 1, "unitPrice", 50)));
		JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/v1/invoices")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(payload)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
		return objectMapper.readTree(mockMvc.perform(post("/api/v1/invoices/" + created.get("id").asText() + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
	}

	private JsonNode createInvoice(String token, String customerId, boolean send) throws Exception {
		return createInvoice(token, customerId, send, "100.00");
	}

	private JsonNode createInvoice(String token, String customerId, boolean send, String unitPrice) throws Exception {
		JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/v1/invoices")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"customerId", customerId,
								"discountType", "NONE",
								"discountValue", 0,
								"taxRate", 0,
								"items", List.of(Map.of(
										"description", "Service",
										"quantity", 1,
										"unitPrice", new BigDecimal(unitPrice)))))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
		if (!send) {
			return created;
		}
		return objectMapper.readTree(mockMvc.perform(post("/api/v1/invoices/" + created.get("id").asText() + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
	}

	private String paymentBody(String amount, String method) throws Exception {
		return objectMapper.writeValueAsString(Map.of(
				"amount", new BigDecimal(amount),
				"paymentMethod", method));
	}

	private static String extractPdfText(byte[] pdf) throws Exception {
		try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
			return new org.apache.pdfbox.text.PDFTextStripper().getText(document);
		}
	}
}
