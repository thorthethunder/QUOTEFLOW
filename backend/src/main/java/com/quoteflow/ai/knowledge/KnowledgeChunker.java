package com.quoteflow.ai.knowledge;

import com.quoteflow.ai.config.AiProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class KnowledgeChunker {

	private final AiProperties properties;

	public KnowledgeChunker(AiProperties properties) {
		this.properties = properties;
	}

	public List<String> chunk(String text) {
		String normalized = normalize(text);
		int target = properties.getKnowledge().getChunkSize();
		int overlap = Math.min(properties.getKnowledge().getChunkOverlap(), target / 3);
		int max = properties.getKnowledge().getMaxChunksPerDocument();
		List<String> chunks = new ArrayList<>();
		int index = 0;
		while (index < normalized.length() && chunks.size() < max) {
			int end = Math.min(normalized.length(), index + target);
			if (end < normalized.length()) {
				int boundary = Math.max(normalized.lastIndexOf("\n\n", end), normalized.lastIndexOf(". ", end));
				if (boundary > index + target / 2) {
					end = boundary + (normalized.charAt(boundary) == '.' ? 1 : 0);
				}
			}
			String chunk = normalized.substring(index, end).trim();
			if (!chunk.isBlank()) {
				chunks.add(chunk);
			}
			if (end >= normalized.length()) {
				index = normalized.length();
				break;
			}
			index = Math.max(end - overlap, index + 1);
		}
		if (index < normalized.length()) {
			throw new IllegalArgumentException("Document exceeds maximum chunk count");
		}
		return chunks;
	}

	public static String normalize(String text) {
		if (text == null) {
			return "";
		}
		return text
				.replace('\u0000', ' ')
				.replace("\r\n", "\n")
				.replace('\r', '\n')
				.replaceAll("[\\t\\x0B\\f]+", " ")
				.replaceAll(" *\\n *", "\n")
				.replaceAll("\\n{3,}", "\n\n")
				.trim();
	}
}
