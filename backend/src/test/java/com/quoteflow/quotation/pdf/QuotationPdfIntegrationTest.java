package com.quoteflow.quotation.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quoteflow.support.PostgresIntegrationTest;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class QuotationPdfIntegrationTest extends PostgresIntegrationTest {

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private QuotationPdfRenderer renderer;

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
	void generatesAuthoritativePdfWithSnapshotsMoneyAndIsolation() throws Exception {
		String tokenA = register("pdfa+" + UUID.randomUUID() + "@example.com", "Original Consulting");
		String tokenB = register("pdfb+" + UUID.randomUUID() + "@example.com", "Other Biz");
		String customerA = createCustomer(tokenA, "Alice Original");
		String customerB = createCustomer(tokenB, "Bob Other");

		JsonNode created = createFinancialQuotation(tokenA, customerA);
		String quotationId = created.get("id").asText();
		long version = created.get("version").asLong();

		mockMvc.perform(post("/api/v1/quotations/" + quotationId + "/send")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("version", version))))
				.andExpect(status().isOk());

		jdbcTemplate.update("UPDATE businesses SET name = ? WHERE id = (SELECT business_id FROM quotations WHERE id = ?::uuid)",
				"New Consulting", UUID.fromString(quotationId));
		mockMvc.perform(put("/api/v1/customers/" + customerA)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("displayName", "Alice Updated"))))
				.andExpect(status().isOk());

		MvcResult pdfResult = mockMvc.perform(get("/api/v1/quotations/" + quotationId + "/pdf")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Q-000001.pdf\""))
				.andReturn();

		byte[] pdf = pdfResult.getResponse().getContentAsByteArray();
		assertThat(pdf.length).isGreaterThan(500);
		assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");

		String text = extractText(pdf);
		assertThat(text).contains("Original Consulting");
		assertThat(text).doesNotContain("New Consulting");
		assertThat(text).contains("Alice Original");
		assertThat(text).doesNotContain("Alice Updated");
		assertThat(text).contains("Q-000001");
		assertThat(text).contains("SENT");
		assertThat(text).contains("250.00");
		assertThat(text).contains("25.00");
		assertThat(text).contains("40.50");
		assertThat(text).contains("265.50");
		assertThat(text).contains("Discount (10%)");
		assertThat(text).contains("Tax (18%)");
		assertThat(text).contains("Item A");
		assertThat(text).contains("Item B");

		String quotationB = createQuotation(tokenB, customerB).get("id").asText();
		mockMvc.perform(get("/api/v1/quotations/" + quotationB + "/pdf")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/quotations/" + quotationId + "/pdf"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void draftAndCancelledMarkedAndMaliciousTextIsPlain() throws Exception {
		String token = register("pdfd+" + UUID.randomUUID() + "@example.com", "Draft Co");
		String customerId = createCustomer(token, "Cust <script>alert(1)</script>");

		MvcResult create = mockMvc.perform(post("/api/v1/quotations")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"customerId", customerId,
								"discountType", "NONE",
								"discountValue", 0,
								"taxRate", 0,
								"notes", "Notes & \"quotes\"",
								"items", List.of(Map.of(
										"description", "<b>Hack</b> & ampersand",
										"quantity", 1,
										"unitPrice", new BigDecimal("1.005")))))))
				.andExpect(status().isCreated())
				.andReturn();
		JsonNode quotation = objectMapper.readTree(create.getResponse().getContentAsString());
		assertThat(quotation.get("totalAmount").decimalValue()).isEqualByComparingTo("1.01");

		byte[] draftPdf = mockMvc.perform(get("/api/v1/quotations/" + quotation.get("id").asText() + "/pdf")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsByteArray();
		String draftText = extractText(draftPdf);
		assertThat(draftText).contains("DRAFT");
		assertThat(draftText).contains("<b>Hack</b> & ampersand");
		assertThat(draftText).contains("Cust <script>alert(1)</script>");
		assertThat(draftText).contains("1.01");

		mockMvc.perform(post("/api/v1/quotations/" + quotation.get("id").asText() + "/cancel")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("version", quotation.get("version").asLong()))))
				.andExpect(status().isOk());

		String cancelledText = extractText(mockMvc.perform(get("/api/v1/quotations/" + quotation.get("id").asText() + "/pdf")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsByteArray());
		assertThat(cancelledText).contains("CANCELLED");
	}

	@Test
	void multiPagePdfHasMultiplePages() {
		List<QuotationPdfDocument.LineItem> lines = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			lines.add(new QuotationPdfDocument.LineItem(
					i,
					"Long description line item number " + i + " with enough wrapping text for layout stability and readability across pages.",
					new BigDecimal("1.5"),
					new BigDecimal("99.99"),
					new BigDecimal("149.99")));
		}
		QuotationPdfDocument doc = new QuotationPdfDocument(
				"Q-000099",
				com.quoteflow.quotation.QuotationStatus.SENT,
				java.time.LocalDate.of(2026, 9, 13),
				java.time.LocalDate.of(2026, 10, 13),
				"INR",
				new QuotationPdfDocument.BusinessParty("Seller", null, null, null, null, null, null, null, null, null),
				new QuotationPdfDocument.CustomerParty("Buyer", null, null, null, null, null, null, null, null, null, null),
				lines,
				com.quoteflow.quotation.DiscountType.NONE,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				new BigDecimal("5999.60"),
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				new BigDecimal("5999.60"),
				"Notes",
				"Terms",
				true);
		byte[] pdf = renderer.render(doc);
		assertThat(pdf.length).isGreaterThan(2_000);
		try (PDDocument pd = Loader.loadPDF(pdf)) {
			assertThat(pd.getNumberOfPages()).isGreaterThan(1);
		} catch (Exception ex) {
			throw new AssertionError(ex);
		}
	}

	@Test
	void filenameSanitizerRejectsUnsafeCharacters() {
		assertThat(PdfFilename.fromQuotationNumber("Q-000001")).isEqualTo("Q-000001.pdf");
		assertThat(PdfFilename.fromQuotationNumber("Q/../evil\nname")).isEqualTo("Q_.._evil_name.pdf");
		assertThat(PdfFilename.fromQuotationNumber("   ")).isEqualTo("quotation.pdf");
	}

	private static String extractText(byte[] pdf) throws Exception {
		try (PDDocument document = Loader.loadPDF(pdf)) {
			return new PDFTextStripper().getText(document);
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

	private JsonNode createQuotation(String token, String customerId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/quotations")
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
										"unitPrice", new BigDecimal("100.00")))))))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private JsonNode createFinancialQuotation(String token, String customerId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/quotations")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"customerId", customerId,
								"discountType", "PERCENTAGE",
								"discountValue", 10,
								"taxRate", 18,
								"items", List.of(
										Map.of("description", "Item A", "quantity", 2, "unitPrice", 100),
										Map.of("description", "Item B", "quantity", 1, "unitPrice", 50))))))
				.andExpect(status().isCreated())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.totalAmount").value(265.50))
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}
}
