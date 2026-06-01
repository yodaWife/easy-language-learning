# Implementation Plan: Phase 1 Persistence Interface Layer

## Summary
- **Total tasks:** 11
- **Parallel phases:** 4
- **Maximum parallelism:** 3 tasks per phase

This plan decomposes Phase 1 gaps from the database migration blueprint into isolated, conflict-free work streams. Each task specifies exact files to create/modify/delete with minimal dependencies.

---

## Task File Scope

### TASK-001: Create ScoreAttemptRepository Interface
**Title:** Extract score attempt persistence contract

- **Create:**
  - `src/main/java/com/yodawife/easyll/repository/ScoreAttemptRepository.java`
- **Modify:** (none)
- **Delete:** (none)
- **Acceptance Criteria:**
  - Interface defines: `void append(String userId, String pairId, String mode, String result)` and `List<ScoreAttempt> findByUserAndPairs(String userId, List<String> pairIds)`
  - Includes JavaDoc explaining the contract
  - Follows existing repository pattern (e.g., `AccountRepository`)

---

### TASK-002: Create ScoreProgressRepository Interface
**Title:** Extract score progress persistence contract

- **Create:**
  - `src/main/java/com/yodawife/easyll/repository/ScoreProgressRepository.java`
- **Modify:** (none)
- **Delete:** (none)
- **Acceptance Criteria:**
  - Interface defines: `Optional<ScoreProgress> find(String userId, String pairId, String mode)`, `Map<String, ScoreProgress> findByUserAndPairs(String userId, List<String> pairIds, String mode)`, `void upsert(ScoreProgress progress)`
  - Includes JavaDoc explaining query semantics
  - Follows existing repository pattern

---

### TASK-003: Create DictionaryRepository Interface
**Title:** Abstract dictionary discovery and retrieval contract

- **Create:**
  - `src/main/java/com/yodawife/easyll/repository/DictionaryRepository.java`
- **Modify:** (none)
- **Delete:** (none)
- **Acceptance Criteria:**
  - Interface defines: `Optional<LanguageBundle> findLanguage(String languageCode)`, `List<String> availableLanguages()`
  - Includes JavaDoc
  - Aligns with existing discovery service behavior

---

### TASK-004: Create PairIdReferentialIntegrityValidator Service
**Title:** Validate pair_id references at startup

- **Create:**
  - `src/main/java/com/yodawife/easyll/service/PairIdReferentialIntegrityValidator.java`
- **Modify:** (none)
- **Delete:** (none)
- **Acceptance Criteria:**
  - Service uses `@EventListener(ApplicationReadyEvent.class)` or `@PostConstruct` to validate on startup
  - Compares all pairIds in score entries against dictionary words
  - Logs warnings for orphaned score entries with count
  - Does not throw exceptions; gracefully handles missing data
  - Exposes `getOrphanedEntries()` method for optional health endpoint integration

---

### TASK-005: Refactor ScoreRepository to Implement New Interfaces
**Title:** Adapt concrete ScoreRepository to implement ScoreAttemptRepository and ScoreProgressRepository

- **Modify:**
  - `src/main/java/com/yodawife/easyll/repository/ScoreRepository.java`
- **Create:** (none)
- **Delete:** (none)
- **Acceptance Criteria:**
  - ScoreRepository now implements both `ScoreAttemptRepository` and `ScoreProgressRepository`
  - Existing methods remain unchanged (backward compatible)
  - Existing tests pass without modification
  - New interface methods map correctly to existing internal logic:
    - `ScoreAttemptRepository.append(...)` → `appendAttempt(...)`
    - `ScoreProgressRepository.upsert(...)` → internally updates histories map
  - No breaking changes to public API

---

### TASK-006: Update ScoreProgressService to Use ScoreProgressRepository Interface
**Title:** Inject interface instead of concrete ScoreRepository

- **Modify:**
  - `src/main/java/com/yodawife/easyll/service/ScoreProgressService.java`
