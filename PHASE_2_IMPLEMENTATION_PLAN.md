# Phase 2 Implementation Plan: PostgreSQL Backend & Flyway Integration

## Executive Summary

**Total Tasks:** 14  
**Parallel Phases:** 4  
**Critical Path:** Phase 1 → Phase 2 → Phase 3 → Phase 4  
**Estimated Developer Load:** 4 streams (4 developers can work simultaneously within phases)

---

## Task File Scope

### TASK-1.1: Add Build Dependencies
**Phase:** 1 (Foundation)

- **Modify:**
  - `build.gradle.kts`

- **Create:** None

- **Delete:** None

**Description:**
Add PostgreSQL JDBC driver, Flyway Core + PostgreSQL dialect, Spring Boot Data JDBC, and Testcontainers to dependencies. All tasks in Phase 2+ depend on these being available.

**Acceptance Criteria:**
- PostgreSQL JDBC driver (org.postgresql:postgresql) is declared as runtimeOnly
- Flyway core and PostgreSQL dialect are added as implementation
- Spring Data JDBC is declared as implementation
- Testcontainers (JUnit 5 + PostgreSQL) are added as testImplementation
- Gradle compiles successfully with all new dependencies

---

### TASK-1.2: Create Flyway Migration Schema
**Phase:** 1 (Foundation)

- **Modify:** None

- **Create:**
  - `src/main/resources/db/migration/V1__init.sql`

- **Delete:** None

**Description:**
Define the PostgreSQL database schema including app_user, dictionary_pair, score_attempt, score_progress tables, indexes, and constraints exactly as specified in the requirements.

**Acceptance Criteria:**
- V1__init.sql exists in src/main/resources/db/migration/
- All four tables (app_user, dictionary_pair, score_attempt, score_progress) are created
- All constraints (PKs, UKs, FKs, CHECKs) are correctly defined
- The index on score_attempt (user_id, pair_id, mode, attempted_at_utc desc) is created
- Flyway will recognize the file and execute it on application startup with profile db

---

### TASK-1.3: Create Profile-Based Repository Configuration
**Phase:** 1 (Foundation)

- **Modify:** None

- **Create:**
  - `src/main/java/com/yodawife/easyll/config/RepositoryConfiguration.java`
  - `src/main/java/com/yodawife/easyll/config/MigrationProperties.java`
  - `src/main/resources/application-csv.properties` (optional; env defaults for csv profile)
  - `src/main/resources/application-db.properties` (optional; env defaults for db profile)

- **Delete:** None

**Description:**
Create a @Configuration class that conditionally registers CSV or DB repository beans based on active Spring profile. Create MigrationProperties configuration class for migration runner settings.

**Acceptance Criteria:**
- RepositoryConfiguration.java is annotated @Configuration (not @Service/@Repository)
- Has @ConditionalOnProfile("csv") methods that return CSV repository beans OR explicit @Profile("csv") on existing CSV classes
- Has @ConditionalOnProfile("db") methods that return DB repository beans (created in Phase 2)
- MigrationProperties.java is a @ConfigurationProperties class with properties:
  - `app.migration.enabled` (boolean, default false)
  - `app.migration.dry-run` (boolean, default true)
  - `app.migration.errors-output-path` (String, default "./data/migration-errors.csv")
  - All fields have getter/setter or record pattern
- Spring resolves exactly one implementation per repository interface based on profile
- Test profile defaults to csv (existing behavior)

---

### TASK-2.1: Implement PostgresAccountRepository
**Phase:** 2 (DB Repository Implementations)  
**Depends on:** Task 1.1, 1.3

- **Modify:**
  - `src/main/java/com/yodawife/easyll/repository/CsvAccountRepository.java` (add @Profile("csv") annotation if not using @Configuration)

- **Create:**
  - `src/main/java/com/yodawife/easyll/repository/db/PostgresAccountRepository.java`
  - `src/main/java/com/yodawife/easyll/repository/db/AccountRowMapper.java` (if needed for RowMapper<Account>)

- **Delete:** None

**Description:**
Implement AccountRepository using Spring Data JDBC. Use JdbcOperations to execute hand-written queries. Map app_user rows to Account domain records.

