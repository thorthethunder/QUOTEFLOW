# AI Agent Workflows (Phase 8)

> **Status: IMPLEMENTED** - durable, bounded workflow orchestration for payment follow-up.  
> Production workflows: **NOT DEPLOYED** by default (`AI_WORKFLOWS_ENABLED=false`).

## Principle

**Workflow automation coordinates approved steps. It does not approve actions.**

```text
User starts workflow
  -> backend creates ai_workflows row
  -> workflow runs explicit allowlisted steps
  -> payment reminder send proposals are created as AiActionProposal PENDING
  -> user reviews each proposal
  -> POST /api/v1/ai/actions/{id}/confirm
  -> existing payment reminder action executes through NotificationService
  -> workflow observes proposal outcomes and completes
```

The model cannot confirm, execute, or bypass human review. Phase 8 payment follow-up is deterministic and uses zero AI calls.

## Workflow types

| Type | Status | Purpose |
|------|--------|---------|
| `PAYMENT_FOLLOW_UP` | Implemented | Select outstanding sent invoices and prepare human-approved payment reminder sends |

## Step allowlist

| Step | Classification | Effect |
|------|----------------|--------|
| `READ_OUTSTANDING_INVOICES` | `READ_ONLY` | Deterministically reads tenant invoices with positive balance |
| `PREPARE_PAYMENT_REMINDER` | `ACTION_REQUIRES_APPROVAL` | Creates one `PAYMENT_REMINDER_SEND` proposal |
| `OBSERVE_ACTION_RESULT` | `READ_ONLY` | Reads proposal status and advances workflow state |

Unknown workflow or step types fail closed. There is no unrestricted shell, HTTP, SQL, repository, or arbitrary tool execution.

## Persistence

Flyway `V14__create_ai_workflows.sql` creates:

- `ai_workflows`
- `ai_workflow_steps`

Rows store tenant, requesting user, workflow type, explicit status, step input/output JSON, timestamps, and optimistic versions. Workflow idempotency is scoped by `(business_id, requested_by_user_id, workflow_type, idempotency_key)`.

## State Machines

Workflow statuses:

```text
CREATED -> RUNNING -> WAITING_FOR_APPROVAL -> COMPLETED
CREATED/RUNNING/WAITING_FOR_APPROVAL -> CANCELLED | EXPIRED | FAILED
WAITING_FOR_APPROVAL -> COMPLETED_WITH_PARTIAL_RESULTS
```

Step statuses:

```text
PENDING -> RUNNING -> COMPLETED | FAILED
PENDING/RUNNING -> CANCELLED | SKIPPED | EXPIRED
```

Transitions are centralized in `AiWorkflowStateMachine`. Services must not invent ad hoc status changes.

## Payment Follow-Up Behavior

Selection is deterministic:

```text
document_status = SENT
balance_due > 0
order by balance_due desc, due_date nulls last, invoice_number asc
limit maxItems
```

For each selected invoice, the workflow prepares an existing `PAYMENT_REMINDER_SEND` action proposal. Email is not queued until the authenticated user confirms that proposal through the existing action approval endpoint.

## API

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/v1/ai/workflows` | List current user's tenant-scoped workflows |
| POST | `/api/v1/ai/workflows` | Start a workflow |
| GET | `/api/v1/ai/workflows/{id}` | Read one workflow |
| POST | `/api/v1/ai/workflows/{id}/resume` | Re-observe approval outcomes and continue |
| POST | `/api/v1/ai/workflows/{id}/cancel` | Cancel workflow and pending proposals |

Cross-tenant or cross-user access returns 404.

## Configuration

```text
AI_WORKFLOWS_ENABLED=false
AI_WORKFLOW_MAX_STEPS=8
AI_WORKFLOW_MAX_ACTIONS=3
AI_WORKFLOW_MAX_PAYMENT_FOLLOW_UP_ITEMS=3
AI_WORKFLOW_MAX_AI_CALLS=0
AI_WORKFLOW_TTL=30m
AI_WORKFLOW_RATE_USER=3
AI_WORKFLOW_RATE_TENANT=10
```

Hard caps are enforced server-side: max 12 steps, max 5 action proposals, max 5 payment follow-up items, and max 0 AI calls for Phase 8.

## UI

Angular route: `/app/workflows`.

The page can start a payment follow-up workflow, display pending reminder proposals, approve/cancel individual proposals using the existing AI action approval APIs, resume observation, and cancel the workflow.

## Security Controls

1. Tenant and user are always derived from the authenticated principal.
2. Workflow actions reuse `AiActionProposal` integrity, TTL, locking, and confirm semantics.
3. No model output is treated as approval.
4. No action step executes side effects directly.
5. Prompt injection inside goal or stored business text cannot bypass the backend state machine.
6. Workflow limits bound steps, proposals, duration, and request rate.

## Related

- [AI_ACTION_APPROVALS.md](AI_ACTION_APPROVALS.md)
- [AI_PAYMENT_REMINDERS.md](AI_PAYMENT_REMINDERS.md)
- [AI_ARCHITECTURE.md](AI_ARCHITECTURE.md)
- [BUSINESS_COPILOT.md](BUSINESS_COPILOT.md)
- [SECURITY.md](SECURITY.md)
