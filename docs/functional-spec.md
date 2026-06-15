# Easy Language Learning — Functional Specification

This document captures the currently implemented behavior of the application.

## 1. Product Scope

The application supports vocabulary learning through two modes:

1. Flashcards
2. Match

It is account-aware: signed-in users get persistent score tracking and learning progress visibility. Anonymous users can play and browse the dictionary without persistence.

The application is local-first in deployment model and PostgreSQL-backed in persistence.

CSV migration work is complete; normal operation is database-only.

## 2. Core Functional Requirements

### 2.1 Account management

1. Persistent accounts: each user has an immutable UUID (`userId`) and a display name.
2. Sign-in by display name: an existing account is retrieved; a new account is created if the name is not yet registered.
3. Multiple accounts can exist; users switch accounts from the account panel in the main menu.
4. Sign-out clears the active user from the session; the user reverts to anonymous.
5. Signed-in vs anonymous state is shown in the main menu header.
6. The account panel is loaded inline without a full page reload (HTMX).

### 2.2 Session and language selection

1. User selects language and learning mode on the home page.
2. Selected language is stored in HTTP session and propagated to gameplay.
3. Signed-in user identity is carried through the session across page reloads.

### 2.3 Flashcards mode

1. A random eligible word is shown.
2. User can flip card to see translation and example.
3. Next card loads a new eligible word for the selected language.
4. No per-session score is tracked in this mode.

### 2.4 Match mode

1. Board is generated from eligible words in selected language.
2. Correct/incorrect attempts are tracked for the session.
3. When board is completed, next board is generated using the same selected language.
4. Session ends after configured success target.

### 2.5 Dictionary management

1. Public dictionary page at /dictionary.
2. Language selector for available valid dictionary bundles.
3. Search, pagination, and sorting by FROM, TO, or PROGRESS.
4. PROGRESS column and sort option are visible only to signed-in users.
5. Sort behavior is available from table headers.
6. Toggle global word enablement.
7. Toggle per-mode enablement.
8. Toggle changes persist to PostgreSQL and are reflected in gameplay.
9. Dictionary add-row rendering stays aligned whether progress is visible or hidden (new-row colspan respects signed-in state).

### 2.6 Data health and reload

1. Health page exposes word and score health states.
2. Admin endpoint supports runtime reload of data.
3. Gameplay availability depends on word data health.
4. On startup, score `pairId` values are validated against loaded dictionary IDs; orphaned values are logged as WARN and do not fail startup.

### 2.7 Score tracking

1. Match attempts are recorded as success/failure for signed-in users only.
2. Anonymous users play without score persistence.
3. Score key is `(userId, pairId, mode)`.
4. Per-user history keeps the last 12 attempts per pair and mode (rolling window).
5. Progress is expressed as a success percentage over those 12 attempts.

### 2.8 Persistence boundary contracts (implemented)

1. `ScoreReadRepository` provides read access for progress and user score lookup (`getHistoriesForUser`, `knownUsers`).
2. `ScoreWriteRepository` provides write access for match-attempt persistence (`appendAttempt`, `flush`).
3. `DictionaryRepository` provides dictionary read/write access (`findLanguage`, `availableLanguages`, `updateGlobalEnabled`, `updateWordContent`, `insertWord`, `upsertModeEligibility`).
4. Score read/write persistence is provided by PostgreSQL-backed repository implementations.
5. `DataHealthService` uses `DictionaryRepository` plus DB score connectivity checks via `JdbcTemplate`.
6. Score services depend on interfaces, not concrete adapters:
  1. `ScoreProgressService -> ScoreReadRepository`
  2. `MatchGameApplicationService -> ScoreWriteRepository`
7. Service dependencies remain interface-driven (`ScoreReadRepository`, `ScoreWriteRepository`, `DictionaryRepository`) with DB implementations active by default.

## 3. Data Requirements

### 3.1 Dictionary schema (database)

Dictionary content is stored in PostgreSQL tables:

1. `dictionary_pair`
2. `mode_eligibility` (added by Flyway `V2__mode_eligibility.sql`)

Rules:

1. `pair_id` uniquely identifies dictionary word pairs.
2. Per-mode overrides are keyed by `(pair_id, mode)`.
3. `mode_eligibility.pair_id` references `dictionary_pair.pair_id`.
4. Missing mode override means mode-enabled by default.

### 3.2 Eligibility rule

A word is eligible in mode M when:

1. GLOBAL_ENABLED = true
2. And per-mode value for M is true, or missing.

### 3.3 User and score persistence

User and score data are persisted in PostgreSQL and exposed through repository interfaces.

Rules:

1. User identity is immutable (`userId`).
2. Score identity key is `(userId, pairId, mode)`.
3. Score history is capped to the last 12 attempts.

## 4. Non-Functional Requirements

1. Runtime: Java 26.
2. Framework: Spring Boot MVC + Thymeleaf + HTMX + Alpine.js.
3. Persistence: PostgreSQL with Flyway-managed schema.
4. Safety: DB constraints and transactional repository operations.
5. Concurrency: per-language write synchronization for dictionary edits.
6. Validation: deterministic, user-visible parsing errors.
7. Static analysis: Error Prone + NullAway in JSpecify mode.

## 5. Security Requirements

1. Gameplay, dictionary, and account endpoints (`/`, `/flashcards`, `/match`, `/dictionary`, `/account/**`) are public.
2. Admin endpoints (`/admin/**`) require HTTP Basic authentication.
3. Credentials are configured in application properties.

## 6. Operational Constraints

1. Session store is in-memory.
2. PostgreSQL availability is required for normal application operation.
3. Flyway migrations must be successfully applied before serving traffic.