**Acceptance Criteria:**
- PostgresAccountRepository implements AccountRepository
- Annotated @Repository and @Profile("db")
- All four methods implemented:
  - `findById(String userId)` — SELECT from app_user where user_id = ?
  - `findByDisplayName(String displayName)` — SELECT from app_user where lower(display_name) = lower(?)
  - `findAll()` — SELECT from app_user ORDER BY display_name ASC
  - `save(Account account)` — INSERT … ON CONFLICT (user_id) DO UPDATE … or explicit UPDATE logic
- Account records are correctly mapped from ResultSet
- All fields (userId, displayName, createdAt) are hydrated from DB columns

---

### TASK-2.2: Implement PostgresScoreReadRepository
**Phase:** 2 (DB Repository Implementations)  
**Depends on:** Task 1.1, 1.3

- **Modify:** None

- **Create:**
  - `src/main/java/com/yodawife/easyll/repository/db/PostgresScoreReadRepository.java`

- **Delete:** None

**Description:**
Implement ScoreReadRepository using Spring Data JDBC to read score progress data. Return history as Map<pairId, List<String>> where List is the 12-attempt history from score_progress.history_last12 split by comma.

**Acceptance Criteria:**
- PostgresScoreReadRepository implements ScoreReadRepository
- Annotated @Repository and @Profile("db")
- `getHistoriesForUser(String userId)` returns Map<pairId, List<String>>:
  - Queries score_progress where user_id = userId
  - history_last12 field is split by "," into a List<String>
  - Returns empty Map if user has no scores
- `knownUsers()` returns Set<String>:
  - Queries DISTINCT user_id from score_attempt
  - Returns empty Set if no attempts exist
- No modification of score_attempt or score_progress tables

---

### TASK-2.3: Implement PostgresScoreWriteRepository
**Phase:** 2 (DB Repository Implementations)  
**Depends on:** Task 1.1, 1.3

- **Modify:** None

- **Create:**
  - `src/main/java/com/yodawife/easyll/repository/db/PostgresScoreWriteRepository.java`

- **Delete:** None

**Description:**
Implement ScoreWriteRepository using Spring Data JDBC. `appendAttempt` inserts into score_attempt and upserts score_progress with recalculated history. `flush()` is a no-op (DB is transactional).

**Acceptance Criteria:**
- PostgresScoreWriteRepository implements ScoreWriteRepository
- Annotated @Repository and @Profile("db")
- `appendAttempt(String userId, String pairId, String mode, String result)`:
  - INSERTs a new row into score_attempt with attempt_id (generated), user_id, pair_id, mode, result, attempted_at_utc (current timestamp)
  - Queries the last 12 score_attempt rows for (user_id, pair_id, mode) ordered by attempted_at_utc DESC
  - Recalculates success_count, total_count, success_percent, and history_last12 from the 12 attempts
  - UPSERTs (INSERT … ON CONFLICT) into score_progress with recalculated values and updated_at_utc (current timestamp)
  - Handles the case where this is the first attempt for a pair/mode (score_progress row does not exist)
- `flush()` implementation:
  - Does nothing (is a no-op; DB transactions are implicit)
  - May add a comment explaining why it's a no-op

---

### TASK-2.4: Implement PostgresDictionaryRepository
**Phase:** 2 (DB Repository Implementations)  
**Depends on:** Task 1.1, 1.3

- **Modify:** None

- **Create:**
  - `src/main/java/com/yodawife/easyll/repository/db/PostgresDictionaryRepository.java`
  - `src/main/java/com/yodawife/easyll/repository/db/DictionaryPairRowMapper.java` (if needed)

- **Delete:** None

**Description:**
Implement DictionaryRepository using Spring Data JDBC. Load words from dictionary_pair table. Mode eligibilities are left empty for Phase 2 (CSV-backed). Create and return LanguageBundle records.

**Acceptance Criteria:**
- PostgresDictionaryRepository implements DictionaryRepository
- Annotated @Repository and @Profile("db")
- `findLanguage(String languageCode)` returns Optional<LanguageBundle>:
  - Queries SELECT * from dictionary_pair where language_code = ? and global_enabled = true
  - Maps each row to a Word domain record (pair_id, from_word, to_word, example, languageCode, createdAt, updatedAt)
  - Returns LanguageBundle with:
    - languageCode (as passed)
    - metadata: set to null (no metadata loaded from DB in Phase 2)
    - words: List of Word records from query
    - modeEligibilities: empty List (CSV-backed in Phase 2)
    - validationErrors: empty List (DB data is assumed valid)
  - Returns empty Optional if no words found for that language
