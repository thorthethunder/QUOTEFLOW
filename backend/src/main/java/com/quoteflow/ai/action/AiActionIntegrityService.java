package com.quoteflow.ai.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Deterministic canonical JSON + SHA-256 integrity for reviewed action payloads.
 */
@Component
public class AiActionIntegrityService {

	private final ObjectMapper canonicalMapper;

	public AiActionIntegrityService(ObjectMapper objectMapper) {
		this.canonicalMapper = objectMapper.copy()
				.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
	}

	public String toCanonicalJson(Object payload) {
		try {
			JsonNode tree = canonicalMapper.valueToTree(payload);
			JsonNode sorted = sort(tree);
			return canonicalMapper.writeValueAsString(sorted);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to canonicalize action payload", ex);
		}
	}

	public String hash(AiActionType actionType, UUID businessId, UUID userId, String canonicalPayloadJson) {
		String material = actionType.name()
				+ "\n"
				+ businessId
				+ "\n"
				+ userId
				+ "\n"
				+ canonicalPayloadJson;
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to hash action payload", ex);
		}
	}

	public boolean matches(
			AiActionProposal proposal,
			String expectedCanonicalJson) {
		String recomputed = hash(
				proposal.getActionType(),
				proposal.getBusinessId(),
				proposal.getRequestedByUserId(),
				expectedCanonicalJson);
		return MessageDigest.isEqual(
				proposal.getPayloadHash().getBytes(StandardCharsets.UTF_8),
				recomputed.getBytes(StandardCharsets.UTF_8));
	}

	public boolean verifyStored(AiActionProposal proposal) {
		return matches(proposal, proposal.getPayloadJson());
	}

	private JsonNode sort(JsonNode node) {
		if (node == null || !node.isObject()) {
			return node;
		}
		ObjectNode sorted = canonicalMapper.createObjectNode();
		TreeMap<String, JsonNode> fields = new TreeMap<>();
		Iterator<Map.Entry<String, JsonNode>> it = node.fields();
		while (it.hasNext()) {
			Map.Entry<String, JsonNode> e = it.next();
			fields.put(e.getKey(), sort(e.getValue()));
		}
		fields.forEach(sorted::set);
		return sorted;
	}
}
