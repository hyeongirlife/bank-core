---
name: interest-domain
description: Use when designing or implementing interest-domain features in this repository and you need repository-specific domain knowledge about accrual, settlement, domain boundaries, and contract-sensitive decisions.
---

# Interest Domain

## Status
Draft. This document is intentionally design-first and should be corrected by user feedback before being treated as authoritative.

## Purpose
- Preserve the user's intended interest-domain model while implementing.
- Prevent accidental leakage of interest rules into `account`, `rate`, `transfer`, `transaction`, or `common`.
- Surface unresolved design questions before code is written.

## Non-goals
- This is not a generic checklist.
- This is not a substitute for implementation planning or TDD.
- This is not a claim that current code fully captures the intended design.

## 1. Domain Overview
`interest` owns the rules for how interest is calculated, accumulated, and settled for an account product.

At a high level, the domain should answer these questions:
- When does interest start to accrue?
- What balance is the calculation based on?
- Which rates apply on a given business date?
- When does accrued interest become finalized settlement?
- What is different about maturity settlement and early termination settlement?

### Boundary assumptions
- `account` owns account lifecycle, status, closure, and the account's stored balance.
- `rate` owns rate definitions and lookup rules by business date, product, and condition.
- `interest` owns accrual and settlement semantics built on top of account lifecycle and rate inputs.
- `transaction` owns auditable posting records created by finalized monetary events.
- `transfer` owns funds movement between accounts and should not become the home of interest generation rules.

### Design principle
`interest` may read lifecycle and rate facts from neighboring domains, but it should not silently redefine them.

## 2. Core Concepts

### Daily accrual
A per-business-date calculation of interest for an eligible account.

### Settlement
The point at which accrued interest is finalized into a recognized payable/posting outcome. Accrual and settlement should not be treated as the same thing by default.

### Maturity settlement
Settlement that occurs because the product reached its contractual maturity condition.

### Early termination settlement
Settlement that occurs because the account was closed before contractual maturity.

### Business date
The financial date that determines product/rate applicability and accrual meaning. This is distinct from request time or transaction timestamp.

### Balance snapshot
The balance amount used as the basis for an accrual calculation. The snapshot rule must be explicit; otherwise interest calculations become non-auditable.

### Applied rate
The effective rate used for a given accrual or settlement path. The source and composition rule must be explicit and reproducible.

## 3. Observed Current Implementation Notes
Verified against the current repository tree on 2026-04-14. These are observations from the current codebase, not permanent design truth.

### Accrual path
- `DailyInterestAccrualService` is the current accrual entry point: `src/main/kotlin/com/bankcore/interest/service/DailyInterestAccrualService.kt:22`.
- A single `InterestLog` is allowed per account and business date via DB uniqueness: `src/main/kotlin/com/bankcore/interest/entity/InterestLog.kt:19`.
- Current code appears to reject closed accounts and business dates outside the account lifecycle: `src/main/kotlin/com/bankcore/interest/service/DailyInterestAccrualService.kt:95`.
- Current code appears to calculate applied rate as base + spread + preferential, scaled to 4 decimals: `src/main/kotlin/com/bankcore/interest/service/InterestAmountCalculator.kt:15`.
- Current code appears to calculate daily interest as `balance * appliedRate / 365`, scaled to 2 decimals with `RoundingMode.DOWN`: `src/main/kotlin/com/bankcore/interest/service/InterestAmountCalculator.kt:22`.
- Current code appears to expose account-scoped daily accrual as the current public API: `src/main/kotlin/com/bankcore/interest/controller/InterestController.kt:55`.
- Current code appears to run daily accrual for `ACTIVE` accounts in pages and continue after per-account failures: `src/main/kotlin/com/bankcore/interest/service/DailyInterestAccrualScheduler.kt:46`.