- **Create:** (none)
- **Delete:** (none)
- **Acceptance Criteria:**
  - Constructor now accepts `ScoreProgressRepository` instead of `ScoreRepository`
  - All calls use interface methods
  - Behavior unchanged; existing tests pass
  - Service is now decoupled from concrete CSV implementation

---

### TASK-007: Update MatchGameApplicationService to Use ScoreAttemptRepository Interface
**Title:** Inject interface instead of concrete ScoreRepository

- **Modify:**
  - `src/main/java/com/yodawife/easyll/service/MatchGameApplicationService.java`
- **Create:** (none)
- **Delete:** (none)
- **Acceptance Criteria:**
  - Constructor now accepts `ScoreAttemptRepository` instead of `ScoreRepository`
  - All calls use interface methods
  - `finaliseSession()` calls `append()` and `flush()` via interface
  - Behavior unchanged; existing tests pass

---

### TASK-008: Update DictionaryDiscoveryService to Implement DictionaryRepository Interface
**Title:** Adapt DictionaryDiscoveryService to implement repository contract

- **Modify:**
  - `src/main/java/com/yodawife/easyll/service/DictionaryDiscoveryService.java`
- **Create:** (none)
- **Delete:** (none)
- **Acceptance Criteria:**
  - DictionaryDiscoveryService now implements `DictionaryRepository`
  - New methods: `Optional<LanguageBundle> findLanguage(String languageCode)`, `List<String> availableLanguages()`
  - These methods leverage existing `discoverLanguages()` logic and `DataHealthService` snapshot
  - Existing methods remain unchanged
  - Service is now a dual-purpose discovery + repository service (acceptable for phase 1)

---

### TASK-009: Create Adapter Classes for Score Repositories (If Not Using ScoreRepository Directly)
**Title:** Optional CSV adapter implementations (may defer to phase 2)

- **Create:** (none, unless deferring ScoreRepository interface implementation)
- **Note:** In phase 1, ScoreRepository itself implements both interfaces. Dedicated adapter classes are deferred to phase 2 when a DB adapter is needed.

---

### TASK-010: Update Tests for ScoreRepository
**Title:** Ensure ScoreRepository tests pass with interface methods

- **Modify:**
  - `src/test/java/com/yodawife/easyll/repository/ScoreRepositoryTest.java`
  - `src/test/java/com/yodawife/easyll/service/DataHealthServiceTest.java`
  - `src/test/java/com/yodawife/easyll/controller/DataReloadIntegrationTest.java` (if needed)
- **Create:**
  - `src/test/java/com/yodawife/easyll/repository/ScoreAttemptRepositoryTest.java` (contract tests for interface)
  - `src/test/java/com/yodawife/easyll/repository/ScoreProgressRepositoryTest.java` (contract tests for interface)
  - `src/test/java/com/yodawife/easyll/repository/DictionaryRepositoryTest.java` (contract tests for interface)
  - `src/test/java/com/yodawife/easyll/service/PairIdReferentialIntegrityValidatorTest.java`
- **Delete:** (none)
- **Acceptance Criteria:**
  - All existing tests continue to pass
  - New contract tests verify interface behavior
  - Validator tests confirm warning logs and orphan detection

---

### TASK-011: Verify Progress Column Gating in Dictionary Template
**Title:** Confirm progressEnabled flag correctly hides/shows progress column

- **Verify:**
  - `src/main/resources/templates/dictionary.html` (already uses `progressEnabled`)
  - `src/main/resources/templates/fragments/dictionary-row.html` (uses `progressEnabled` to conditionally render column)
  - `src/main/java/com/yodawife/easyll/controller/DictionaryController.java` (passes `progressEnabled = signedIn && userId != null`)
- **Modify:** (none — already correct)
- **Acceptance Criteria:**
  - Progress column is visible only when `progressEnabled == true`
  - Progress column is hidden when `progressEnabled == false` or when user is anonymous
  - No colspan issues in table layout when column is hidden
  - UI tests confirm behavior

