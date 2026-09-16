package com.quoteflow.ai.structured.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Foundation / smoke schema for structured quotation drafts.
 * <p>
 * NOT persisted. Does NOT include authoritative totals — those belong to
 * {@code FinancialDocumentCalculator} after user confirmation.
 */
public record QuotationDraftProposal(
		@NotBlank @Size(max = 200) String customerName,
		@NotEmpty @Size(max = 50) List<@Valid LineItem> items,
		@Size(max = 2000) String notes
) {

	public record LineItem(
			@NotBlank @Size(max = 500) String description,
			@NotNull @DecimalMin(value = "0.0001") BigDecimal quantity,
			@NotNull @DecimalMin(value = "0") BigDecimal unitPrice
	) {
	}

	public static JsonNode jsonSchema(ObjectMapper mapper) {
		ObjectNode root = mapper.createObjectNode();
		root.put("type", "object");
		ArrayNode required = root.putArray("required");
		required.add("customerName");
		required.add("items");

		ObjectNode properties = root.putObject("properties");
		properties.putObject("customerName").put("type", "string");
		properties.putObject("notes").put("type", "string");

		ObjectNode items = properties.putObject("items");
		items.put("type", "array");
		ObjectNode item = items.putObject("items");
		item.put("type", "object");
		ArrayNode itemRequired = item.putArray("required");
		itemRequired.add("description");
		itemRequired.add("quantity");
		itemRequired.add("unitPrice");
		ObjectNode itemProps = item.putObject("properties");
		itemProps.putObject("description").put("type", "string");
		itemProps.putObject("quantity").put("type", "number");
		itemProps.putObject("unitPrice").put("type", "number");

		root.put("additionalProperties", false);
		return root;
	}
}
