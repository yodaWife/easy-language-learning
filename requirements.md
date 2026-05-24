# Easy Language Learning - Current Requirements Snapshot

This document captures the currently implemented behavior of the application.

## 1. Product Scope

The application supports vocabulary learning through two modes:

1. Flashcards
2. Match

It is local-first and file-based, with no database dependency.

## 2. Core Functional Requirements

### 2.1 Session start

1. User can start a session with optional nickname.
2. User selects language and learning mode on the home page.
3. Selected language is stored in HTTP session and propagated to gameplay.

### 2.2 Flashcards mode

1. A random eligible word is shown.
2. User can flip card to see translation and example.
3. Next card loads a new eligible word for the selected language.
4. No per-session score is tracked in this mode.

### 2.3 Match mode

1. Board is generated from eligible words in selected language.
2. Correct/incorrect attempts are tracked for the session.
3. When board is completed, next board is generated using the same selected language.
4. Session ends after configured success target.

### 2.4 Dictionary management

1. Public dictionary page at /dictionary.
2. Language selector for available valid dictionary bundles.
3. Search, pagination, and sorting by FROM/TO.
4. Sort behavior is available from table headers.
5. Toggle global word enablement.
6. Toggle per-mode enablement.
7. Toggle changes persist to CSV files and are reflected in gameplay.

### 2.5 Data health and reload

1. Health page exposes word and score health states.
2. Admin endpoint supports runtime reload of data.
3. Gameplay availability depends on word data health.

### 2.6 Score tracking

1. Match attempts are recorded as success/failure.
2. Optional nickname enables persistence in scores CSV.
3. Per-user history keeps last 10 entries per word pair.

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
2. WORD_ID unique per language.
3. FROM and TO non-blank.
4. GLOBAL_ENABLED is true or false.

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

## 4. Non-Functional Requirements

1. Runtime: Java 26.
2. Framework: Spring Boot MVC + Thymeleaf + HTMX + Alpine.js.
3. Persistence: CSV files only.
4. Safety: atomic writes for mutable CSV updates.
5. Concurrency: per-language write synchronization for dictionary edits.
6. Validation: deterministic, user-visible parsing errors.
7. Static analysis: Error Prone + NullAway in JSpecify mode.

## 5. Security Requirements

1. Gameplay and dictionary endpoints are public.
2. Admin endpoints (/admin/**) require HTTP Basic authentication.
3. Credentials are configured in application properties.

## 6. Operational Constraints

1. Session store is in-memory.
2. Application process must have write permission to:
   1. data/dictionaries (dictionary toggles)
   2. score write path (score persistence)
3. Dictionary availability is based on valid bundles discovered under configured root.

## 7. Backward Compatibility Notes

1. Legacy single-file word source property (app.words.source) is retained.
2. Gameplay uses multi-language dictionary bundles as the authoritative source.
3. Legacy parser health errors may still appear in diagnostics but do not override valid multi-language gameplay data.