---

## Conflict Matrix

| Task | Conflicts with | Shared files |
|------|---------------|--------------|
| TASK-001 | (none) | — |
| TASK-002 | (none) | — |
| TASK-003 | (none) | — |
| TASK-004 | (none) | — |
| TASK-005 | TASK-006, TASK-007 | `ScoreRepository.java` (read-only by 006, 007) |
| TASK-006 | TASK-005 | `ScoreRepository.java` (implementation) |
| TASK-007 | TASK-005 | `ScoreRepository.java` (implementation) |
| TASK-008 | (none) | — |
| TASK-010 | TASK-005, TASK-006, TASK-007, TASK-008 | Test classes |
| TASK-011 | (none) | — |

---

## Execution Plan

### Phase 1 (Parallel)
Create all repository interfaces — no dependencies on other tasks.

| Stream | Task ID | Title |
|--------|---------|-------|
| A | TASK-001 | Create ScoreAttemptRepository Interface |
| B | TASK-002 | Create ScoreProgressRepository Interface |
| C | TASK-003 | Create DictionaryRepository Interface |
| D | TASK-004 | Create PairIdReferentialIntegrityValidator Service |
| E | TASK-011 | Verify Progress Column Gating |

---

### Phase 2 (Parallel)
Refactor concrete classes to implement new interfaces; no file conflicts.

| Stream | Task ID | Title |
|--------|---------|-------|
| A | TASK-005 | Refactor ScoreRepository |
| B | TASK-006 | Update ScoreProgressService |
| C | TASK-007 | Update MatchGameApplicationService |
| D | TASK-008 | Update DictionaryDiscoveryService |

---

### Phase 3 (Sequential)
Update and create tests — depends on Phase 2 implementations.

| Stream | Task ID | Title |
|--------|---------|-------|
| A | TASK-010 | Update and Create Tests |

---

## Developer Assignment Guide

### Phase 1 Assignment

**Developer A → TASK-001 (ScoreAttemptRepository Interface)**
- Files: `src/main/java/com/yodawife/easyll/repository/ScoreAttemptRepository.java` (create)
- Dependencies: None
- Guidance: Model after `AccountRepository.java`; include methods for appending and querying attempts

**Developer B → TASK-002 (ScoreProgressRepository Interface)**
- Files: `src/main/java/com/yodawife/easyll/repository/ScoreProgressRepository.java` (create)
- Dependencies: None
- Guidance: Define query and upsert methods for progress tracking; ensure Optional return types match existing patterns

**Developer C → TASK-003 (DictionaryRepository Interface)**
- Files: `src/main/java/com/yodawife/easyll/repository/DictionaryRepository.java` (create)
- Dependencies: None
- Guidance: Align with existing `LanguageBundle` domain model; keep methods simple and focused

**Developer D → TASK-004 (PairIdReferentialIntegrityValidator)**
- Files: `src/main/java/com/yodawife/easyll/service/PairIdReferentialIntegrityValidator.java` (create)
- Dependencies: None
- Guidance: Use Spring's `ApplicationReadyEvent` or `@PostConstruct`; log warnings but do not fail startup; leverage `DataHealthService.snapshot()` for both scores and dictionaries

**Developer E → TASK-011 (Template Verification)**
- Files: `src/main/resources/templates/dictionary.html`, `src/main/resources/templates/fragments/dictionary-row.html`, `src/main/java/com/yodawife/easyll/controller/DictionaryController.java` (verify only)
- Dependencies: None
- Guidance: Inspect existing implementation; confirm colspan math and no regressions

---

### Phase 2 Assignment

**Developer A → TASK-005 (Refactor ScoreRepository)**
- Files: `src/main/java/com/yodawife/easyll/repository/ScoreRepository.java` (modify)
- Dependencies: Phase 1 (TASK-001, TASK-002 must be created first)
- Guidance: Add interface declarations; map internal methods to interface contracts; preserve existing public API and internal state management

