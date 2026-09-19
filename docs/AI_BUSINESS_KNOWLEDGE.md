# AI Business Knowledge

Status: implemented locally, pending full PostgreSQL regression rerun in this workspace because the local test database was unavailable during the Phase 7 pass.

Business Knowledge is tenant-owned RAG for policies, terms, warranties, and service notes. It is not a general document-management system and does not ingest websites, email, cloud drives, invoices, customers, or arbitrary business data.

## Architecture

```text
Authenticated tenant user
  -> /api/v1/knowledge
  -> trusted businessId from JWT principal
  -> bounded text/TXT validation
  -> deterministic chunking
  -> KnowledgeEmbeddingProvider
  -> PostgreSQL knowledge_documents / knowledge_chunks
```

Query:

```text
question
  -> embed question
  -> SQL similarity query with WHERE business_id = authenticated business
  -> top-K bounded source chunks
  -> grounded AiProvider generation with no tools
```

Tenant isolation is enforced in the retrieval SQL. QuoteFlow does not retrieve global chunks and then filter in Java.

## Vector Storage

Phase 7 uses PostgreSQL tables with `double precision[]` embeddings plus a `knowledge_cosine_similarity` SQL function. This avoids requiring `pgvector` extension availability while keeping tenant filtering in the database query.

Stored chunk metadata includes `business_id`, `document_id`, `document_version`, `embedding_provider`, `embedding_model`, and `embedding_dimension`.

## Supported Sources

- Authored text: supported
- TXT upload: supported
- PDF: deferred
- Other file types: rejected

PDF support is intentionally deferred until safe extraction limits and production library behavior are separately validated.

## Limits

Configured under `quoteflow.ai.knowledge`, with hard ceilings in code:

- max file bytes: hard ceiling 1 MB
- max extracted text chars: hard ceiling 120,000
- max title chars: hard ceiling 140
- max chunks per document: hard ceiling 80
- chunk size: hard ceiling 1,500 chars
- overlap: hard ceiling 250 chars
- top-K: hard ceiling 8
- question: hard ceiling 1,000 chars

Client payloads cannot raise these limits.

## Embedding

Business code depends on `KnowledgeEmbeddingProvider`, not Ollama-specific DTOs.

Providers:

- `OLLAMA`: server-side `/api/embed`, default `OLLAMA_EMBEDDING_MODEL=mxbai-embed-large`
- `HASH`: deterministic test provider for integration tests

Dimension is configuration-driven with default `1024`. Re-indexing is required before changing embedding model or dimension for existing knowledge.

## Local Embedding Benchmark

Small development benchmark, not a universal quality proof.

Synthetic documents covered quotation validity, invoice payment terms, cancellation notice, and installation warranty with 8 paraphrased questions.

| Model | Available | Dimension | Top-1 | Top-3 | Top-5 | Approx latency |
|-------|-----------|-----------|-------|-------|-------|----------------|
| `nomic-embed-text` | yes | 768 | 2/8 | 6/8 | 8/8 | 47,923 ms total / 3,994 ms avg embed |
| `mxbai-embed-large` | yes | 1024 | 2/8 | 6/8 | 8/8 | 2,644 ms total / 220 ms avg embed |

`mxbai-embed-large` is the default because it tied retrieval quality on the small benchmark and was materially faster in this local run.

## Grounding

If no tenant chunks meet the relevance threshold, the API returns `grounded=false`, no sources, and a no-knowledge answer. The assistant must not answer unsupported tenant policy from general model knowledge.

## Source References

Source references are backend-generated from retrieved chunks. The model does not create authoritative source IDs.

## Deletion And Reindexing

Delete removes chunks and marks the document `DELETED`, so deleted knowledge is no longer searchable.

Text replacement removes old chunks before inserting chunks for the next document version. Retrieval joins on the active document version to avoid stale chunks.

## Security

- businessId is never accepted from the client
- provider/model/topK/vector/filter/system prompt are never accepted from the client
- HTML, JavaScript, executables, archives, Office files, and arbitrary binaries are rejected
- original uploads are not persisted to public disk
- no RAG tool callbacks are registered
- RAG generation cannot mutate invoices, payments, emails, or subscriptions
- prompt-injection text inside documents is treated as untrusted evidence

## Configuration

```text
AI_KNOWLEDGE_ENABLED=false
AI_EMBEDDING_PROVIDER=OLLAMA
OLLAMA_EMBEDDING_MODEL=mxbai-embed-large
AI_KNOWLEDGE_EMBEDDING_DIMENSION=1024
AI_KNOWLEDGE_TOP_K=5
AI_KNOWLEDGE_RELEVANCE_THRESHOLD=0.55
AI_KNOWLEDGE_MAX_FILE_SIZE=500000
AI_KNOWLEDGE_MAX_TEXT_CHARS=80000
AI_KNOWLEDGE_CHUNK_SIZE=900
AI_KNOWLEDGE_CHUNK_OVERLAP=120
AI_KNOWLEDGE_QUERY_RATE_USER=10
AI_KNOWLEDGE_QUERY_RATE_TENANT=30
```

Production AI/RAG remains disabled by default.

## Deferred

- PDF extraction
- external knowledge connectors
- website crawling
- Google Drive/Dropbox ingestion
- transactional business-data embeddings
- persistent AI memory
- autonomous agents
- AI quotas/cost controls
- managed AI providers
- production AI deployment
