# Implementation Plan: CSV-to-PostgreSQL Migration

## Summary
- **Total tasks:** 20
- **Parallel phases:** 5
- **Maximum parallelism per phase:** 4 tasks
- **Critical path:** Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5

---

## Task File Scope

### TASK-001: Delete CSV Parser Classes & Tests
**Phase:** 1 (parallel)
- **Delete:**
  - src/main/java/com/yodawife/easyll/validation/WordsCsvParser.java
  - src/main/java/com/yodawife/easyll/validation/ModeEligibilityCsvParser.java
  - src/main/java/com/yodawife/easyll/validation/ScoreCsvParser.java
  - src/main/java/com/yodawife/easyll/domain/CsvParseResult.java
  - src/test/java/com/yodawife/easyll/validation/WordsCsvParserTest.java
  - src/test/java/com/yodawife/easyll/validation/WordsCsvParserNegativeTest.java
  - src/test/java/com/yodawife/easyll/validation/ScoreCsvParserTest.java
  - src/test/java/com/yodawife/easyll/validation/ModeEligibilityCsvParserTest.java

**Acceptance Criteria:**
- All CSV parser classes removed
- No references to WordsCsvParser, ScoreCsvParser, ModeEligibilityCsvParser remain in codebase
- CsvParseResult domain class removed
- Parser test files deleted
- Codebase compiles after deletion

---

### TASK-002: Delete Migration Infrastructure Classes & Tests
**Phase:** 1 (parallel)
- **Delete:**
  - src/main/java/com/yodawife/easyll/migration/CsvToDbMigrationRunner.java
  - src/main/java/com/yodawife/easyll/migration/MigrationErrorRecorder.java
  - src/main/java/com/yodawife/easyll/config/MigrationProperties.java
  - src/test/java/com/yodawife/easyll/migration/CsvToDbMigrationRunnerTest.java
  - src/test/java/com/yodawife/easyll/migration/MigrationErrorRecorderTest.java

**Acceptance Criteria:**
- All migration classes removed
- No references to CsvToDbMigrationRunner, MigrationErrorRecorder, or MigrationProperties remain
- Migration test files deleted
- Codebase compiles after deletion

---

### TASK-003: Delete PersistenceProfiles Configuration Class
**Phase:** 1 (parallel)
- **Delete:**
  - src/main/java/com/yodawife/easyll/config/PersistenceProfiles.java

**Modify:**
- src/main/java/com/yodawife/easyll/repository/CsvAccountRepository.java (line 37: `@Profile({"csv", "db"})` → will be deleted in Phase 3)
- src/main/java/com/yodawife/easyll/repository/CsvDictionaryRepository.java (line 17: `@Profile(PersistenceProfiles.CSV)` → will be deleted in Phase 3)
- src/main/java/com/yodawife/easyll/repository/ScoreRepository.java (line 31: `@Profile("csv")` → will be deleted in Phase 3)
- src/main/java/com/yodawife/easyll/repository/db/PostgresAccountRepository.java (line 24: `@Profile(PersistenceProfiles.DB)` → update to hardcoded string)
- src/main/java/com/yodawife/easyll/repository/db/PostgresDictionaryRepository.java (line 20: `@Profile(PersistenceProfiles.DB)` → update to hardcoded string)
- src/main/java/com/yodawife/easyll/repository/db/PostgresScoreReadRepository.java (line 22: `@Profile(PersistenceProfiles.DB)` → update to hardcoded string)
- src/main/java/com/yodawife/easyll/repository/db/PostgresScoreWriteRepository.java (line 18: `@Profile(PersistenceProfiles.DB)` → update to hardcoded string)

**Acceptance Criteria:**
- PersistenceProfiles.java deleted
- All @Profile annotations updated to use hardcoded string `"db"` (no longer reference deleted class)
- Codebase compiles
- Note: CSV profile references will be removed entirely in Phase 3 when those classes are deleted

---

### TASK-004: Create Flyway Migration for mode_eligibility Table
**Phase:** 1 (parallel)
- **Create:**
  - src/main/resources/db/migration/V2__mode_eligibility.sql

**File Content:**
```sql
CREATE TABLE IF NOT EXISTS mode_eligibility (
    pair_id VARCHAR(255) NOT NULL,
    mode VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL,
    PRIMARY KEY (pair_id, mode),
    FOREIGN KEY (pair_id) REFERENCES dictionary_pair(pair_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_mode_eligibility_pair_id ON mode_eligibility(pair_id);
```

**Acceptance Criteria:**
- Flyway migration file created at correct path
- Migration provides mode_eligibility table with proper schema (pair_id, mode, enabled)
- Composite primary key on (pair_id, mode)
- Foreign key constraint on dictionary_pair
- Migration runs cleanly on test database

---

