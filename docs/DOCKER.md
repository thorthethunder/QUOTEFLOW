# QuoteFlow Docker

## Images

| Image | Dockerfile | Base runtime | Purpose |
|-------|------------|--------------|---------|
| Backend | `backend/Dockerfile` | `eclipse-temurin:21-jre-alpine` | Production Spring Boot JAR, non-root `quoteflow` |
| Frontend | `frontend/Dockerfile` | `nginx:1.27-alpine` | Optional local static SPA + `/api` proxy |

Build context is each app directory (Maven wrapper / npm lockfile for reproducible builds).

```bash
docker build -t quoteflow-backend -f backend/Dockerfile backend
docker build -t quoteflow-frontend --build-arg API_BASE_URL=/api/v1 -f frontend/Dockerfile frontend
```

Architecture assumption: **linux/amd64** (or host default). Multi-arch not required in Phase 15.

## Compose files

| File | Purpose |
|------|---------|
| `docker-compose.yml` | **Dev only** — PostgreSQL 16 |
| `docker-compose.prod-local.yml` | **Prod-like local smoke** — postgres + backend + frontend |

```bash
# Dev DB (unchanged)
docker compose up -d

# Prod-like stack
cp .env.example .env.prod-local
# Edit JWT_SECRET (≥32), passwords, CORS_ALLOWED_ORIGINS=http://localhost:8088
docker compose -f docker-compose.prod-local.yml --env-file .env.prod-local up --build
```

Frontend: http://127.0.0.1:8088 (nginx proxies `/api` → backend).  
Backend direct: http://127.0.0.1:8089  

Compose Postgres wait does **not** replace production startup policy on managed platforms.

## Security practices

- Non-root backend user
- No secrets in image layers (runtime env only)
- No `--privileged`, no Docker socket mounts
- Postgres bound to `127.0.0.1` in prod-local compose
- Logs to stdout/stderr
- UTC (`TZ=UTC`)
- Backend healthcheck: `curl` → `/actuator/health/liveness` (curl installed in runtime image solely for probes)

## Image vulnerability scan

Run Trivy (or equivalent) when available:

```bash
trivy image quoteflow-backend
trivy image quoteflow-frontend
```

If unavailable: report **NOT COMPLETED** — do not invent a clean result.

## JVM memory

Optional: `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0` (default in image). Do not hardcode huge `-Xmx` without measurement.

## Read-only filesystem

Business data lives in PostgreSQL. PDF/email use in-memory streams. Container may need writable `/tmp` if the JVM requires it; do not persist documents on the container filesystem.
