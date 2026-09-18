package com.quoteflow.ai.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quoteflow.ai.action.dto.ActionConfirmResponse;
import com.quoteflow.ai.action.dto.ActionProposalSummaryDto;
import com.quoteflow.ai.action.payload.QuotationCreateDraftPayload;
import com.quoteflow.ai.tool.AiToolRegistry;
import com.quoteflow.identity.TenantRole;
import com.quoteflow.security.AuthenticatedUser;
import com.quoteflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
		"quoteflow.ai.actions.enabled=true",
		"quoteflow.ai.actions.approval-ttl=10m"
})
class AiActionApprovalIntegrationTest extends PostgresIntegrationTest {

	@Autowired
	WebApplicationContext webApplicationContext;
	@Autowired
	ObjectMapper objectMapper;
	@Autowired
	JdbcTemplate jdbcTemplate;
	@Autowired
	AiActionApprovalService approvalService;
	@Autowired
	AiActionProposalRepository proposalRepository;
	@Autowired
	AiActionIntegrityService integrityService;
	@Autowired
	AiToolRegistry toolRegistry;
	@Autowired
	EntityManager entityManager;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(springSecurity())
				.build();
		jdbcTemplate.update("DELETE FROM ai_action_proposals");
		jdbcTemplate.update("DELETE FROM payments");
		jdbcTemplate.update("DELETE FROM invoice_items");
		jdbcTemplate.update("DELETE FROM invoices");
		jdbcTemplate.update("DELETE FROM quotation_items");
		jdbcTemplate.update("DELETE FROM quotations");
		jdbcTemplate.update("DELETE FROM document_sequences");
		jdbcTemplate.update("DELETE FROM customers");
		jdbcTemplate.update("DELETE FROM refresh_tokens");
		jdbcTemplate.update("DELETE FROM app_users");
		jdbcTemplate.update("DELETE FROM subscriptions");
		jdbcTemplate.update("DELETE FROM notifications");
		jdbcTemplate.update("DELETE FROM businesses");
	}

	@Test
	void actionToolsRegisteredWhenActionsEnabled() {
		assertThat(toolRegistry.allowlistedNames()).contains(
				"quotation_create_draft", "invoice_create_draft", "reminder_prepare", "payment_reminder_send");
		assertThat(toolRegistry.allowlistedNames()).doesNotContain(
				"confirm_action", "approve_action", "execute_action", "payment_record", "email_send");
	}

	@Test
	void proposalRequiresAuth() throws Exception {
		mockMvc.perform(get("/api/v1/ai/actions/" + UUID.randomUUID()))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/ai/actions/" + UUID.randomUUID() + "/confirm"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void quotationProposalDoesNotPersistUntilConfirm() throws Exception {
		String token = register("q+" + UUID.randomUUID() + "@example.com", "Q Co");
		AuthenticatedUser user = principal(token);
		String customerId = createCustomer(token, "Raj Electrical");

		long quotationsBefore = count("quotations");
		ActionProposalSummaryDto proposal = approvalService.prepareQuotationDraft(
				user,
				new QuotationCreateDraftPayload(
						UUID.fromString(customerId),
						null,
						"INR",
						"NONE",
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						"AI draft",
						null,
						List.of(new QuotationCreateDraftPayload.LineItem(
								"Fans", new BigDecimal("2"), new BigDecimal("3000")))));

		assertThat(count("quotations")).isEqualTo(quotationsBefore);
		assertThat(proposal.status()).isEqualTo(AiActionProposalStatus.PENDING);

		MvcResult detail = mockMvc.perform(get("/api/v1/ai/actions/" + proposal.proposalId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.actionType").value("QUOTATION_CREATE_DRAFT"))
				.andReturn();
		JsonNode body = objectMapper.readTree(detail.getResponse().getContentAsString());
		assertThat(body.path("payload").path("customerDisplayName").asText()).isEqualTo("Raj Electrical");
		assertThat(body.path("preview").path("total").asText()).isEqualTo("6000.00");
		assertThat(body.path("preview").path("authoritative").asBoolean()).isTrue();
		ActionConfirmResponse confirmed = approvalService.confirm(user, proposal.proposalId());
		assertThat(confirmed.status()).isEqualTo("EXECUTED");
		assertThat(confirmed.resultReferenceType()).isEqualTo("QUOTATION");
		assertThat(count("quotations")).isEqualTo(quotationsBefore + 1);
	}

	@Test
	void confirmationDoesNotNeedAiProvider() throws Exception {
		String token = register("ai-down+" + UUID.randomUUID() + "@example.com", "Offline Co");
		AuthenticatedUser user = principal(token);
		String customerId = createCustomer(token, "Raj Electrical");
		ActionProposalSummaryDto proposal = approvalService.prepareQuotationDraft(
				user,
				draftPayload(UUID.fromString(customerId), "3000"));
		// AI remains disabled in test profile; confirm uses stored payload only.
		ActionConfirmResponse confirmed = approvalService.confirm(user, proposal.proposalId());
		assertThat(confirmed.resultReferenceId()).isNotNull();
	}

	@Test
	void invoiceProposalAndConfirm() throws Exception {
		String token = register("inv+" + UUID.randomUUID() + "@example.com", "Inv Co");
		AuthenticatedUser user = principal(token);
		String customerId = createCustomer(token, "Raj Electrical");
		long before = count("invoices");
		var proposal = approvalService.prepareInvoiceDraft(
				user,
				new com.quoteflow.ai.action.payload.InvoiceCreateDraftPayload(
						UUID.fromString(customerId),
						null,
						"INR",
						"NONE",
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						null,
						null,
						List.of(new com.quoteflow.ai.action.payload.InvoiceCreateDraftPayload.LineItem(
								"Consulting", BigDecimal.ONE, new BigDecimal("5000")))));
		assertThat(count("invoices")).isEqualTo(before);
		ActionConfirmResponse confirmed = approvalService.confirm(user, proposal.proposalId());
		assertThat(confirmed.resultReferenceType()).isEqualTo("INVOICE");
		assertThat(count("invoices")).isEqualTo(before + 1);
	}

	@Test
	void reminderPrepareDoesNotSendEmail() throws Exception {
		String token = register("rem+" + UUID.randomUUID() + "@example.com", "Rem Co");
		AuthenticatedUser user = principal(token);
		String customerId = createCustomer(token, "Raj Electrical");
		JsonNode inv = createSentInvoice(token, customerId, "1000.00", "INR", LocalDate.now());
		long notificationsBefore = count("notifications");
		var proposal = approvalService.prepareReminder(
				user,
				UUID.fromString(inv.get("id").asText()),
				"Payment reminder",
				"Please pay the outstanding balance.");
		assertThat(count("notifications")).isEqualTo(notificationsBefore);
		ActionConfirmResponse confirmed = approvalService.confirm(user, proposal.proposalId());
		assertThat(confirmed.resultReferenceType()).isEqualTo("REMINDER_PREPARED");
		assertThat(count("notifications")).isEqualTo(notificationsBefore);
	}

	@Test
	void payloadIntegrityRejectsTamperedStoredPayload() throws Exception {
		String token = register("tamper+" + UUID.randomUUID() + "@example.com", "T Co");
		AuthenticatedUser user = principal(token);
		String customerId = createCustomer(token, "Raj Electrical");
		var proposal = approvalService.prepareQuotationDraft(user, draftPayload(UUID.fromString(customerId), "3000"));
		jdbcTemplate.update(
				"UPDATE ai_action_proposals SET payload_json = ? WHERE id = ?",
				"{\"tampered\":true}",
				proposal.proposalId());
		assertThat(integrityService.verifyStored(proposalRepository.findById(proposal.proposalId()).orElseThrow()))
				.isFalse();
		mockMvc.perform(post("/api/v1/ai/actions/" + proposal.proposalId() + "/confirm")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("AI_ACTION_INTEGRITY"));
		assertThat(count("quotations")).isZero();
	}

	@Test
	void confirmIgnoresClientPayloadBody() throws Exception {
		String token = register("body+" + UUID.randomUUID() + "@example.com", "Body Co");
		AuthenticatedUser user = principal(token);
		String customerId = createCustomer(token, "Raj Electrical");
		var proposal = approvalService.prepareQuotationDraft(user, draftPayload(UUID.fromString(customerId), "3000"));
		mockMvc.perform(post("/api/v1/ai/actions/" + proposal.proposalId() + "/confirm")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"unitPrice\":30000,\"items\":[]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("EXECUTED"));
		JsonNode list = objectMapper.readTree(mockMvc.perform(get("/api/v1/quotations")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
		assertThat(list.get("content").get(0).get("totalAmount").decimalValue())
				.isEqualByComparingTo("6000.00");
	}

	@Test
	void expiredProposalCannotConfirm() throws Exception {
		String token = register("exp+" + UUID.randomUUID() + "@example.com", "Exp Co");
		AuthenticatedUser user = principal(token);
		String customerId = createCustomer(token, "Raj Electrical");
		var proposal = approvalService.prepareQuotationDraft(user, draftPayload(UUID.fromString(customerId), "100"));
		int updated = jdbcTemplate.update(
				"UPDATE ai_action_proposals SET expires_at = NOW() - INTERVAL '2 hours' WHERE id = CAST(? AS uuid)",
				proposal.proposalId().toString());
		assertThat(updated).isEqualTo(1);
		entityManager.clear();
		mockMvc.perform(post("/api/v1/ai/actions/" + proposal.proposalId() + "/confirm")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("AI_ACTION_EXPIRED"));
		entityManager.clear();
		assertThat(proposalRepository.findById(proposal.proposalId()).orElseThrow().getStatus())
				.isEqualTo(AiActionProposalStatus.EXPIRED);
	}

	@Test
	void cancelledThenConfirmFails() throws Exception {
		String token = register("can+" + UUID.randomUUID() + "@example.com", "Can Co");
		AuthenticatedUser user = principal(token);
		String customerId = createCustomer(token, "Raj Electrical");
		var proposal = approvalService.prepareQuotationDraft(user, draftPayload(UUID.fromString(customerId), "100"));
		approvalService.cancel(user, proposal.proposalId());
		mockMvc.perform(post("/api/v1/ai/actions/" + proposal.proposalId() + "/confirm")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("AI_ACTION_CANCELLED"));
	}

	@Test
	void crossTenantBlocked() throws Exception {
		String tokenA = register("a+" + UUID.randomUUID() + "@example.com", "A Co");
		String tokenB = register("b+" + UUID.randomUUID() + "@example.com", "B Co");
		AuthenticatedUser userA = principal(tokenA);
		String customerId = createCustomer(tokenA, "Raj Electrical");
		var proposal = approvalService.prepareQuotationDraft(userA, draftPayload(UUID.fromString(customerId), "100"));
		mockMvc.perform(get("/api/v1/ai/actions/" + proposal.proposalId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/ai/actions/" + proposal.proposalId() + "/confirm")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/ai/actions/" + proposal.proposalId() + "/cancel")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
				.andExpect(status().isNotFound());
		assertThat(count("quotations")).isZero();
	}

	@Test
	void crossUserConfirmDenied() throws Exception {
		String token = register("owner+" + UUID.randomUUID() + "@example.com", "Shared Co");
		AuthenticatedUser owner = principal(token);
		String customerId = createCustomer(token, "Raj Electrical");
		var proposal = approvalService.prepareQuotationDraft(owner, draftPayload(UUID.fromString(customerId), "100"));
		AuthenticatedUser otherSameTenant = new AuthenticatedUser(
				UUID.randomUUID(),
				owner.getBusinessId(),
				TenantRole.STAFF,
				"staff+" + UUID.randomUUID() + "@example.com");
		try {
			approvalService.confirm(otherSameTenant, proposal.proposalId());
			org.junit.jupiter.api.Assertions.fail("expected cross-user confirm to fail");
		} catch (com.quoteflow.common.api.DomainApiException ex) {
			assertThat(ex.getStatus()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
		}
		assertThat(count("quotations")).isZero();
		entityManager.clear();
		assertThat(proposalRepository.findById(proposal.proposalId()).orElseThrow().getStatus())
				.isEqualTo(AiActionProposalStatus.PENDING);
	}

	@Test
	void concurrentConfirmCreatesExactlyOne() throws Exception {
		String token = register("race+" + UUID.randomUUID() + "@example.com", "Race Co");
		AuthenticatedUser user = principal(token);
		String customerId = createCustomer(token, "Raj Electrical");
		var proposal = approvalService.prepareQuotationDraft(user, draftPayload(UUID.fromString(customerId), "250"));
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger successes = new AtomicInteger();
		AtomicInteger conflicts = new AtomicInteger();
		Future<?> f1 = pool.submit(() -> {
			await(start);
			try {
				approvalService.confirm(user, proposal.proposalId());
				successes.incrementAndGet();
			} catch (Exception ex) {
				conflicts.incrementAndGet();
			}
		});
		Future<?> f2 = pool.submit(() -> {
			await(start);
			try {
				approvalService.confirm(user, proposal.proposalId());
				successes.incrementAndGet();
			} catch (Exception ex) {
				conflicts.incrementAndGet();
			}
		});
		start.countDown();
		f1.get();
		f2.get();
		pool.shutdown();
		assertThat(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
		assertThat(successes.get()).isEqualTo(1);
		assertThat(conflicts.get()).isEqualTo(1);
		assertThat(count("quotations")).isEqualTo(1);
		entityManager.clear();
		assertThat(proposalRepository.findById(proposal.proposalId()).orElseThrow().getStatus())
				.isEqualTo(AiActionProposalStatus.EXECUTED);
	}

	@Test
	void capabilitiesExposeAiActionsFlag() throws Exception {
		String token = register("cap+" + UUID.randomUUID() + "@example.com", "Cap");
		mockMvc.perform(get("/api/v1/ai/capabilities")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.aiActions").value(false));
	}

	private QuotationCreateDraftPayload draftPayload(UUID customerId, String unitPrice) {
		return new QuotationCreateDraftPayload(
				customerId,
				null,
				"INR",
				"NONE",
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				null,
				null,
				List.of(new QuotationCreateDraftPayload.LineItem(
						"Item", new BigDecimal("2"), new BigDecimal(unitPrice))));
	}

	private long count(String table) {
		Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
		return n == null ? 0 : n;
	}

	private AuthenticatedUser principal(String token) {
		try {
			JsonNode me = objectMapper.readTree(mockMvc.perform(get("/api/v1/me")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
					.andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString());
			return new AuthenticatedUser(
					UUID.fromString(me.get("userId").asText()),
					UUID.fromString(me.get("businessId").asText()),
					TenantRole.valueOf(me.get("tenantRole").asText()),
					me.get("email").asText());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
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

	private JsonNode createSentInvoice(
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
		JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/v1/invoices")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
		return objectMapper.readTree(mockMvc.perform(post("/api/v1/invoices/" + created.get("id").asText() + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
