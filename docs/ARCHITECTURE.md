# SecureBank — Architecture and Design

This document is the Phase 1 design. Everything in the code follows it; when a decision below
changed during implementation, this file was updated to match.

## 1. Style

A **layered modular monolith**: one deployable Spring Boot application, one PostgreSQL database,
code split by layer and by feature. No microservices, no message broker, no cache. A transfer has
to touch two accounts, a ledger row, an audit row and an idempotency row atomically; one database
transaction is the simplest correct way to get that.

```
Client
  │  HTTPS + Authorization: Bearer <JWT>, X-Request-ID, Idempotency-Key
  ▼
RequestIdFilter ........ assigns/propagates X-Request-ID, puts it in the logging MDC
  ▼
Spring Security chain .. JwtAuthenticationFilter → SecurityContext → URL rules → @PreAuthorize
  ▼
Controller ............. HTTP only: DTO validation, status codes, headers. No business logic.
  ▼
Service ................ business rules, @Transactional boundaries, audit, idempotency
  ▼
Repository ............. Spring Data JPA
  ▼
PostgreSQL ............. constraints (unique, FK, CHECK balance >= 0), indexes, Flyway migrations
```

### Transfer flow

```
POST /api/v1/transfers  (Idempotency-Key required)
  → JWT filter authenticates customer
  → TransferController validates TransferRequest
  → MoneyOperationFacade
       1. IdempotencyService.claim()         own short transaction, unique(customer_id, key)
            - key new           → row IN_PROGRESS, continue
            - same key, done    → return stored response (no money moves)
            - same key, running → 409
            - same key, other payload → 422
       2. TransactionTemplate (one DB transaction) {
            TransferService.transfer()
              load source (must be owned by caller) and destination
              validate: not same account, both ACTIVE, sufficient balance
              debit + credit in ascending account-id order, flush (optimistic @Version check)
              insert BankTransaction (SUCCESS)
              insert AuditLog (TRANSFER, SUCCESS)
            IdempotencyService.complete()    stores response JSON in the SAME transaction
          } COMMIT
       3. on any exception: transaction already rolled back →
            release idempotency claim, write FAILED BankTransaction (business-rule failures only)
            and FAILURE AuditLog in a new transaction, rethrow → GlobalExceptionHandler
```

## 2. Database schema

Managed by Flyway (`src/main/resources/db/migration`). Hibernate never generates DDL
(`ddl-auto: none`), so the schema reviewed in Git is the schema that runs.

| Table | Key columns | Constraints / indexes |
|---|---|---|
| `customers` | id, full_name, email, phone, password_hash, role, created_at, updated_at | `uk_customers_email`, role CHECK |
| `accounts` | id, account_number, customer_id, account_type, status, balance `NUMERIC(19,2)`, currency, version, timestamps | `uk_accounts_account_number`, FK customer, **CHECK balance >= 0**, `idx_accounts_customer_id` |
| `bank_transactions` | id, transaction_reference, type, status, amount, source_account_id, destination_account_id, description, failure_reason, initiated_by, request_id, created_at | `uk_bank_transactions_reference`, FKs, CHECK amount > 0, `idx_txn_source_created (source_account_id, created_at)`, `idx_txn_destination_created (destination_account_id, created_at)` |
| `audit_logs` | id, customer_id, action, resource, resource_id, status, description, ip_address, request_id, created_at | `idx_audit_customer_created`, `idx_audit_action_created` |
| `idempotency_records` | id, idempotency_key, customer_id, operation, request_hash, status, response_status, response_body, created_at, completed_at | **`uk_idempotency_customer_key (customer_id, idempotency_key)`** |

Relationships:

```
customers 1 ──── N accounts
accounts  1 ──── N bank_transactions   (as source_account_id or destination_account_id)
customers 1 ──── N audit_logs          (customer_id, nullable: LOGIN_FAILED for unknown email)
customers 1 ──── N idempotency_records
```

`audit_logs.customer_id` intentionally has no foreign key. Audit rows must be writable for
unknown users (failed logins) and must never be blocked or cascaded by changes to customer data.

## 3. API