### TASK-005: Delete MultiLanguageDictionaryParser & DictionaryDiscoveryService
**Phase:** 2 (depends on Phase 1)
- **Delete:**
  - src/main/java/com/yodawife/easyll/validation/MultiLanguageDictionaryParser.java
  - src/main/java/com/yodawife/easyll/service/DictionaryDiscoveryService.java

**Acceptance Criteria:**
- Both classes deleted
- No references to MultiLanguageDictionaryParser or DictionaryDiscoveryService in remaining code
- Note: DataHealthService imports these; it will be refactored in TASK-006
- Temporary compilation error expected; resolved when TASK-006 is merged

---

### TASK-006: Refactor DataHealthService to Query PostgreSQL
**Phase:** 2 (depends on Phase 1, blocks TASK-008)
**Modify:**
- src/main/java/com/yodawife/easyll/service/DataHealthService.java

**Changes:**
1. Remove constructor parameters:
   - Remove `ScoreCsvParser scoreCsvParser`
   - Remove `MultiLanguageDictionaryParser multiLanguageDictionaryParser`

2. Add new parameter:
   - Add `DictionaryRepository dictionaryRepository` (inject)
   - Add `ScoreReadRepository scoreReadRepository` (inject)
   - Add `JdbcTemplate jdbc` (for direct DB health checks if needed)

3. Refactor `reload()` method:
   - Remove CSV parsing logic (`scoreCsvParser.parse()`, `multiLanguageDictionaryParser.parseAll()`)
   - Replace with direct PostgreSQL queries:
     - Check if `app_user` table is readable and has records
     - Check if `dictionary_pair` table is readable and has records
     - Check if `score_progress` table is readable
   - Simplify to just report health status (no longer holds data bundles)
   - `DataSnapshot` should only contain: `wordsHealthy`, `scoresHealthy`, `wordErrors`, `scoreErrors`

4. Simplify `availableLanguages()` method:
   - Query `SELECT DISTINCT language_code FROM dictionary_pair WHERE enabled=true ORDER BY language_code`
   - Return list directly from DB

5. Remove snapshot fields:
   - Remove `wordData`, `multiLanguageData`, `scoreData` from maintained state
   - DataSnapshot becomes lightweight health indicator only

**Acceptance Criteria:**
- DataHealthService no longer depends on any CSV parser classes
- `reload()` performs direct DB health checks via JdbcTemplate or repository queries
- `snapshot()` returns health status only; no data bundles held
- `availableLanguages()` queries DB directly
- All tests for DataHealthService updated to work with DB-backed implementation
- Service compiles and starts successfully with `db` profile

---

### TASK-007: Enhance PostgresDictionaryRepository with Write Operations
**Phase:** 2 (depends on Phase 1, blocks TASK-008)
**Modify:**
- src/main/java/com/yodawife/easyll/repository/db/PostgresDictionaryRepository.java

**Changes:**
1. Add new write methods:
   ```java
   public int updateWord(String languageCode, WordId wordId, String fromWord, String toWord, String example, boolean globalEnabled)
   public int updateModeEligibility(String pairId, String mode, boolean enabled)
   public int insertModeEligibility(String pairId, String mode, boolean enabled)
   ```

2. Enhance read methods to include mode eligibilities:
   - Update `findLanguage(String languageCode)` to:
     - Query words from `dictionary_pair`
     - Query mode eligibilities from `mode_eligibility` table
     - Return complete `LanguageBundle` with both

3. Update query logic:
   - `findLanguage()` must JOIN `dictionary_pair` and `mode_eligibility` tables
   - Handle NULL eligibilities as implicit `enabled=true` (FR-021)
   - Return sorted word lists and complete eligibility sets

**Acceptance Criteria:**
- Write methods added (update, insert)
- Read methods return complete data with mode eligibilities
- Queries work correctly with new V2 migration schema
- Tests verify mode eligibility CRUD operations
- Backward compatibility: handles missing eligibility records as enabled=true

---

### TASK-008: Refactor DictionaryEditService to Use PostgreSQL
**Phase:** 3 (depends on Phase 2: TASK-006, TASK-007)
**Modify:**
- src/main/java/com/yodawife/easyll/service/DictionaryEditService.java

**Changes:**
1. Remove CSV dependencies:
   - Remove `CsvPersistence csvPersistence` parameter
   - Remove imports of `CsvPersistence`, file path resolution logic

2. Add DB dependency:
   - Add `DictionaryRepository dictionaryRepository` (inject)
   - Add `DictionaryWriteLock dictionaryWriteLock` (already present; keep)

3. Refactor `toggleGlobalEnabled()`:
   - Instead of: read from snapshot → modify → write CSV → reload
   - Do: query DB → update `dictionary_pair` table → audit log
   - Call `dictionaryRepository.updateWord(...)`
   - Publish `DataReloadedEvent` instead of calling `dataHealthService.reload()`
   - Remove file path resolution