- `availableLanguages()` returns List<String>:
  - Queries SELECT DISTINCT language_code from dictionary_pair where global_enabled = true
  - Returns List sorted ascending
  - Returns empty List if no languages in DB

---

### TASK-3.1: Implement CSV-to-DB Migration Runner
**Phase:** 3 (Migration Infrastructure)  
**Depends on:** Task 1.1, 2.1, 2.2, 2.3, 2.4

- **Modify:** None

- **Create:**
  - `src/main/java/com/yodawife/easyll/migration/CsvToDbMigrationRunner.java`
  - `src/main/java/com/yodawife/easyll/migration/MigrationErrorRecorder.java`
  - `src/main/java/com/yodawife/easyll/migration/MigrationContext.java` (if needed; holds userId → UUID mapping, pairId resolution)

- **Delete:** None

**Description:**
Implement an ApplicationRunner that reads CSV files (users.csv, words.csv for all languages, scores.csv) and writes to database tables. Support dry-run mode. Log errors and write unresolved rows to migration-errors.csv.

**Acceptance Criteria:**
- CsvToDbMigrationRunner implements ApplicationRunner (Spring Boot hook)
- Annotated @Component and @ConditionalOnProperty(name="app.migration.enabled", havingValue="true")
- Constructor accepts:
  - DataHealthService (to access CSV word/score data)
  - CsvAccountRepository (to read users.csv)
  - JdbcTemplate or similar (to insert into DB)
  - MigrationProperties (config)
  - ObjectMapper (JSON logging, optional)
- `run(ApplicationArguments args)` method:
  - If app.migration.enabled=false, logs and returns early
  - Reads users.csv via CsvAccountRepository.findAll()
  - Reads all language dictionaries via DataHealthService.snapshot().multiLanguageData()
  - Reads score data via DataHealthService.snapshot().scoreData()
  - For each user, inserts into app_user (or skips if exists)
  - For each word pair, inserts into dictionary_pair (or skips if exists, checking pair_id)
  - For each score attempt, inserts into score_attempt, then upserts score_progress
  - If dry-run mode, logs INSERTs but does not execute
  - Tracks errors: pairId not found, userId not found, parse errors
  - Writes migration-errors.csv with columns: row_number, source_table, error_message
  - Logs summary: N users migrated, M words migrated, K attempts migrated, L errors logged
- MigrationErrorRecorder utility class:
  - Accepts errors from migration process
  - Writes to migration-errors.csv in CSV format
  - Flushes on close (try-with-resources)

---

### TASK-4.1: Create Abstract Repository Contract Test Base
**Phase:** 4 (Testing & Integration)  
**Depends on:** Task 1.1, 2.1–2.4

- **Modify:** None

- **Create:**
  - `src/test/java/com/yodawife/easyll/repository/AccountRepositoryContractTest.java` (abstract base class)
  - `src/test/java/com/yodawife/easyll/repository/ScoreReadRepositoryContractTest.java` (abstract base)
  - `src/test/java/com/yodawife/easyll/repository/ScoreWriteRepositoryContractTest.java` (abstract base)
  - `src/test/java/com/yodawife/easyll/repository/DictionaryRepositoryContractTest.java` (abstract base)

- **Delete:** None

**Description:**
Create abstract base test classes that define shared test assertions for repository implementations. Both CSV and DB implementations must extend and pass these tests.

**Acceptance Criteria:**
- Each test base is an abstract class (not instantiable on its own)
- AccountRepositoryContractTest defines @Test methods:
  - testFindByIdReturnsAccountWhenExists
  - testFindByIdReturnsEmptyWhenNotFound
  - testFindByDisplayNameIsCaseInsensitive
  - testFindAllReturnsSortedByDisplayName
  - testSaveCreatesNewAccountAndReturnsIt
  - testSaveUpdatesExistingAccountByUserId
- ScoreReadRepositoryContractTest defines @Test methods:
  - testGetHistoriesForUserReturnsMapOfHistories
  - testGetHistoriesForUserReturnsEmptyMapWhenUserNotFound
  - testKnownUsersReturnsSetOfUserIds
  - testKnownUsersReturnsEmptySetWhenNoAttempts