### Settlement path
- `EarlyTerminationSettlementService` is the current settlement path tied to pre-maturity account closure: `src/main/kotlin/com/bankcore/interest/service/EarlyTerminationSettlementService.kt:26`.
- If close date is not before maturity, early termination settlement is skipped: `src/main/kotlin/com/bankcore/interest/service/EarlyTerminationSettlementService.kt:44`.
- Current code appears to accrue the close business date before computing cumulative settlement amount: `src/main/kotlin/com/bankcore/interest/service/EarlyTerminationSettlementService.kt:58`.
- A single `InterestSettlement` is allowed per account and settlement type via DB uniqueness: `src/main/kotlin/com/bankcore/interest/entity/InterestSettlement.kt:21`.
- Current code appears to record a `Transaction` with type `INTEREST_SETTLEMENT` when settlement is first persisted: `src/main/kotlin/com/bankcore/interest/service/EarlyTerminationSettlementService.kt:94`.

## 4. Accrual Knowledge
This section captures what must be made explicit when designing accrual behavior.

### Accrual should define
- Which account statuses are eligible.
- Whether accrual is allowed on open date, close date, maturity date, and the day after closure.
- What balance snapshot is used.
- Which rate sources participate.
- Whether preferential conditions are request-driven, system-derived, or both.
- The rounding and scale rules for rates and money.
- How duplicate accrual for the same account/business date is prevented.

### Current code suggests, but does not settle, these answers
- Eligibility currently depends on account lifecycle and rejects `CLOSED`: `src/main/kotlin/com/bankcore/interest/service/DailyInterestAccrualService.kt:95`.
- Current code appears to use the account's current balance as `balanceSnapshot`: `src/main/kotlin/com/bankcore/interest/service/DailyInterestAccrualService.kt:80`.
- Preferential conditions are currently supplied as `conditionCodes` in the request: `src/main/kotlin/com/bankcore/interest/dto/DailyInterestAccrualRequest.kt:10`.
- Condition codes are normalized by trimming and must be unique uppercase underscore tokens: `src/main/kotlin/com/bankcore/interest/dto/DailyInterestAccrualRequest.kt:19`.

### Design questions to confirm with the user
- Is accrual based on current balance, start-of-day balance, end-of-day balance, or product-defined cutoff balance?
- Should preferential conditions be user-provided through the API, derived from internal events, or both?
- Is the 365 day-count convention fixed for all products?
- Is `RoundingMode.DOWN` the contractual rule or only a temporary implementation choice?
- Is accrual only a log, or does it represent a payable amount with downstream accounting meaning?

## 5. Settlement Knowledge
This section captures what must be made explicit when designing settlement behavior.

### Settlement should define
- What event triggers settlement.
- Whether settlement is distinct for maturity and early termination.
- Which accrual logs are included in the settlement amount.
- Whether settlement mutates account balance, creates a transaction only, or does both.
- What prevents repeated settlement on the same business event.
- How settlement interacts with account state transition order.

### Current code suggests, but does not settle, these answers
- Early termination settlement exists as a distinct path and is skipped if the account is not closing before maturity: `src/main/kotlin/com/bankcore/interest/service/EarlyTerminationSettlementService.kt:44`.
- Current settlement sums accrued interest from account open date through close business date: `src/main/kotlin/com/bankcore/interest/service/EarlyTerminationSettlementService.kt:66`.
- Current settlement uniqueness is keyed by `(account, settlementType)`: `src/main/kotlin/com/bankcore/interest/entity/InterestSettlement.kt:23`.
- Current settlement writes an `INTEREST_SETTLEMENT` transaction, but the service code shown does not itself update account balance: `src/main/kotlin/com/bankcore/interest/service/EarlyTerminationSettlementService.kt:94`.

### Design questions to confirm with the user
- What is the exact difference between maturity settlement and early termination settlement?
- Does maturity settlement need its own `InterestSettlementType`?
- Should settlement include the close-day accrual, and is that rule identical for maturity and early termination?
- Should settlement change account balance, or should it only create an auditable record for a separate posting step?
- If settlement is retried, should the API return the original result, a distinct already-processed marker, or an error?

## 6. Domain Invariants
These invariants should hold unless the user intentionally changes the design.