4. Refactor `toggleModeEnabled()`:
   - Instead of: read from snapshot → modify → write mode-eligibility CSV → reload
   - Do: query DB → insert/update `mode_eligibility` table
   - Call `dictionaryRepository.updateModeEligibility(pairId, mode, enabled)`
   - Publish `DataReloadedEvent`
   - Remove file path resolution

5. Refactor `setModeEnabled()` (similar pattern):
   - Use DB operations instead of CSV writes

6. Remove methods:
   - Remove `resolveLanguagePath()` (no longer needed)
   - Remove `isEditableRootPath()` check (always editable now that it's DB)

7. Update exception handling:
   - Replace `IOException` handling with SQL/DB exception handling
   - Replace `DictionaryLockTimeoutException` with appropriate DB-level handling

**Acceptance Criteria:**
- All write operations use DictionaryRepository/JdbcTemplate
- No CSV file operations or path resolution
- No CsvPersistence imports or usage
- DataReloadedEvent published after writes
- Tests updated to verify DB write calls instead of CSV mock calls
- DictionaryEditServiceTest uses mocked DictionaryRepository instead of CsvPersistence

---

### TASK-009: Refactor PairIdIntegrityValidator to Query DB Directly
**Phase:** 3 (depends on Phase 2: TASK-006)
**Modify:**
- src/main/java/com/yodawife/easyll/service/PairIdIntegrityValidator.java

**Changes:**
1. Remove snapshot dependency:
   - Remove usage of `dataHealthService.snapshot().wordData()`
   - Remove usage of `dataHealthService.snapshot().multiLanguageData()`

2. Add repository dependency:
   - Add `DictionaryRepository dictionaryRepository` (inject)
   - Add `JdbcTemplate jdbc` for direct queries if needed

3. Refactor validation logic:
   - Instead of reading from snapshot bundles, query DB:
   - `SELECT pair_id FROM dictionary_pair WHERE language_code = ? AND enabled = true`
   - Validate pairIds against actual DB records
   - Collect pairIds for validation directly from DB

**Acceptance Criteria:**
- No snapshot() calls remain
- All validations performed via repository/JdbcTemplate queries
- Tests updated to mock DictionaryRepository instead of DataHealthService snapshot
- Service compiles and runs correctly

---

### TASK-010: Refactor FlashcardService, MatchBoardGenerator, MatchGameApplicationService
**Phase:** 3 (depends on Phase 2: TASK-006)
**Modify:**
- src/main/java/com/yodawife/easyll/service/FlashcardService.java
- src/main/java/com/yodawife/easyll/service/MatchBoardGenerator.java
- src/main/java/com/yodawife/easyll/service/MatchGameApplicationService.java

**Changes for FlashcardService:**
1. Replace snapshot-based word retrieval:
   - Current: reads from `dataHealthService.snapshot().wordData()` and `snapshot().getLanguageBundle(languageCode)`
   - New: inject `DictionaryRepository dictionaryRepository`
   - Query `dictionaryRepository.findLanguage(languageCode)` to get `LanguageBundle`
   - Extract words from bundle instead of snapshot

**Changes for MatchBoardGenerator:**
1. Same pattern as FlashcardService:
   - Replace snapshot reads with `DictionaryRepository.findLanguage()`
   - Query DB for available words instead of snapshot bundles

**Changes for MatchGameApplicationService:**
1. Replace snapshot-based language bundle retrieval:
   - Current: reads from `dataHealthService.snapshot().getLanguageBundle(languageCode)`
   - New: inject `DictionaryRepository dictionaryRepository`
   - Call `dictionaryRepository.findLanguage(languageCode)` instead

**Acceptance Criteria:**
- All three services use DictionaryRepository instead of DataHealthService snapshots
- No snapshot() calls remain in these classes
- Tests updated to mock DictionaryRepository instead of DataHealthService
- Services compile and function correctly

---

### TASK-011: Delete CsvPersistence Service Class
**Phase:** 3 (depends on Phase 3: TASK-008, after DictionaryEditService refactored)
- **Delete:**
  - src/main/java/com/yodawife/easyll/service/CsvPersistence.java

**Acceptance Criteria:**
- CsvPersistence deleted
- No remaining references in codebase (DictionaryEditService should not reference it)
- Codebase compiles

---

### TASK-012: Delete CsvAccountRepository & Test Files
**Phase:** 3 (depends on Phase 2 completion; safe to delete after migration classes removed)
- **Delete:**
  - src/main/java/com/yodawife/easyll/repository/CsvAccountRepository.java
  - src/test/java/com/yodawife/easyll/repository/CsvAccountRepositoryTest.java
  - src/test/java/com/yodawife/easyll/repository/csv/CsvAccountRepositoryContractTest.java

**Acceptance Criteria:**
- CsvAccountRepository deleted (only used by CsvToDbMigrationRunner, now deleted)
- Test files deleted
- Codebase compiles; PostgresAccountRepository is @Primary and takes over

---

### TASK-013: Delete CsvDictionaryRepository & Test Files
**Phase:** 3 (depends on Phase 2 completion)
- **Delete:**
  - src/main/java/com/yodawife/easyll/repository/CsvDictionaryRepository.java
  - src/test/java/com/yodawife/easyll/repository/csv/CsvDictionaryRepositoryContractTest.java

**Acceptance Criteria:**
- CsvDictionaryRepository deleted (replaced by enhanced PostgresDictionaryRepository)
- Contract test for CSV implementation deleted
- Codebase compiles; PostgresDictionaryRepository is @Primary

---

### TASK-014: Delete ScoreRepository (CSV) & Test Files
**Phase:** 3 (depends on Phase 2 completion; PostgreSQL implementations fully active)
- **Delete:**
  - src/main/java/com/yodawife/easyll/repository/ScoreRepository.java
  - src/test/java/com/yodawife/easyll/repository/ScoreRepositoryTest.java
  - src/test/java/com/yodawife/easyll/repository/csv/CsvScoreRepositoryContractTest.java

**Acceptance Criteria:**
- CSV ScoreRepository deleted
- Test files deleted
- PostgresScoreReadRepository and PostgresScoreWriteRepository are active and complete
- Codebase compiles; all score operations routed to Postgres implementations

---

### TASK-015: Refactor Controllers to Use DictionaryRepository Directly
**Phase:** 4 (depends on Phase 3: TASK-010 completion)
**Modify:**
- src/main/java/com/yodawife/easyll/controller/HomeController.java
- src/main/java/com/yodawife/easyll/controller/DictionaryController.java
- src/main/java/com/yodawife/easyll/controller/FlashcardsController.java
- src/main/java/com/yodawife/easyll/controller/HealthController.java

**Changes:**
1. HomeController:
   - Replace `dataHealthService.snapshot().getLanguageBundle(...)` with `dictionaryRepository.findLanguage(...)`
   - Replace `dataHealthService.availableLanguages()` with `dictionaryRepository.availableLanguages()`
   - Replace health check `snapshot.wordsHealthy()` with DB-level check or simple availability check

2. DictionaryController:
   - Replace all `dataHealthService.snapshot().getLanguageBundle(languageCode)` with `dictionaryRepository.findLanguage(languageCode)`
   - Replace all `dataHealthService.availableLanguages()` with `dictionaryRepository.availableLanguages()`
   - Keep mode eligibility access pattern but sourced from DB

3. FlashcardsController:
   - Replace `dataHealthService.snapshot().getLanguageBundle(languageCode)` with `dictionaryRepository.findLanguage(languageCode)`

4. HealthController:
   - Keep reference to `dataReloadApplicationService` (it still returns simplified health snapshot)
   - Optionally simplify health response to show DB connectivity status instead

**Acceptance Criteria:**
- All snapshot() calls replaced with repository calls
- Controllers use DictionaryRepository and ScoreRepository interfaces only
- No DataHealthService.snapshot() calls remain in controllers
- All controller tests updated to mock repositories instead of DataHealthService snapshots
- Controllers compile and render correctly

---

### TASK-016: Update Gradle Build Configuration
**Phase:** 5 (depends on Phase 4)
**Modify:**
- build.gradle.kts

**Changes:**
1. Remove CSV dependency:
   - Remove: `implementation("org.apache.commons:commons-csv:1.12.0")`

2. Verify DB dependencies present:
   - Confirm: `implementation("org.springframework.boot:spring-boot-starter-data-jdbc")`
   - Confirm: `runtimeOnly("org.postgresql:postgresql")`
   - Confirm: `implementation("org.flywaydb:flyway-core")`
   - Confirm: `implementation("org.flywaydb:flyway-database-postgresql")`

**Acceptance Criteria:**
- commons-csv dependency removed
- No CSV-related dependencies remain
- All PostgreSQL and Flyway dependencies present
- Gradle build succeeds with `./gradlew build`
- All classes compile without CSV library

---

### TASK-017: Update Application Properties
**Phase:** 5 (depends on Phase 4)
**Modify:**
- src/main/resources/application.properties
- src/main/resources/application-db.properties

**Changes to application.properties:**
1. Remove CSV-specific properties:
   - Remove: `app.scores.file-path=./data/scores/scores.csv`
   - Remove: `app.scores.write-path=./data/scores/scores.csv`
   - Remove: `app.accounts.file-path=./data/users/users.csv`
   - Remove: `app.dictionaries.root-path=./data/dictionaries`
   - Remove: `app.migration.enabled=false`
   - Remove: `app.migration.dry-run=true`
   - Remove: `app.migration.errors-output-path=./data/migration-errors.csv`

2. Remove profile management:
   - Remove: `spring.profiles.active=db` (or keep as fallback)
   - Remove: `spring.profiles.group.test=csv` (CSV profile no longer exists)

3. Update DataSource configuration:
   - Move datasource settings from application-db.properties into main application.properties
   - Ensure: `spring.datasource.url=jdbc:postgresql://localhost:5432/easyll` (with environment override support)
   - Ensure: `spring.datasource.username=easyll`
   - Ensure: `spring.datasource.password=easyll`

4. Update Flyway configuration:
   - Set: `spring.flyway.enabled=true` (always enabled; no longer conditional on profile)
   - Set: `spring.flyway.locations=classpath:db/migration`

5. Remove autoconfiguration exclusion:
   - Remove: `spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration`
   - This was needed to avoid error when no DB profile was active; no longer needed

6. Keep application properties:
   - Keep: `app.match.max-attempts=30`
   - Keep: `app.match.board-size=5`
   - Keep: `app.dictionaries.primary-language-code=hun`
   - Keep: `app.dictionaries.modes=flashcards,match`
   - Keep security settings

**Changes to application-db.properties:**
- Option A (preferred): Delete this file; merge needed settings into main application.properties
- Option B: Keep but mark as deprecated; only override if needed in specific environments

**Final application.properties structure:**
```properties
spring.application.name=easyll

# Security (default credentials for development)
spring.security.user.name=admin
spring.security.user.password=admin
spring.security.user.roles=ADMIN

# PostgreSQL datasource
spring.datasource.url=jdbc:postgresql://localhost:5432/easyll
spring.datasource.username=easyll
spring.datasource.password=easyll
spring.datasource.driver-class-name=org.postgresql.Driver

# Flyway database migrations
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

# Application-specific properties
app.match.max-attempts=30
app.match.board-size=5
app.dictionaries.primary-language-code=hun
app.dictionaries.modes=flashcards,match
```

**Acceptance Criteria:**
- CSV properties removed
- Profile groups removed (test no longer activates "csv")
- Spring profiles functionality simplified (only "db" profile exists)
- PostgreSQL datasource configured in main properties
- Flyway always enabled
- Application starts correctly with default properties
- Application starts correctly with environment variable overrides
- No warnings about missing profiles or configurations

---

### TASK-018: Update Test Configuration
**Phase:** 5 (depends on Phase 4)
**Modify:**
- src/test/resources/application-test.properties

**Changes:**
1. Remove CSV-specific test properties:
   - Remove any `app.scores.*` properties
   - Remove any `app.accounts.file-path` overrides
   - Remove any `app.migration.*` properties

2. Update to point to test database:
   - Ensure: test datasource points to Testcontainers PostgreSQL (or mock DB)
   - Verify Spring Boot Test automatically handles @DataJdbcTest and @SpringBootTest with Testcontainers

3. Update profile group if present:
   - Remove: `spring.profiles.group.test=csv`
   - Add (if needed): any test-specific profile activation

**Acceptance Criteria:**
- Test configuration has no CSV references
- Tests run against PostgreSQL (Testcontainers or in-memory)
- All integration tests pass with DB profile active
- Unit tests mock repositories correctly

---

### TASK-019: Delete All CSV Data Files
**Phase:** 5 (can run in parallel with other Phase 5 tasks, but safest after config complete)
- **Delete:**
  - data/dictionaries/hun/words.csv
  - data/dictionaries/hun/mode-eligibility.csv
  - data/dictionaries/pl/words.csv
  - data/dictionaries/pl/mode-eligibility.csv
  - data/users/users.csv
  - data/scores/scores.csv
  - src/test/resources/scores-test.csv
  - src/test/resources/data/dictionaries/hun/words.csv
  - src/test/resources/data/dictionaries/hun/mode-eligibility.csv
  - src/test/resources/data/dictionaries/pl/words.csv
  - src/test/resources/data/dictionaries/pl/mode-eligibility.csv

**Acceptance Criteria:**
- All CSV data files deleted
- data/dictionaries, data/users, data/scores directories may be empty or deleted
- No CSV files remain in data/ or src/test/resources/data/
- Tests do not rely on pre-existing CSV files

---

### TASK-020: Update Affected Service & Controller Tests
**Phase:** 5 (depends on Phase 4; final test cleanup)
**Modify:**
- src/test/java/com/yodawife/easyll/service/DataHealthServiceTest.java
- src/test/java/com/yodawife/easyll/service/DictionaryEditServiceTest.java
- src/test/java/com/yodawife/easyll/service/DataReloadApplicationServiceTest.java
- src/test/java/com/yodawife/easyll/controller/HomeControllerTest.java
- src/test/java/com/yodawife/easyll/controller/DictionaryControllerTest.java
- src/test/java/com/yodawife/easyll/controller/FlashcardsControllerTest.java
- src/test/java/com/yodawife/easyll/controller/HealthControllerTest.java

**Changes for DataHealthServiceTest:**
1. Remove MultiLanguageDictionaryParser mock
2. Remove ScoreCsvParser mock
3. Add DictionaryRepository mock
4. Add ScoreReadRepository mock
5. Update reload() tests to verify DB health checks are called instead of CSV parsing
6. Simplify snapshot assertions (no more data bundles)
7. Tests should verify: `dictionaryRepository.availableLanguages()` called

**Changes for DictionaryEditServiceTest:**
1. Remove CsvPersistence mock
2. Add DictionaryRepository mock
3. Update assertion from `verify(csvPersistence).writeWords(...)` to `verify(dictionaryRepository).updateWord(...)`
4. Update assertion from `verify(csvPersistence).writeModeEligibilities(...)` to `verify(dictionaryRepository).updateModeEligibility(...)`
5. Remove file path resolution mocks
6. Update exception scenarios from IOException to SQL exceptions

**Changes for DataReloadApplicationServiceTest:**
1. Update snapshot() method expectations (health-only snapshot, no data bundles)
2. Verify event publishing still works

**Changes for Controller Tests:**
1. HomeControllerTest:
   - Remove `dataHealthService.snapshot()` mocks
   - Add `dictionaryRepository.findLanguage()` mocks
   - Add `dictionaryRepository.availableLanguages()` mocks
   - Update assertions to verify repository calls instead of service calls

2. DictionaryControllerTest:
   - Same pattern as HomeControllerTest
   - Update assertions for mode eligibility retrieval from DictionaryRepository

3. FlashcardsControllerTest:
   - Remove snapshot mocks
   - Add DictionaryRepository mocks
   - Update assertions

4. HealthControllerTest:
   - Minimal changes; keep DataReloadApplicationService interaction

**Acceptance Criteria:**
- All tests updated to mock DictionaryRepository instead of DataHealthService snapshots
- No CSV-related mocks remain (CsvPersistence, ScoreCsvParser, MultiLanguageDictionaryParser)
- All assertions verify DB/repository calls instead of CSV file operations
- All tests pass with Phase 4 changes in place
- Test coverage maintained (if not improved)

---

## Conflict Matrix

| Task | Conflicts with | Shared files |
|------|---|---|
| TASK-001 | None | — |
| TASK-002 | None | — |
| TASK-003 | TASK-001, TASK-002 (all must complete before Phase 2) | (no direct file overlap but order-dependent) |
| TASK-004 | None | — |
| TASK-005 | TASK-001, TASK-002, TASK-003 | (depends on Phase 1 deletion completeness) |
| TASK-006 | TASK-005, TASK-001, TASK-002, TASK-003 | — |
| TASK-007 | None (Phase 2) | — |
| TASK-008 | TASK-006, TASK-007 | — |
| TASK-009 | TASK-006 | — |
| TASK-010 | TASK-006 | — |
| TASK-011 | TASK-008 | — |
| TASK-012 | TASK-002, TASK-005 | — |
| TASK-013 | TASK-005, TASK-007 | — |
| TASK-014 | Phase 2 completion | — |
| TASK-015 | TASK-010, TASK-006 | — |
| TASK-016 | TASK-001 (Phase 1 must complete) | build.gradle.kts |
| TASK-017 | Phase 3 completion | src/main/resources/application.properties, src/main/resources/application-db.properties |
| TASK-018 | Phase 3 completion | src/test/resources/application-test.properties |
| TASK-019 | All other tasks (no code references remaining) | data/dictionaries/*, data/users/users.csv, data/scores/scores.csv |
| TASK-020 | TASK-015, Phase 4 completion | Multiple test files |

---

## Execution Plan

### Phase 1 (parallel) — Foundational Deletion
| Stream | Task ID | Title | Est. Effort |
|--------|---------|-------|---|
| A | TASK-001 | Delete CSV Parser Classes & Tests | 30 min |
| B | TASK-002 | Delete Migration Infrastructure | 30 min |
| C | TASK-003 | Delete PersistenceProfiles & Update Profile References | 20 min |
| D | TASK-004 | Create Flyway V2__mode_eligibility Migration | 15 min |

**Phase 1 Completion Criteria:**
- All CSV parser classes removed
- Migration infrastructure deleted
- PersistenceProfiles deleted; DB profile references updated to hardcoded strings
- V2 Flyway migration created and verified
- Codebase compiles with 0 errors

---

### Phase 2 (parallel) — Core Refactoring
| Stream | Task ID | Title | Est. Effort |
|--------|---------|-------|---|
| A | TASK-005 | Delete MultiLanguageDictionaryParser & DictionaryDiscoveryService | 20 min |
| B | TASK-006 | Refactor DataHealthService to Query PostgreSQL | 1.5 hours |
| C | TASK-007 | Enhance PostgresDictionaryRepository with Writes | 1.5 hours |

**Phase 2 Completion Criteria:**
- MultiLanguageDictionaryParser and DictionaryDiscoveryService deleted
- DataHealthService no longer parses CSVs; queries DB directly
- DataSnapshot simplified to health-only state
- PostgresDictionaryRepository enhanced with write methods and mode eligibility queries
- All three components tested individually and with integration
- Codebase compiles

---

### Phase 3 (mostly parallel) — Service Layer Migration
| Stream | Task ID | Title | Est. Effort |
|--------|---------|-------|---|
| A | TASK-008 | Refactor DictionaryEditService to Use DB | 1 hour |
| B | TASK-009 | Refactor PairIdIntegrityValidator | 45 min |
| C | TASK-010 | Refactor FlashcardService, MatchBoardGenerator, MatchGameApplicationService | 1 hour |
| D | TASK-011 | Delete CsvPersistence | 5 min |
| E | TASK-012 | Delete CsvAccountRepository & Tests | 10 min |
| F | TASK-013 | Delete CsvDictionaryRepository & Tests | 10 min |
| G | TASK-014 | Delete ScoreRepository & Tests | 15 min |

**Phase 3 Completion Criteria:**
- All service-layer snapshot() calls replaced with repository calls
- DictionaryEditService uses DB writes instead of CSV
- All CSV repository implementations deleted
- CsvPersistence deleted
- Codebase compiles and services run with Postgres backend

---

### Phase 4 (sequential) — Controller & Integration Updates
| Stream | Task ID | Title | Est. Effort |
|--------|---------|-------|---|
| A | TASK-015 | Refactor Controllers to Use DictionaryRepository | 1 hour |

**Phase 4 Completion Criteria:**
- All controllers query repositories directly
- No snapshot() calls in controller layer
- Controllers tested and working with DB backend
- All integration tests pass

---

### Phase 5 (parallel) — Final Configuration & Cleanup
| Stream | Task ID | Title | Est. Effort |
|--------|---------|-------|---|
| A | TASK-016 | Update Gradle Build Configuration | 15 min |
| B | TASK-017 | Update Application Properties | 30 min |
| C | TASK-018 | Update Test Configuration | 15 min |
| D | TASK-019 | Delete All CSV Data Files | 5 min |
| E | TASK-020 | Update Service & Controller Tests | 2 hours |

**Phase 5 Completion Criteria:**
- commons-csv dependency removed
- Application properties cleaned; all CSV properties removed
- Test properties updated; profiles simplified
- CSV data files deleted
- All affected tests updated and passing
- Full application build succeeds
- `./gradlew build` and `./gradlew test` pass with 0 errors

---

## Developer Assignment Guide

### Stream A Tasks (Sequential Path)
**Developer 1:** Owns critical refactorings

| Phase | Task | Files | Notes |
|-------|------|-------|-------|
| 1 | TASK-001 | Deletion only | Clean removal |
| 2 | TASK-006 | DataHealthService.java + tests | Core service refactor |
| 3 | TASK-008 | DictionaryEditService.java + tests | Depends on TASK-006, TASK-007 |
| 4 | TASK-015 | 4 controller files + tests | Depends on TASK-010 |
| 5 | TASK-020 | 7 test files | Final test cleanup |

### Stream B Tasks (Repository Enhancement)
**Developer 2:** PostgreSQL repository work

| Phase | Task | Files | Notes |
|-------|------|-------|---|
| 1 | TASK-002 | Deletion only | Clean removal |
| 2 | TASK-007 | PostgresDictionaryRepository.java | Add write methods, mode_eligibility |
| 3 | TASK-013 | CsvDictionaryRepository deletion + tests | Replaces CSV impl |
| 5 | TASK-017 | application*.properties | Config updates |

### Stream C Tasks (Service Layer)
**Developer 3:** Service refactoring

| Phase | Task | Files | Notes |
|-------|------|-------|---|
| 1 | TASK-003 | PersistenceProfiles deletion + @Profile updates | 7 files touched |
| 2 | TASK-005 | 2 class deletions | Remove CSV-era parsers |
| 3 | TASK-009 | PairIdIntegrityValidator.java | DB queries |
| 3 | TASK-010 | 3 service files | Snapshot → repository |

### Stream D Tasks (Cleanup & Migration)
**Developer 4:** Infrastructure & tests

| Phase | Task | Files | Notes |
|-------|------|-------|---|
| 1 | TASK-004 | V2__mode_eligibility.sql | New Flyway migration |
| 3 | TASK-011 | CsvPersistence deletion | Simple removal |
| 3 | TASK-012 | CsvAccountRepository deletion + tests | Replaces CSV impl |
| 3 | TASK-014 | ScoreRepository deletion + tests | Replaces CSV impl |
| 5 | TASK-016 | build.gradle.kts | Remove commons-csv |
| 5 | TASK-018 | application-test.properties | Test config |
| 5 | TASK-019 | CSV data files | Safe deletion |

---

## Assumptions & Risks

### Assumptions
1. **V1 Flyway migration already applied:** `V1__init.sql` has created `app_user`, `dictionary_pair`, `score_attempt`, `score_progress` tables.
2. **PostgreSQL is running:** Development environment has PostgreSQL available (Testcontainers for tests, localhost for dev).
3. **No remaining CSV profile usage:** All active code paths use `db` profile; no "csv" profile-specific code runs in production.
4. **DictionaryRepository write methods sufficient:** Enhanced PostgresDictionaryRepository covers all write operations needed by DictionaryEditService.
5. **Tests use Testcontainers:** Integration tests (particularly `PostgresDictionaryRepositoryContractTest`, etc.) use Testcontainers PostgreSQL for isolation.
6. **DataSnapshot is safe to simplify:** Controllers and services have no hidden dependency on data bundles within snapshot; only health flags are needed.

### Risks

| Risk | Impact | Mitigation |
|------|--------|-----------|
| **DataSnapshot simplification breaks unforeseen code** | Phase 2 refactoring fails | In TASK-006: Run full integration test suite before merging; verify all snapshot usages identified |
| **PostgresDictionaryRepository write methods incomplete** | DictionaryEditService refactoring stalls | In TASK-007: Comprehensive unit test coverage; contract test against real DB |
| **Profile @Primary conflicts** | Ambiguous bean at startup | In TASK-003: Verify all PostgresXxx repositories have @Primary; delete all CSV impls cleanly in Phase 3 |
| **CSV data files needed by tests after deletion** | Tests fail in Phase 5 | In TASK-020: Ensure all tests use DB fixtures or Testcontainers; verify no test data dependencies on CSV files |
| **Application properties migration incomplete** | Runtime errors on datasource config | In TASK-017: Run application with all property combinations (default, env var override, external config) |
| **Mode eligibility constraints violated** | V2 migration fails in production | In TASK-004: Verify foreign key constraints; test migration with existing dictionary_pair data |
| **Profiles still referenced after deletion** | Compilation errors | In TASK-003: Grep for `PersistenceProfiles.` or `@Profile("csv")` after completion; verify no hardcoded "csv" profiles remain |

---

## Key Milestones

- **Phase 1 complete:** All CSV classes deleted, profiles hardcoded; codebase compiles. ✓ Ready for Phase 2.
- **Phase 2 complete:** DataHealthService queries DB; PostgresDictionaryRepository fully functional with writes and mode eligibility. ✓ Ready for Phase 3.
- **Phase 3 complete:** All services updated; CSV implementations deleted; only PostgreSQL backend remains. ✓ Ready for Phase 4.
- **Phase 4 complete:** Controllers use repositories directly; full integration tested. ✓ Ready for Phase 5.
- **Phase 5 complete:** Build clean; tests pass; application runs with PostgreSQL only. ✓ **CSV Removal Complete**.

---

## Rollback Strategy

If critical issues arise:
- **Within Phase 1:** No rollback needed; deletions are isolated and reversible via git.
- **Within Phase 2:** Revert DataHealthService and PostgresDictionaryRepository changes; restore MultiLanguageDictionaryParser import.
- **Within Phase 3+:** Revert service layers; restore CsvPersistence, CSV repositories. Fallback to dual-profile mode (csv+db) temporarily.

**Safety net:** Git branch per phase; tag stable points before moving to next phase.

---

## Success Criteria (End of Phase 5)

- ✅ Zero CSV-related code remains in codebase
- ✅ Zero CSV data files in data/ or src/test/resources/
- ✅ `build.gradle.kts` has no commons-csv dependency
- ✅ `application.properties` has no app.scores.*, app.accounts.file-path, app.migration.*, or CSV-related profiles
- ✅ All tests pass against PostgreSQL (Testcontainers or local instance)
- ✅ Application starts successfully with `./gradlew bootRun` and connects to PostgreSQL
- ✅ All CRUD operations verified against DB (create, read via controllers, update via DictionaryEditService, delete via cascading foreign keys)
- ✅ Mode eligibility table V2__mode_eligibility.sql applied and functional
- ✅ Audit logging and data reload events still work
- ✅ No compile warnings or errors

---

*Plan created: 2026-06-09*
*Estimated total effort: ~10-12 developer-days across 4 developers (5-6 calendar days with 4-stream parallelism)*