- ScoreWriteRepositoryContractTest defines @Test methods:
  - testAppendAttemptInsertsScoreAttempt
  - testAppendAttemptUpsertsScoreProgress
  - testAppendAttemptRecalculatesHistoryFromLast12
  - testFlushDoesNotThrow
- DictionaryRepositoryContractTest defines @Test methods:
  - testFindLanguageReturnsLanguageBundleWhenExists
  - testFindLanguageReturnsEmptyWhenNotExists
  - testFindLanguageBuildsWordListFromDatabase
  - testAvailableLanguagesReturnsSortedList
  - testAvailableLanguagesReturnsEmptyWhenNoLanguages
- Each abstract test has abstract protected method(s) that concrete subclasses must implement to provide the repository instance under test
- Tests use @DisplayName for clarity

---

### TASK-4.2: Create PostgreSQL Integration Tests
**Phase:** 4 (Testing & Integration)  
**Depends on:** Task 1.1, 1.2, 2.1–2.4

- **Modify:** None

- **Create:**
  - `src/test/java/com/yodawife/easyll/repository/db/PostgresAccountRepositoryIntegrationTest.java`
  - `src/test/java/com/yodawife/easyll/repository/db/PostgresScoreReadRepositoryIntegrationTest.java`
  - `src/test/java/com/yodawife/easyll/repository/db/PostgresScoreWriteRepositoryIntegrationTest.java`
  - `src/test/java/com/yodawife/easyll/repository/db/PostgresDictionaryRepositoryIntegrationTest.java`

- **Delete:** None

**Description:**
Create integration tests for each DB repository using Testcontainers (PostgreSQL). Each test class extends the corresponding contract test base, so they inherit all shared assertions.

**Acceptance Criteria:**
- Each test class uses @Testcontainers (JUnit 5 extension from org.testcontainers:junit-jupiter)
- Each test class has a static @Container PostgreSQLContainer field (or uses DynamicPropertySource)
- Tests are annotated @Tag("db") for filtering (e.g., mvn test -Dgroups=db)
- PostgresAccountRepositoryIntegrationTest:
  - Extends AccountRepositoryContractTest
  - Implements abstract methods to instantiate PostgresAccountRepository with testcontainers connection
  - Runs Flyway V1 migration before tests
  - All inherited @Test methods from base run against live PostgreSQL
  - Includes additional DB-specific tests (e.g., transaction rollback, concurrent writes)
- PostgresScoreReadRepositoryIntegrationTest:
  - Extends ScoreReadRepositoryContractTest
  - Sets up test fixtures (users, dictionary pairs, score attempts) before each test
  - Runs all inherited contract tests
- PostgresScoreWriteRepositoryIntegrationTest:
  - Extends ScoreWriteRepositoryContractTest
  - Tests upsert logic, history recalculation, constraint enforcement
  - Validates score_progress.history_last12 is exactly 12 items (or fewer if < 12 attempts exist)
- PostgresDictionaryRepositoryIntegrationTest:
  - Extends DictionaryRepositoryContractTest
  - Sets up dictionary_pair rows with global_enabled = true/false
  - Tests filtering by global_enabled
  - Tests Word record hydration (all fields present)

---

### TASK-4.3: Create CSV Repository Contract Test Implementations
**Phase:** 4 (Testing & Integration)  
**Depends on:** Task 1.1, 4.1

- **Modify:** None

- **Create:**
  - `src/test/java/com/yodawife/easyll/repository/csv/CsvAccountRepositoryContractTest.java` (extends AccountRepositoryContractTest)
  - `src/test/java/com/yodawife/easyll/repository/csv/CsvScoreReadRepositoryContractTest.java` (extends ScoreReadRepositoryContractTest)
  - `src/test/java/com/yodawife/easyll/repository/csv/CsvScoreWriteRepositoryContractTest.java` (extends ScoreWriteRepositoryContractTest)
  - `src/test/java/com/yodawife/easyll/repository/csv/CsvDictionaryRepositoryContractTest.java` (extends DictionaryRepositoryContractTest)

- **Delete:** None

**Description:**
Create concrete test implementations for CSV repositories by extending contract test bases. These ensure CSV implementations meet the same interface contracts as DB implementations.

