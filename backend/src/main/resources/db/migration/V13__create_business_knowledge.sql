CREATE TABLE knowledge_documents (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    created_by_user_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
    title VARCHAR(140) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    original_filename VARCHAR(180),
    content_type VARCHAR(120),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    indexed_at TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 1,
    embedding_provider VARCHAR(40),
    embedding_model VARCHAR(120),
    embedding_dimension INTEGER,
    CONSTRAINT knowledge_documents_status_chk CHECK (status IN ('READY', 'FAILED', 'DELETED')),
    CONSTRAINT knowledge_documents_source_type_chk CHECK (source_type IN ('TEXT', 'TXT'))
);

CREATE TABLE knowledge_chunks (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES knowledge_documents(id) ON DELETE CASCADE,
    document_version INTEGER NOT NULL,
    chunk_index INTEGER NOT NULL,
    text TEXT NOT NULL,
    embedding DOUBLE PRECISION[] NOT NULL,
    embedding_provider VARCHAR(40) NOT NULL,
    embedding_model VARCHAR(120) NOT NULL,
    embedding_dimension INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT knowledge_chunks_index_chk CHECK (chunk_index >= 0),
    CONSTRAINT knowledge_chunks_dimension_chk CHECK (embedding_dimension > 0),
    CONSTRAINT knowledge_chunks_vector_len_chk CHECK (array_length(embedding, 1) = embedding_dimension),
    CONSTRAINT knowledge_chunks_document_index_uk UNIQUE (document_id, document_version, chunk_index)
);

CREATE INDEX idx_knowledge_documents_business_status
    ON knowledge_documents (business_id, status, updated_at DESC);

CREATE INDEX idx_knowledge_chunks_tenant_model
    ON knowledge_chunks (business_id, embedding_provider, embedding_model, embedding_dimension);

CREATE OR REPLACE FUNCTION knowledge_cosine_similarity(a DOUBLE PRECISION[], b DOUBLE PRECISION[])
RETURNS DOUBLE PRECISION
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT CASE
        WHEN array_length(a, 1) IS NULL
          OR array_length(b, 1) IS NULL
          OR array_length(a, 1) <> array_length(b, 1)
        THEN NULL
        ELSE (
            WITH pairs AS (
                SELECT av.v AS av, bv.v AS bv
                FROM unnest(a) WITH ORDINALITY AS av(v, i)
                JOIN unnest(b) WITH ORDINALITY AS bv(v, i) USING (i)
            ),
            sums AS (
                SELECT sum(av * bv) AS dot,
                       sqrt(sum(av * av)) AS amag,
                       sqrt(sum(bv * bv)) AS bmag
                FROM pairs
            )
            SELECT CASE
                WHEN amag = 0 OR bmag = 0 THEN 0
                ELSE dot / (amag * bmag)
            END
            FROM sums
        )
    END;
$$;
