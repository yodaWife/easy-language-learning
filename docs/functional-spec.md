# Easy Language Learning — Functional Specification

This document captures the currently implemented behavior of the application.

## 1. Product Scope

The application supports vocabulary learning through two modes:

1. Flashcards
2. Match

It is account-aware: signed-in users get persistent score tracking and learning progress visibility. Anonymous users can play and browse the dictionary without persistence.

The application is local-first and file-based. A relational database migration is planned for the next phase (see [docs/architecture/db-migration-blueprint.md](architecture/db-migration-blueprint.md)).

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
8. Toggle changes persist to CSV files and are reflected in gameplay.

### 2.6 Data health and reload

1. Health page exposes word and score health states.
2. Admin endpoint supports runtime reload of data.
3. Gameplay availability depends on word data health.

### 2.7 Score tracking

1. Match attempts are recorded as success/failure for signed-in users only.
2. Anonymous users play without score persistence.
3. Score key is `(userId, pairId, mode)`.
4. Per-user history keeps the last 12 attempts per pair and mode (rolling window).
5. Progress is expressed as a success percentage over those 12 attempts.

## 3. Data Requirements

### 3.1 Dictionary structure

Dictionary root path contains language folders:

```text
data/dictionaries/{languageCode}/
  words.csv
  mode-eligibility.csv
```

### 3.2 words.csv schema

```text
WORD_ID;FROM;TO;EXAMPLE;GLOBAL_ENABLED
```

Rules:

1. Exactly 5 columns.
2. WORD_ID is a deterministic type-3 UUID derived from `languageCode:from:to`.
3. WORD_ID unique per language.
4. FROM and TO non-blank.
5. GLOBAL_ENABLED is true or false.

### 3.3 mode-eligibility.csv schema

```text
WORD_ID;MODE;ENABLED
```

Rules:

1. Exactly 3 columns.
2. WORD_ID must exist in words.csv.
3. MODE non-blank.
4. ENABLED is true or false.
5. (WORD_ID, MODE) unique.
6. Missing row means default enabled for that mode.

### 3.4 Eligibility rule

A word is eligible in mode M when:

1. GLOBAL_ENABLED = true
2. And per-mode value for M is true, or missing.

### 3.5 users.csv schema

```text
USER_ID;DISPLAY_NAME;CREATED_AT
```

Rules:

1. Exactly 3 columns.
2. USER_ID is a randomly-generated UUID, immutable after creation.
3. DISPLAY_NAME is unique (case-insensitive on lookup).
4. CREATED_AT is an ISO-8601 UTC timestamp.

### 3.6 scores.csv schema

```text
userId;pairId;mode;history
```

Rules:

1. Exactly 4 columns.
2. `userId` must reference a known user.
3. `pairId` must match a WORD_ID in the dictionary.
4. `history` is a string of `S` (success) and `F` (failure) characters, most recent last, max 12.

## 4. Non-Functional Requirements

1. Runtime: Java 26.
2. Framework: Spring Boot MVC + Thymeleaf + HTMX + Alpine.js.
3. Persistence: CSV files only (database migration planned for next phase).
4. Safety: atomic writes for mutable CSV updates.
5. Concurrency: per-language write synchronization for dictionary edits.
6. Validation: deterministic, user-visible parsing errors.
7. Static analysis: Error Prone + NullAway in JSpecify mode.

## 5. Security Requirements

1. Gameplay, dictionary, and account endpoints (`/`, `/flashcards`, `/match`, `/dictionary`, `/account/**`) are public.
2. Admin endpoints (`/admin/**`) require HTTP Basic authentication.
3. Credentials are configured in application properties.

## 6. Operational Constraints

1. Session store is in-memory.
2. Application process must have write permission to:
   1. `data/dictionaries` (dictionary toggles)
   2. `data/users` (account creation and updates)
   3. Score write path (score persistence)
3. Dictionary availability is based on valid bundles discovered under configured root.