**Developer B → TASK-006 (ScoreProgressService)**
- Files: `src/main/java/com/yodawife/easyll/service/ScoreProgressService.java` (modify)
- Dependencies: Phase 1 (TASK-002 must be created first); Phase 2 (TASK-005 must complete first)
- Guidance: Change constructor parameter from `ScoreRepository` to `ScoreProgressRepository`; update all method calls to use interface

**Developer C → TASK-007 (MatchGameApplicationService)**
- Files: `src/main/java/com/yodawife/easyll/service/MatchGameApplicationService.java` (modify)
- Dependencies: Phase 1 (TASK-001 must be created first); Phase 2 (TASK-005 must complete first)
- Guidance: Change constructor parameter from `ScoreRepository` to `ScoreAttemptRepository`; map calls to interface methods; ensure `finaliseSession()` uses `append()` method

**Developer D → TASK-008 (DictionaryDiscoveryService)**
- Files: `src/main/java/com/yodawife/easyll/service/DictionaryDiscoveryService.java` (modify)
- Dependencies: Phase 1 (TASK-003 must be created first)
- Guidance: Implement `DictionaryRepository`; add new methods leveraging existing logic and `DataHealthService` snapshots; ensure backward compatibility with existing discovery methods

---

### Phase 3 Assignment

**Developer A → TASK-010 (Tests)**
- Files:
  - Modify: `src/test/java/com/yodawife/easyll/repository/ScoreRepositoryTest.java`
  - Create: `src/test/java/com/yodawife/easyll/repository/ScoreAttemptRepositoryTest.java`
  - Create: `src/test/java/com/yodawife/easyll/repository/ScoreProgressRepositoryTest.java`
  - Create: `src/test/java/com/yodawife/easyll/repository/DictionaryRepositoryTest.java`
  - Create: `src/test/java/com/yodawife/easyll/service/PairIdReferentialIntegrityValidatorTest.java`
  - Update: `src/test/java/com/yodawife/easyll/service/DataHealthServiceTest.java` (if needed)
  - Update: `src/test/java/com/yodawife/easyll/controller/DataReloadIntegrationTest.java` (if needed)
- Dependencies: All Phase 2 tasks (TASK-005, TASK-006, TASK-007, TASK-008)
- Guidance:
  - Write contract-based tests for each interface (can run against any implementation)
  - Validator tests should verify warning logs and orphan detection
  - Ensure all existing tests pass; no behavior changes
  - Consider parameterized tests if testing multiple implementations

---

## Notes

1. **Interface Segregation:** Each repository interface is focused on a specific concern (attempts vs. progress), allowing independent scaling and testing.

2. **Backward Compatibility:** The concrete `ScoreRepository` implements both new interfaces, so no migration of Spring bean injection is needed. The service layer gradually adopts the interfaces.

3. **Referential Integrity:** The validator service is a new, optional enhancement. It does not block startup and logs diagnostics for operational awareness.

4. **Template Gating:** The dictionary template already correctly uses the `progressEnabled` flag; TASK-011 is a verification step to ensure no regressions.

5. **Phase 2 Release:** After Phase 1 + Phase 2, the codebase will be ready for Phase 2 database implementation, where the service layer can swap in a database-backed `ScoreRepository` implementation without changing any consumers.

6. **Testing Strategy:** Contract-based tests ensure that any future implementation (CSV, DB) meets the same interface contract.

---

## Success Criteria (All Phases)

- All new interfaces are created with clear, documented contracts
- All concrete classes implement the new interfaces correctly
- All service and controller dependencies updated to use interfaces (where applicable)
- All existing tests pass without modification
- New tests achieve >80% coverage of new code
- No breaking changes to public APIs
- Code follows Java 21+ and project conventions
- Referential integrity validator runs at startup and logs diagnostics
- Dictionary template correctly gates progress column based on `signedIn` and `userId`
