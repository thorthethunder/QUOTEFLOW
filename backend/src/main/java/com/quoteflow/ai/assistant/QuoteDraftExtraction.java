package com.quoteflow.ai.assistant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Model extraction schema for Quote Assistant. Untrusted until validated.
 * Quantities/prices may be null when missing — do not hallucinate silently.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuoteDraftExtraction(
		@Size(max = 200) String customerName,
		@Size(max = 50) List<@Valid ExtractedLine> items,
		@Size(max = 2000) String notes,
		BigDecimal discountPercent,
		BigDecimal taxRatePercent,
		@Size(max = 20) List<@Size(max = 500) String> ambiguities
) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ExtractedLine(
			@Size(max = 500) String description,
			@DecimalMin(value = "0.0001", inclusive = true) BigDecimal quantity,
			@DecimalMin(value = "0.0") BigDecimal unitPrice,
			boolean needsReview
	) {
	}

	public static JsonNode jsonSchema(ObjectMapper mapper) {
		ObjectNode root = mapper.createObjectNode();
		root.put("type", "object");
		ObjectNode properties = root.putObject("properties");
		properties.putObject("customerName").put("type", "string");
		properties.putObject("notes").put("type", "string");
		properties.putObject("discountPercent").put("type", "number");
		properties.putObject("taxRatePercent").put("type", "number");

		ObjectNode items = properties.putObject("items");
		items.put("type", "array");
		ObjectNode item = items.putObject("items");
		item.put("type", "object");
		ObjectNode itemProps = item.putObject("properties");
		itemProps.putObject("description").put("type", "string");
		itemProps.putObject("quantity").put("type", "number");
		itemProps.putObject("unitPrice").put("type", "number");
		itemProps.putObject("needsReview").put("type", "boolean");

		ObjectNode ambiguities = properties.putObject("ambiguities");
		ambiguities.put("type", "array");
		ambiguities.putObject("items").put("type", "string");

		ArrayNode required = root.putArray("required");
		required.add("customerName");
		required.add("items");
		root.put("additionalProperties", false);
		return root;
	}
}
