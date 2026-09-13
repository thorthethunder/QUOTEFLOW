package com.quoteflow.support;

/**
 * Phase 2 integration tests use real PostgreSQL (Docker Compose locally, CI service in GitHub Actions).
 * Database: quoteflow_test on localhost:5432 (see application-test.yml).
 * <p>
 * Testcontainers were evaluated but Docker client discovery failed in this Windows environment;
 * Compose/CI PostgreSQL still exercises native UUID, TIMESTAMPTZ, CHECK, and UNIQUE constraints.
 */
public abstract class PostgresIntegrationTest {
}
