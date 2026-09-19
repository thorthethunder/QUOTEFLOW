package com.quoteflow.ai.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class KnowledgeRepository {

	private final JdbcTemplate jdbcTemplate;

	public KnowledgeRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public void saveDocumentWithChunks(
			KnowledgeDocumentRow document,
			List<String> chunks,
			List<KnowledgeEmbedding> embeddings) {
		jdbcTemplate.update("""
				INSERT INTO knowledge_documents (
				  id, business_id, created_by_user_id, title, source_type, original_filename,
				  content_type, status, created_at, updated_at, indexed_at, version,
				  embedding_provider, embedding_model, embedding_dimension
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				document.id(),
				document.businessId(),
				document.createdByUserId(),
				document.title(),
				document.sourceType().name(),
				document.originalFilename(),
				document.contentType(),
				document.status().name(),
				Timestamp.from(document.createdAt()),
				Timestamp.from(document.updatedAt()),
				document.indexedAt() == null ? null : Timestamp.from(document.indexedAt()),
				document.version(),
				embeddings.isEmpty() ? null : embeddings.getFirst().provider(),
				embeddings.isEmpty() ? null : embeddings.getFirst().model(),
				embeddings.isEmpty() ? null : embeddings.getFirst().dimension());
		insertChunks(document, chunks, embeddings);
	}

	@Transactional
	public KnowledgeDocumentRow replaceText(
			UUID businessId,
			UUID documentId,
			String title,
			List<String> chunks,
			List<KnowledgeEmbedding> embeddings,
			Instant now) {
		KnowledgeDocumentRow existing = findByBusinessAndId(businessId, documentId);
		int nextVersion = existing.version() + 1;
		jdbcTemplate.update("DELETE FROM knowledge_chunks WHERE business_id = ? AND document_id = ?", businessId, documentId);
		jdbcTemplate.update("""
				UPDATE knowledge_documents
				   SET title = ?, status = 'READY', updated_at = ?, indexed_at = ?, version = ?,
				       embedding_provider = ?, embedding_model = ?, embedding_dimension = ?
				 WHERE business_id = ? AND id = ? AND status <> 'DELETED'
				""",
				title,
				Timestamp.from(now),
				Timestamp.from(now),
				nextVersion,
				embeddings.getFirst().provider(),
				embeddings.getFirst().model(),
				embeddings.getFirst().dimension(),
				businessId,
				documentId);
		KnowledgeDocumentRow updated = new KnowledgeDocumentRow(
				existing.id(), existing.businessId(), existing.createdByUserId(), title, existing.sourceType(),
				existing.originalFilename(), existing.contentType(), KnowledgeDocumentStatus.READY,
				existing.createdAt(), now, now, nextVersion);
		insertChunks(updated, chunks, embeddings);
		return updated;
	}

	private void insertChunks(
			KnowledgeDocumentRow document,
			List<String> chunks,
			List<KnowledgeEmbedding> embeddings) {
		for (int i = 0; i < chunks.size(); i++) {
			final int chunkIndex = i;
			final String chunkText = chunks.get(i);
			final KnowledgeEmbedding embedding = embeddings.get(i);
			jdbcTemplate.update(con -> {
				var ps = con.prepareStatement("""
						INSERT INTO knowledge_chunks (
						  id, business_id, document_id, document_version, chunk_index, text,
						  embedding, embedding_provider, embedding_model, embedding_dimension, created_at
						) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						""");
				ps.setObject(1, UUID.randomUUID());
				ps.setObject(2, document.businessId());
				ps.setObject(3, document.id());
				ps.setInt(4, document.version());
				ps.setInt(5, chunkIndex);
				ps.setString(6, chunkText);
				Array vector = con.createArrayOf("float8", embedding.vector().toArray());
				ps.setArray(7, vector);
				ps.setString(8, embedding.provider());
				ps.setString(9, embedding.model());
				ps.setInt(10, embedding.dimension());
				ps.setTimestamp(11, Timestamp.from(document.indexedAt()));
				return ps;
			});
		}
	}

	public List<KnowledgeDocumentRow> list(UUID businessId) {
		return jdbcTemplate.query("""
				SELECT id, business_id, created_by_user_id, title, source_type, original_filename,
				       content_type, status, created_at, updated_at, indexed_at, version
				  FROM knowledge_documents
				 WHERE business_id = ? AND status <> 'DELETED'
				 ORDER BY updated_at DESC
				""", documentMapper(), businessId);
	}

	public KnowledgeDocumentRow findByBusinessAndId(UUID businessId, UUID id) {
		List<KnowledgeDocumentRow> rows = jdbcTemplate.query("""
				SELECT id, business_id, created_by_user_id, title, source_type, original_filename,
				       content_type, status, created_at, updated_at, indexed_at, version
				  FROM knowledge_documents
				 WHERE business_id = ? AND id = ? AND status <> 'DELETED'
				""", documentMapper(), businessId, id);
		if (rows.isEmpty()) {
			throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Knowledge document not found");
		}
		return rows.getFirst();
	}

	@Transactional
	public void delete(UUID businessId, UUID id) {
		KnowledgeDocumentRow row = findByBusinessAndId(businessId, id);
		jdbcTemplate.update("DELETE FROM knowledge_chunks WHERE business_id = ? AND document_id = ?", businessId, row.id());
		jdbcTemplate.update("""
				UPDATE knowledge_documents
				   SET status = 'DELETED', updated_at = ?
				 WHERE business_id = ? AND id = ?
				""", Timestamp.from(Instant.now()), businessId, id);
	}

	public List<KnowledgeSearchResult> search(
			UUID businessId,
			KnowledgeEmbedding query,
			double threshold,
			int topK) {
		return jdbcTemplate.query(con -> {
			var ps = con.prepareStatement("""
					SELECT kc.id AS chunk_id, kc.document_id, kd.title, kc.chunk_index, kc.text,
					       knowledge_cosine_similarity(kc.embedding, ?) AS score
					  FROM knowledge_chunks kc
					  JOIN knowledge_documents kd
					    ON kd.id = kc.document_id
					   AND kd.business_id = kc.business_id
					   AND kd.version = kc.document_version
					   AND kd.status = 'READY'
					 WHERE kc.business_id = ?
					   AND kc.embedding_provider = ?
					   AND kc.embedding_model = ?
					   AND kc.embedding_dimension = ?
					   AND knowledge_cosine_similarity(kc.embedding, ?) >= ?
					 ORDER BY score DESC, kc.chunk_index ASC
					 LIMIT ?
					""");
			Array vector = con.createArrayOf("float8", query.vector().toArray());
			ps.setArray(1, vector);
			ps.setObject(2, businessId);
			ps.setString(3, query.provider());
			ps.setString(4, query.model());
			ps.setInt(5, query.dimension());
			Array vector2 = con.createArrayOf("float8", query.vector().toArray());
			ps.setArray(6, vector2);
			ps.setDouble(7, threshold);
			ps.setInt(8, topK);
			return ps;
		}, (rs, rowNum) -> new KnowledgeSearchResult(
				rs.getObject("chunk_id", UUID.class),
				rs.getObject("document_id", UUID.class),
				rs.getString("title"),
				rs.getInt("chunk_index"),
				rs.getString("text"),
				rs.getDouble("score")));
	}

	private static RowMapper<KnowledgeDocumentRow> documentMapper() {
		return (rs, rowNum) -> new KnowledgeDocumentRow(
				rs.getObject("id", UUID.class),
				rs.getObject("business_id", UUID.class),
				rs.getObject("created_by_user_id", UUID.class),
				rs.getString("title"),
				KnowledgeSourceType.valueOf(rs.getString("source_type")),
				rs.getString("original_filename"),
				rs.getString("content_type"),
				KnowledgeDocumentStatus.valueOf(rs.getString("status")),
				rs.getTimestamp("created_at").toInstant(),
				rs.getTimestamp("updated_at").toInstant(),
				rs.getTimestamp("indexed_at") == null ? null : rs.getTimestamp("indexed_at").toInstant(),
				rs.getInt("version"));
	}
}
