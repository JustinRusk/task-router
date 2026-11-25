# task-router

A rules-based IVR call routing service built with Java 21, Spring Boot 4 and PostgreSQL.

An IVR sends the call's details (caller number, dialed number, and any DTMF menu input). The service picks a destination queue from a set of prioritized routing rules, records the decision, and returns it. I built it as interview practice to sharpen my Spring Boot, JPA and Postgres skills on a problem from my own domain: contact center routing.

> **Scope:** this routes interactions to **queues** using rules. It does not yet choose an **agent**. Agent selection (skills, proficiency, availability, atomic reservation) is the planned next step. See [Next steps](#next-steps).

## How it works

1. The IVR calls `POST /api/interactions/route` with a `callId` from the telephony platform.
2. The service checks whether that `callId` was already handled. If so, it returns the original decision (idempotent retries).
3. The routing engine evaluates the enabled rules in ascending priority order. The first rule whose conditions all match wins. A `null` condition matches anything.
4. The decision (queue, matching rule, time) is saved on the interaction and returned.

Rule conditions:

| Condition | Matches when |
| --- | --- |
| `dialed_number` | The dialed number (DNIS) equals it exactly |
| `caller_prefix` | The caller number (ANI) starts with it |
| `dtmf_input` | The caller's DTMF input equals it exactly |

## Running locally

**Requirements:** Java 21, Docker.

**1. Start PostgreSQL**

```bash
docker run -d --name pg -e POSTGRES_PASSWORD=dev -p 5432:5432 postgres:16
```

**2. Create the schema and load sample rules**

macOS / Linux:

```bash
docker exec -i pg psql -U postgres < src/main/resources/db/schema.sql
docker exec -i pg psql -U postgres < src/main/resources/db/seed.sql
```

Windows PowerShell:

```powershell
Get-Content src/main/resources/db/schema.sql | docker exec -i pg psql -U postgres
Get-Content src/main/resources/db/seed.sql   | docker exec -i pg psql -U postgres
```

**3. Run the app**

The database connection is set with environment variables. `DB_URL` defaults to `jdbc:postgresql://localhost:5432/postgres`.

macOS / Linux:

```bash
DB_PASSWORD=dev ./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
$env:DB_PASSWORD = "dev"
.\mvnw.cmd spring-boot:run
```

Health check: `GET http://localhost:8080/actuator/health`

## API

| Method | Path | What it does |
| --- | --- | --- |
| `POST` | `/api/interactions/route` | Create and route in one call (what an IVR uses). 201 when new; 200 for a retry with the same `callId` |
| `POST` | `/api/interactions` | Create without routing |
| `POST` | `/api/interactions/{id}/route` | Route an existing interaction. Safe to retry |
| `GET` | `/api/interactions/{id}` | Get one interaction and its routing decision |
| `GET` | `/api/interactions` | Search, newest first. Optional filters: `callId`, `callerNumber`, `dialedNumber`, `status`, `queue`, `from`, `to`, `page`, `size` (max 200) |

Example: a caller dials the billing line and presses 2.

```http
POST /api/interactions/route
Content-Type: application/json

{
  "callId": "call-0001",
  "callerNumber": "+15125550123",
  "dialedNumber": "+18005550100",
  "dtmfInput": "2"
}
```

The response includes the chosen `routedQueue` (here `billing`), the `matchedRule` that produced the decision, `status: ROUTED`, `routedAt`, and `persisted: true`.

Errors use the RFC 9457 problem format (`application/problem+json`):

| Status | When |
| --- | --- |
| 400 | Validation failed; the `errors` field lists each invalid field |
| 404 | Interaction not found |
| 409 | A `callId` was reused with different call details, or an interaction can no longer be routed |
| 422 | No enabled rule matched (usually means the catch-all rule is missing) |
| 503 | Database unavailable (except for create-and-route; see below) |

A Postman collection is in [`postman/`](postman/).

## Design decisions

- **Idempotent on `callId`.** Telephony platforms retry requests, so a repeated `callId` returns the original decision instead of creating a duplicate. A unique constraint covers two identical requests that arrive at the same moment: the second insert fails, and the service re-reads the winning row in a new transaction. A new one is needed because PostgreSQL rejects further statements in a transaction that has already hit an error.
- **No double routing.** `POST /{id}/route` locks the row (`SELECT … FOR UPDATE`), so two concurrent requests cannot route the same interaction twice.
- **Rules cached in memory.** Routing a call never queries the database for rules. The cache reloads on a schedule (`routing.rules.refresh-interval`, default 30s) and swaps in the complete new list in one atomic step, so a request never sees a half-updated rule set. If a reload fails, the previous rules stay in use.
- **Calls keep routing during a database outage.** A caller in the IVR should never wait on the database. If it is unreachable, create-and-route still answers from the cached rules, returns the decision with `persisted: false`, and logs it so the call can be traced. A circuit breaker skips the database for `routing.db.retry-after` (default 5s) before trying again. Short connection and socket timeouts make it fail fast.
- **Routing logic is pure.** `RoutingEngine` has no database access, so every rule combination is unit tested directly.
- **No N+1 queries.** The search loads each interaction's queue and matched rule in the same query (`@EntityGraph`), and rules are loaded with their queues (`join fetch`). Open-session-in-view is turned off.

## Tests

```bash
./mvnw test
```

| Test class | What it covers |
| --- | --- |
| `RoutingEngineTest` | Rule priority, condition combinations, DTMF matching, disabled rules, catch-all |
| `RuleCacheTest` | Initial load, refresh, keeping old rules when a refresh fails, read-only list |
| `DatabaseCircuitBreakerTest` | Opening, retry window, closing, telling outages from other errors |
| `InteractionServiceOutageTest` | Routing without saving during an outage, recovery, errors that are not outages |
| `TaskRouterApplicationTests` | Application context starts (needs a running database) |

## Performance

Tested with Postman against a local PostgreSQL instance.

| Scenario | Virtual users | Duration | Requests/sec | p95 latency | p99 latency | Error rate |
| --- | --- | --- | --- | --- | --- | --- |
| Create and route | _fill in_ | _fill in_ | _fill in_ | _fill in_ | _fill in_ | _fill in_ |
| Search | _fill in_ | _fill in_ | _fill in_ | _fill in_ | _fill in_ | _fill in_ |

_Fill in: hardware, and what the bottleneck was._

## Next steps

- **Agent selection:** agents with skills and proficiency; pick the best available agent and reserve it atomically (`FOR UPDATE SKIP LOCKED` or a conditional update) so no agent is assigned twice.
- **Response DTOs:** return an `InteractionResponse` record instead of the entity, so the API contract is independent of the schema.
- **Flyway migrations** with `ddl-auto=validate`, instead of `schema.sql` plus `ddl-auto=update`.
- **Testcontainers** for integration tests against a real PostgreSQL.
- **Events:** publish routing decisions through an outbox table for reporting and workforce management.