**Acceptance Criteria:**
- Each class extends the corresponding contract test base (e.g., CsvAccountRepositoryContractTest extends AccountRepositoryContractTest)
- Each class is annotated @Spring @ActiveProfiles("csv") to ensure CSV beans are loaded
- Implements abstract method(s) to instantiate the CSV repository instance
- All inherited @Test methods run against CSV implementations
- Tests use temporary CSV files or test fixtures (not production data files)
- CsvScoreWriteRepositoryContractTest may need to adapt flush() tests (CSV flushes to disk, not no-op)

---

### TASK-4.4: Create Migration Runner Tests
**Phase:** 4 (Testing & Integration)  
**Depends on:** Task 3.1, 2.1–2.4

- **Modify:** None

- **Create:**
  - `src/test/java/com/yodawife/easyll/migration/CsvToDbMigrationRunnerTest.java`
  - `src/test/java/com/yodawife/easyll/migration/MigrationErrorRecorderTest.java`

- **Delete:** None

**Description:**
Create unit and integration tests for the migration runner. Use in-memory test fixtures; no real database required for unit tests.

**Acceptance Criteria:**
- CsvToDbMigrationRunnerTest:
  - Mocks DataHealthService, CsvAccountRepository, JdbcTemplate
  - Tests dry-run mode (no DB writes)
  - Tests full migration (all tables populated)
  - Tests error handling (missing pairId, duplicate userId)
  - Tests logging: summary line with counts
  - Tests migration-errors.csv is created when errors occur
  - Tests idempotency (second run doesn't re-insert duplicates)
- MigrationErrorRecorderTest:
  - Tests CSV output format (header row + error rows)
  - Tests file creation if not exists
  - Tests append behavior (multiple error records)
  - Tests close() flushes buffered content

---

### TASK-4.5: Update Test Configuration & Properties
**Phase:** 4 (Testing & Integration)  
**Depends on:** Task 1.1, 1.3

- **Modify:**
  - `src/test/resources/application-test.properties` (add profile settings if needed)

- **Create:**
  - None (or optional: `src/test/resources/db/migration/V1__init_test.sql` if test schema differs from production)

- **Delete:** None

**Description:**
Ensure test profile uses CSV repositories by default; update any test properties to disable migration runner during normal test execution.

**Acceptance Criteria:**
- `spring.profiles.active=csv` or similar in application-test.properties ensures tests use CSV adapters by default
- `app.migration.enabled=false` in application-test.properties prevents migration runner from executing during normal tests
- DB integration tests explicitly activate "db" profile and override connection properties for testcontainers
- No duplicate or conflicting profile declarations

---

## Conflict Matrix

| Task | Conflicts with | Shared files |
|------|---------------|--------------|
| 1.1  | 1.3, 2.1–2.4, 3.1, 4.1–4.5 | None (indirect: dependency) |
| 1.2  | None | None |
| 1.3  | None | None |
| 2.1  | None | None |
| 2.2  | None | None |
| 2.3  | None | None |
| 2.4  | None | None |
| 3.1  | None | None |
| 4.1  | None | None |
| 4.2  | None | None |
| 4.3  | None | None |
| 4.4  | None | None |
| 4.5  | None | None |

**Summary:** No file-level conflicts. All tasks are isolated at the file level. Dependencies are logical (Phase 1 → Phase 2 → Phase 3 → Phase 4).

---

## Execution Plan

### Phase 1: Foundation (Parallel – All tasks can run simultaneously)

| Stream | Task ID | Title | Files Created | Files Modified |
|--------|---------|-------|---------------|----------------|
| A | TASK-1.1 | Add Build Dependencies | — | build.gradle.kts |
| B | TASK-1.2 | Create Flyway Migration Schema | V1__init.sql | — |
| C | TASK-1.3 | Create Profile-Based Repository Configuration | RepositoryConfiguration.java, MigrationProperties.java, application-csv.properties (opt), application-db.properties (opt) | — |

**Blockers for Phase 2:** All Phase 1 tasks must complete. No cross-blocking within Phase 1.

---

### Phase 2: DB Repository Implementations (Parallel – All tasks can run simultaneously)

| Stream | Task ID | Title | Files Created | Files Modified |
|--------|---------|-------|---------------|----------------|
| A | TASK-2.1 | Implement PostgresAccountRepository | PostgresAccountRepository.java, AccountRowMapper.java (if needed) | CsvAccountRepository.java (add @Profile("csv")) |
| B | TASK-2.2 | Implement PostgresScoreReadRepository | PostgresScoreReadRepository.java | — |
| C | TASK-2.3 | Implement PostgresScoreWriteRepository | PostgresScoreWriteRepository.java | — |
| D | TASK-2.4 | Implement PostgresDictionaryRepository | PostgresDictionaryRepository.java, DictionaryPairRowMapper.java (if needed) | — |

**Blockers for Phase 3:** All Phase 2 tasks must complete.

---

### Phase 3: Migration Infrastructure (Sequential – Cannot start until Phase 2 completes)

| Stream | Task ID | Title | Files Created | Files Modified |
|--------|---------|-------|---------------|----------------|
| A | TASK-3.1 | Implement CSV-to-DB Migration Runner | CsvToDbMigrationRunner.java, MigrationErrorRecorder.java, MigrationContext.java (if needed) | — |

**Blockers for Phase 4:** TASK-3.1 must complete.

---

### Phase 4: Testing & Integration (Parallel – All tasks can run simultaneously)

| Stream | Task ID | Title | Files Created | Files Modified |
|--------|---------|-------|---------------|----------------|
| A | TASK-4.1 | Create Abstract Repository Contract Test Bases | AccountRepositoryContractTest.java, ScoreReadRepositoryContractTest.java, ScoreWriteRepositoryContractTest.java, DictionaryRepositoryContractTest.java | — |
| B | TASK-4.2 | Create PostgreSQL Integration Tests | PostgresAccountRepositoryIntegrationTest.java, PostgresScoreReadRepositoryIntegrationTest.java, PostgresScoreWriteRepositoryIntegrationTest.java, PostgresDictionaryRepositoryIntegrationTest.java | — |
| C | TASK-4.3 | Create CSV Repository Contract Test Implementations | CsvAccountRepositoryContractTest.java, CsvScoreReadRepositoryContractTest.java, CsvScoreWriteRepositoryContractTest.java, CsvDictionaryRepositoryContractTest.java | — |
| D | TASK-4.4 | Create Migration Runner Tests | CsvToDbMigrationRunnerTest.java, MigrationErrorRecorderTest.java | — |
| E | TASK-4.5 | Update Test Configuration & Properties | — | application-test.properties |

**Blockers for Release:** All Phase 4 tasks must complete.

---

## Developer Assignment Guide

### Phase 1

**Developer A (TASK-1.1):**
- File: `build.gradle.kts`
- Exclusive scope: Gradle configuration section, dependencies block
- No file conflicts with other Phase 1 tasks
- Action: Add new dependency blocks; no modification to existing code

**Developer B (TASK-1.2):**
- File: `src/main/resources/db/migration/V1__init.sql`
- Exclusive scope: Create new SQL file (no existing file modified)
- Action: Write SQL DDL statements

**Developer C (TASK-1.3):**
- File: `src/main/java/com/yodawife/easyll/config/RepositoryConfiguration.java` (new)
- File: `src/main/java/com/yodawife/easyll/config/MigrationProperties.java` (new)
- File: `src/main/resources/application-*.properties` (new; optional)
- Exclusive scope: Configuration classes and properties; no modification to existing classes
- Action: Create new @Configuration class with @ConditionalOnProfile methods; create @ConfigurationProperties class

---

### Phase 2

**Developer A (TASK-2.1):**
- Files: 
  - `src/main/java/com/yodawife/easyll/repository/db/PostgresAccountRepository.java` (new)
  - `src/main/java/com/yodawife/easyll/repository/db/AccountRowMapper.java` (new, optional)
  - `src/main/java/com/yodawife/easyll/repository/CsvAccountRepository.java` (modify: add @Profile("csv"))
- Exclusive scope: Account repository DB implementation + minor CSV annotation
- Depends on Phase 1 completion (TASK-1.1, 1.3)

**Developer B (TASK-2.2):**
- File: `src/main/java/com/yodawife/easyll/repository/db/PostgresScoreReadRepository.java` (new)
- Exclusive scope: Score read repository DB implementation
- Depends on Phase 1 completion

**Developer C (TASK-2.3):**
- File: `src/main/java/com/yodawife/easyll/repository/db/PostgresScoreWriteRepository.java` (new)
- Exclusive scope: Score write repository DB implementation
- Depends on Phase 1 completion

**Developer D (TASK-2.4):**
- Files:
  - `src/main/java/com/yodawife/easyll/repository/db/PostgresDictionaryRepository.java` (new)
  - `src/main/java/com/yodawife/easyll/repository/db/DictionaryPairRowMapper.java` (new, optional)
- Exclusive scope: Dictionary repository DB implementation
- Depends on Phase 1 completion

---

### Phase 3

**Developer A (TASK-3.1):**
- Files:
  - `src/main/java/com/yodawife/easyll/migration/CsvToDbMigrationRunner.java` (new)
  - `src/main/java/com/yodawife/easyll/migration/MigrationErrorRecorder.java` (new)
  - `src/main/java/com/yodawife/easyll/migration/MigrationContext.java` (new, optional)
- Exclusive scope: Migration runner implementation
- Depends on Phase 2 completion (TASK-2.1–2.4)
- Action: Implement ApplicationRunner; orchestrate CSV→DB data transfer; handle errors

---

### Phase 4

**Developer A (TASK-4.1):**
- Files:
  - `src/test/java/com/yodawife/easyll/repository/AccountRepositoryContractTest.java` (new, abstract)
  - `src/test/java/com/yodawife/easyll/repository/ScoreReadRepositoryContractTest.java` (new, abstract)
  - `src/test/java/com/yodawife/easyll/repository/ScoreWriteRepositoryContractTest.java` (new, abstract)
  - `src/test/java/com/yodawife/easyll/repository/DictionaryRepositoryContractTest.java` (new, abstract)
- Exclusive scope: Abstract test base classes
- Depends on Phase 2 completion (for implementation context)

**Developer B (TASK-4.2):**
- Files:
  - `src/test/java/com/yodawife/easyll/repository/db/PostgresAccountRepositoryIntegrationTest.java` (new)
  - `src/test/java/com/yodawife/easyll/repository/db/PostgresScoreReadRepositoryIntegrationTest.java` (new)
  - `src/test/java/com/yodawife/easyll/repository/db/PostgresScoreWriteRepositoryIntegrationTest.java` (new)
  - `src/test/java/com/yodawife/easyll/repository/db/PostgresDictionaryRepositoryIntegrationTest.java` (new)
- Exclusive scope: DB integration tests with Testcontainers
- Depends on Phase 2 completion + TASK-4.1 availability (extends abstract bases)

**Developer C (TASK-4.3):**
- Files:
  - `src/test/java/com/yodawife/easyll/repository/csv/CsvAccountRepositoryContractTest.java` (new)
  - `src/test/java/com/yodawife/easyll/repository/csv/CsvScoreReadRepositoryContractTest.java` (new)
  - `src/test/java/com/yodawife/easyll/repository/csv/CsvScoreWriteRepositoryContractTest.java` (new)
  - `src/test/java/com/yodawife/easyll/repository/csv/CsvDictionaryRepositoryContractTest.java` (new)
- Exclusive scope: CSV contract test implementations
- Depends on TASK-4.1 availability (extends abstract bases)

**Developer D (TASK-4.4):**
- Files:
  - `src/test/java/com/yodawife/easyll/migration/CsvToDbMigrationRunnerTest.java` (new)
  - `src/test/java/com/yodawife/easyll/migration/MigrationErrorRecorderTest.java` (new)
- Exclusive scope: Migration runner unit/integration tests
- Depends on Phase 3 completion (TASK-3.1)

**Developer E (TASK-4.5):**
- File: `src/test/resources/application-test.properties` (modify)
- Exclusive scope: Test configuration
- Depends on Phase 1 completion (TASK-1.3)

---

## Key Implementation Notes

### Spring Profile Strategy

1. **CSV Profile (default):**
   - Activated by default (no explicit profile needed)
   - CsvAccountRepository, ScoreRepository (CSV impl), DataHealthService (CSV DictionaryRepository) are @Repository/@Service with @Profile("csv")
   - Configuration: RepositoryConfiguration class conditionally instantiates these if csv profile is active

2. **DB Profile:**
   - Explicitly activated with `--spring.profiles.active=db` or application-db.properties
   - PostgresAccountRepository, PostgresScoreReadRepository, PostgresScoreWriteRepository, PostgresDictionaryRepository all use @Repository with @Profile("db")
   - Requires PostgreSQL connection (Flyway will initialize schema)
   - CsvToDbMigrationRunner is enabled only if `app.migration.enabled=true`

3. **Test Profile:**
   - application-test.properties sets `spring.profiles.active=csv` (default) to use CSV implementations
   - DB integration tests annotate with `@ActiveProfiles("db")` to override and use DB implementations with Testcontainers

### Database Connection Configuration

- **CSV Profile:** No DB driver or Flyway needed; CSV files only
- **DB Profile:** application-db.properties should configure Spring Data JDBC datasource:
  ```properties
  spring.datasource.url=jdbc:postgresql://localhost:5432/easyll
  spring.datasource.username=postgres
  spring.datasource.password=...
  spring.datasource.driver-class-name=org.postgresql.Driver
  spring.flyway.enabled=true
  ```
- **Test Profile:** Testcontainers override datasource URL/credentials programmatically

### Migration Runner Activation

- Default: disabled (`app.migration.enabled=false` in application.properties)
- To run migration: start app with `--app.migration.enabled=true` or set in application-db.properties
- Dry-run mode: `--app.migration.dry-run=true` (default) logs planned changes without executing
- Errors are logged and written to `./data/migration-errors.csv` (configurable)

### Java Language Features

- Use Java 26 records (already in use; Account, LanguageBundle, etc.)
- Use var for type inference where appropriate
- Use sealed classes if defining hierarchy for domain objects (optional)
- Ensure NullAway compatibility: add @Nullable annotations to nullable fields
- Use String.format() or formatted string literals for logging/error messages

---

## Testing Strategy

### Contract Tests

- Abstract base classes (AccountRepositoryContractTest, etc.) define the contract that all implementations must satisfy
- CSV and DB implementations both extend these bases and inherit all @Test methods
- Ensures behavior consistency across backends
- New features added to interface are tested in contract; both implementations immediately inherit the test

### Integration Tests

- DB integration tests use Testcontainers(PostgreSQLContainer) to spin up ephemeral PostgreSQL instances
- Each test class has @Testcontainers annotation and a static @Container field
- Flyway migration (V1__init.sql) is run before each test class
- Tests are tagged @Tag("db") for optional filtering (e.g., exclude from CI if DB not available)

### Unit Tests

- Migration runner tests mock DataHealthService, CsvAccountRepository, JdbcTemplate
- Error recorder tests use file I/O assertions (verify CSV output format)
- No real database required for migration runner unit tests

---

## Acceptance Criteria Summary

**Before Phase 1 is complete:**
- [ ] build.gradle.kts compiles with all new dependencies
- [ ] V1__init.sql exists and Flyway recognizes it
- [ ] RepositoryConfiguration and MigrationProperties classes exist and compile

**Before Phase 2 is complete:**
- [ ] All four DB repository implementations exist and compile
- [ ] Each implements its interface completely (no abstract methods left)
- [ ] Profile annotations (@Profile("db")) are in place
- [ ] CsvAccountRepository has @Profile("csv") annotation

**Before Phase 3 is complete:**
- [ ] CsvToDbMigrationRunner exists and implements ApplicationRunner
- [ ] MigrationErrorRecorder exists and is functional
- [ ] Migration can be triggered with app.migration.enabled=true
- [ ] Dry-run mode works without modifying database

**Before Phase 4 is complete:**
- [ ] All abstract contract test bases compile
- [ ] All DB integration tests compile and pass (with Testcontainers)
- [ ] All CSV contract test implementations compile and pass
- [ ] Migration runner tests compile and pass
- [ ] Test profile defaults to csv; DB tests explicitly activate db profile
- [ ] Full test suite (gradle test) passes

---

## Build & Deployment Checklist

- [ ] `gradle clean build` succeeds with no errors
- [ ] All new test classes inherit from contract bases and pass inherited tests
- [ ] No Java compiler warnings (with NullAway checks enabled)
- [ ] Jacoco or similar code coverage tool shows >= 80% coverage for new code
- [ ] Integration test suite (tagged "db") passes against Testcontainers PostgreSQL
- [ ] CSV test suite passes (existing behavior preserved)
- [ ] Migration runner dry-run logs all operations to be performed
- [ ] No hardcoded passwords or connection strings in source code
- [ ] Documentation updated: add Phase 2 architecture diagram and migration procedure to docs/