| Method | Path | Auth | Success |
|---|---|---|---|
| POST | /api/v1/auth/register | public | 201 |
| POST | /api/v1/auth/login | public | 200 |
| GET | /api/v1/customers/me | customer | 200 |
| PUT | /api/v1/customers/me | customer | 200 |
| POST | /api/v1/accounts | customer | 201 + Location |
| GET | /api/v1/accounts | customer | 200 |
| GET | /api/v1/accounts/{accountNumber} | owner | 200 |
| GET | /api/v1/accounts/{accountNumber}/balance | owner | 200 |
| POST | /api/v1/accounts/{accountNumber}/deposit | owner | 201 (Idempotency-Key optional) |
| POST | /api/v1/accounts/{accountNumber}/withdraw | owner | 201 (Idempotency-Key optional) |
| POST | /api/v1/transfers | owner of source | 201 (Idempotency-Key **required**) |
| GET | /api/v1/accounts/{accountNumber}/transactions | owner | 200, paged |
| PATCH | /api/v1/admin/accounts/{accountNumber}/status | ADMIN | 200 |
| GET | /api/v1/admin/audit-logs | ADMIN | 200, paged |
| GET | /actuator/health, /actuator/info | public | 200 |

Error status codes: 400 validation / malformed input, 401 missing or bad token or bad credentials,
403 not your account or not admin, 404 unknown resource, 409 duplicate email / concurrent update /
same idempotency key still processing, 422 well-formed request that breaks a business rule
(insufficient balance, blocked account, same-account transfer, key reused with a different payload),
429 too many failed logins.

## 4. Security

* Registration: Bean Validation → email normalised to lower case → BCrypt (strength 12) → saved.
* Login: `AuthenticationManager` (DaoAuthenticationProvider + `CustomUserDetailsService`) checks
  the BCrypt hash. Success issues an HS256 JWT: `sub` = customer id, `email`, `role`, `iss`, `iat`,
  `exp` (30 min default). Failures are counted per email by `LoginAttemptService`; 5 failures lock
  login for that email for 15 minutes (in memory, single instance).
* Each request: `JwtAuthenticationFilter` verifies signature, issuer and expiry and puts an
  `AuthenticatedCustomer` principal in the `SecurityContext`. No HTTP session is created.
* Authorization: URL rules (`/api/v1/admin/**` needs ROLE_ADMIN) plus ownership checks in the
  service layer (every account lookup goes through `AccountService.getOwnedAccount`).
* Secrets: `JWT_SECRET`, DB credentials and admin bootstrap credentials come from environment
  variables. Request DTOs holding passwords override `toString()` so they cannot leak into logs.

## 5. Key decisions

| Decision | Choice | Why |
|---|---|---|
| Money type | `BigDecimal`, `NUMERIC(19,2)` | exact decimal arithmetic; no binary rounding |
| Concurrency | optimistic locking (`@Version`) on `Account` | no locks held while reading; conflicting writer gets a clean 409 and can retry with the same idempotency key |
| Deadlock avoidance | accounts updated in ascending id order | two opposite transfers (A→B, B→A) take row locks in the same order |
| Last line of defence | `CHECK (balance >= 0)` in PostgreSQL | even a bug in Java cannot persist an overdraft |
| Idempotency | DB row with unique `(customer_id, key)`, request hash, stored response, completed in the same transaction as the money movement | duplicates are stopped by the database, not by a check-then-insert race |
| Failure records | written after rollback in a separate transaction | a failed transfer's evidence survives the rollback of the transfer itself |
| Schema | Flyway SQL migrations | reviewable, versioned DDL with constraints and indexes |
| DTOs | Java records, mapped by static `from()` factories | no entity leaks, no mapping library |
| Lombok | not used | entities are small; explicit code is easier to explain |

## 6. Package structure

```
com.securebank
├── SecureBankApplication
├── config        OpenAPI, Clock, configuration properties, admin bootstrap
├── controller    REST controllers (thin)
├── dto           request/response records: auth, customer, account, transaction, admin, common
├── entity        JPA entities and enums
├── exception     ErrorCode, BankingException hierarchy, GlobalExceptionHandler
├── repository    Spring Data repositories
├── security      SecurityConfig, JWT, filters, entry point, login attempt protection
├── service       auth, customer, account, history, audit
│   ├── money         facade, cash (deposit/withdraw), transfer, failure recorder
│   └── idempotency   claim / replay / complete / release
├── util          account number and reference generators, masking
└── web           RequestIdFilter, request context (MDC keys)
```

## 7. Future enhancement: recurring transfers (design only)

```
recurring_transfers(id, customer_id, source_account_id, destination_account_id, amount,
                    frequency [DAILY|WEEKLY|MONTHLY], next_execution_date, end_date,
                    status [ACTIVE|PAUSED|CANCELLED], last_run_reference, version)
```

A `@Scheduled` job would select due rows with `FOR UPDATE SKIP LOCKED` (so two instances never
pick the same row) and call the existing `MoneyOperationFacade` with a deterministic idempotency
key such as `recurring-{id}-{executionDate}`. Re-running a job after a crash then cannot debit
twice. Not implemented: it adds scheduling and failure-retry policy that the project does not need
to demonstrate its core ideas.