- The same account and business date must not be accrued twice.
- The same settlement event must not be finalized twice.
- Account lifecycle and interest lifecycle must remain consistent.
- Rate source, balance snapshot, and rounding rules must be reproducible from records.
- Financial meaning should be driven by business date, not by request timestamp alone.
- Interest behavior must remain auditable after retries, scheduler reruns, and concurrent requests.

## 7. Boundary Map

### `account`
Owns:
- account state (`ACTIVE`, `CLOSED`, etc.)
- open/close timestamps
- maturity date
- stored account balance

`interest` may depend on these facts but should not redefine account lifecycle semantics.

### `rate`
Owns:
- base rate
- spread rate
- preferential rate definitions
- date/product/condition scoping of rates

`interest` should consume rate facts, not duplicate rate catalog rules.

### `transaction`
Owns:
- durable posting/audit records for monetary events
- transaction numbering and transaction typing

`interest` may create transactions as a result of settlement, but transaction history should not become the source of accrual rules.

### `transfer`
Owns:
- funds movement semantics between accounts
- transfer-specific concurrency and lock rules

Interest payout may resemble money movement, but transfer should not become the place where interest logic lives.

### `common`
Only technical concerns already shared across domains belong here. Interest-specific business rules should stay in `interest`.

## 8. API and Contract Notes
This section is intentionally short. It exists to protect domain semantics when they are projected as HTTP APIs.

- `businessDate` must be explicit in meaning; callers should know whether it is request date, accounting date, or settlement base date.
- Success responses should differentiate newly processed results from already-processed/idempotent replays when relevant.
- Error responses should explain why the operation is disallowed: missing rate, invalid lifecycle state, duplicate processing, or contract mismatch.
- Swagger/OpenAPI updates are part of API completion, not optional polish.
- If a settlement API is exposed, its idempotency contract must be explicit.

## 9. Current Implementation Touchpoints
These files are the main places where current interest behavior is visible.

- Controller: `src/main/kotlin/com/bankcore/interest/controller/InterestController.kt:31`
- Accrual service: `src/main/kotlin/com/bankcore/interest/service/DailyInterestAccrualService.kt:22`
- Scheduler: `src/main/kotlin/com/bankcore/interest/service/DailyInterestAccrualScheduler.kt:17`
- Settlement service: `src/main/kotlin/com/bankcore/interest/service/EarlyTerminationSettlementService.kt:26`
- Amount calculator: `src/main/kotlin/com/bankcore/interest/service/InterestAmountCalculator.kt:8`
- Accrual request DTO: `src/main/kotlin/com/bankcore/interest/dto/DailyInterestAccrualRequest.kt:10`
- Accrual response DTO: `src/main/kotlin/com/bankcore/interest/dto/DailyInterestAccrualResponse.kt:8`
- Accrual log entity: `src/main/kotlin/com/bankcore/interest/entity/InterestLog.kt:25`
- Settlement entity: `src/main/kotlin/com/bankcore/interest/entity/InterestSettlement.kt:27`

## 10. Open Design Questions
These are the main unresolved topics that should be answered by user design, not guessed by implementation.

1. What is the canonical balance snapshot rule for daily accrual?
2. What is the canonical day-count and rounding policy for each product family?
3. How is maturity settlement defined, and how does it differ from early termination settlement?
4. Should settlement mutate account balance immediately, or only record settlement/transaction facts?
5. Are preferential conditions externally supplied, internally derived, or mixed?
6. Which parts of interest behavior are user-facing APIs versus internal batch/close flows?
7. What should be considered the stable contract for retries and already-processed responses?

## 11. How to Use This Draft

1. Treat current code as evidence, not authority.
2. Promote only user-confirmed rules into canonical sections.
3. Keep unresolved semantics visible in `Open Design Questions` until the user settles them.
4. Avoid turning this document into a generic checklist; preserve domain knowledge first.

## 12. Review Notes for the User
Likely places where your feedback will matter most:
- accrual balance snapshot rule
- maturity settlement semantics
- settlement posting/balance mutation rule
- rate composition and rounding as contract versus implementation detail
- whether public APIs should expose condition-code driven accrual at all
