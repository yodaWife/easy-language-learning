# Phase 3 Implementation Plan: Production Cutover

## Executive Summary

**Total Tasks:** 8  
**Parallel Phases:** 4  
**Maximum Parallelism per Phase:** 3 tasks  
**Critical Path:** PostgreSQL Setup → Database Creation → Dry-run Migration → Live Migration → Profile Switch → Smoke Tests → Documentation

---

## Task File Scope

### PREP-001: PostgreSQL Environment Verification & Setup

**Title:** Verify PostgreSQL availability or provide installation path (Windows)

**Phase:** 1 (Foundation)

- **Modify:**
  - `docs/developer-guide.md` (add PostgreSQL setup section under "Build and run")

- **Create:**
  - (None — follow-up documentation only)

- **Delete:** (none)

**Description:**
On Windows developer machine, check if PostgreSQL is installed (via `psql --version` or registry check). If unavailable:
- Recommend standalone PostgreSQL installer from postgresql.org (preferred over Docker due to past reliability issues)
- Provide steps to verify installation and psql availability in PATH

**Acceptance Criteria:**
- Developer can confirm PostgreSQL is installed with `psql --version`
- `psql` binary is available in shell PATH
- developer-guide.md includes clear PostgreSQL installation and PATH setup instructions for Windows

**Open Questions:**
- Is PostgreSQL currently installed on the developer machine? Check by running `psql --version` or `pg_ctl --version`
- If Docker is the chosen path, will docker-compose be used or manual `docker run` commands?

---

### SETUP-001: Create PostgreSQL Database and User

**Title:** Create easyll database and user with appropriate permissions

**Phase:** 2 (Dependent on PostgreSQL being available)

- **Modify:** (none — SQL operations only)

- **Create:**
  - `docs/database-setup.md` (SQL commands and manual setup steps)

- **Delete:** (none)

**Description:**
Using `psql` as a superuser, execute:
```sql
CREATE USER easyll WITH PASSWORD 'easyll';
CREATE DATABASE easyll OWNER easyll;
GRANT ALL PRIVILEGES ON DATABASE easyll TO easyll;
```

Document the exact steps in a new setup guide so the developer can follow them manually.

**Acceptance Criteria:**
- `psql -U easyll -d easyll -h localhost` connects successfully
- database-setup.md provides copy-paste ready SQL commands
- Developer has confirmed database and user exist via `\du` and `\l` in psql

---

### CONFIG-001: Verify application-db.properties Configuration

**Title:** Verify PostgreSQL datasource config matches environment

**Phase:** 2 (Can run in parallel with SETUP-001)

- **Modify:** (none — verify only, no changes expected)

- **Create:** (none)

- **Delete:** (none)

**Description:**
Review [src/main/resources/application-db.properties](src/main/resources/application-db.properties):
- `spring.datasource.url=jdbc:postgresql://localhost:5432/easyll` ✓
- `spring.datasource.username=easyll` ✓
- `spring.datasource.password=easyll` ✓
- `spring.datasource.driver-class-name=org.postgresql.Driver` ✓
- `spring.autoconfigure.exclude=` (empty — permits DataSource autoconfiguration) ✓
- `spring.flyway.enabled=true` ✓
- `spring.flyway.locations=classpath:db/migration` ✓

No code changes needed; file is ready.

**Acceptance Criteria:**
- All datasource values match the database/user created in SETUP-001
- Flyway is enabled and points to `db/migration` (existing V1__init.sql)
- Developer confirms they reviewed the file and values are correct

---

### TEST-001: Execute Dry-Run Migration

**Title:** Run CSV-to-DB migration in dry-run mode to validate data and identify errors

**Phase:** 3 (Depends on SETUP-001 and CONFIG-001)

- **Modify:** (none)

- **Create:**
  - `docs/phase-3-testing.md` (dry-run and live migration procedures)

- **Delete:** (none)

**Description:**
Start the app with profile `db` and migration properties:
```bash
./gradlew bootRun \
  --args='--spring.profiles.active=db \
          --app.migration.enabled=true \
          --app.migration.dry-run=true'
```

Observe logs:
- Should see `[DRY-RUN]` messages for each user, pair, and score entry
- No actual database writes occur
- Migration summary logged at end: "Migration complete: X users, Y pairs, Z score entries processed. N errors."
- If `data/migration-errors.csv` is created and non-empty, review and document unresolved references

**Acceptance Criteria:**
- App starts without connection errors
- Logs show `[DRY-RUN]` output for users, pairs, and scores
- No actual inserts occur (verify by checking database remains empty or unchanged)
- `data/migration-errors.csv` is empty or contains only expected/documented errors
- Migration summary is logged successfully

---

### MIGRATE-001: Execute Live Migration

**Title:** Run CSV-to-DB migration for real, writing data to PostgreSQL

**Phase:** 3 (Depends on TEST-001 success)

- **Modify:** (none)

- **Create:**
  - (none — reference existing docs)

- **Delete:** (none)

**Description:**
Start the app with profile `db` and migration enabled, dry-run disabled:
```bash
./gradlew bootRun \
  --args='--spring.profiles.active=db \
          --app.migration.enabled=true \
          --app.migration.dry-run=false'
```

Verify in PostgreSQL console:
```sql
SELECT COUNT(*) FROM app_user;
SELECT COUNT(*) FROM dictionary_pair;
SELECT COUNT(*) FROM score_progress;
SELECT COUNT(*) FROM score_attempt;
```

Expected row counts should match the CSV data (see data/ folder):
- 2 users (Ewa, Ala)
- ~150+ dictionary pairs (Hungarian words)
- Multiple score_progress and score_attempt rows

**Acceptance Criteria:**
- App starts, runs migration, and writes data without errors
- PostgreSQL tables are populated with expected row counts
- Data integrity is visually confirmed (sample queries on users, pairs, scores)
- Logs show no SQL constraint violations or foreign key errors
- Migration summary indicates 0 or acceptable errors

---

### CUTOVER-001: Switch Default Profile to `db`

**Title:** Update application.properties to activate PostgreSQL profile by default

**Phase:** 4 (Depends on MIGRATE-001 success)

- **Modify:**
  - `src/main/resources/application.properties`

- **Create:** (none)

- **Delete:** (none)

**Description:**
Change the default profile from CSV to PostgreSQL:

**Before:**
```properties
spring.profiles.active=csv
```

**After:**
```properties
spring.profiles.active=db
```

This ensures future app starts default to PostgreSQL adapters. CSV adapters remain in the codebase as fallback (not deleted).

**Acceptance Criteria:**
- Profile line changed from `csv` to `db`
- File otherwise unchanged
- Change is committed to version control

---

### VERIFY-001: Smoke Test on `db` Profile

**Title:** Verify app runs correctly with PostgreSQL and all features work end-to-end

**Phase:** 4 (Parallel with CUTOVER-001, tests run after)

- **Modify:** (none)

- **Create:**
  - `docs/phase-3-testing.md` (smoke test procedure)

- **Delete:** (none)

**Description:**
After profile switch, start the app normally:
```bash
./gradlew bootRun
```

Run smoke tests manually or via test suite:

1. **Web Access:** Navigate to `http://localhost:8080` — home page loads
2. **Sign-in Flow:** 
   - Click "Sign in" → enter user "Ewa" → confirm signed-in status
   - Play a match or flashcards round → confirm scores are read from database
   - Sign out → confirm session cleared
3. **Data Integrity:**
   - Visit `/health/data` endpoint — data health OK
   - Verify progress percentages match migrated scores
4. **Admin Reload:**
   - POST to `/admin/data/reload` (with HTTP Basic auth admin:admin)
   - Confirm data reloads without errors
5. **Test Suite:**
   - Run `./gradlew test` — all tests pass (227 passing, 16 skipped)
   - No test failures on `db` profile

**Acceptance Criteria:**
- App starts with `db` profile (no CSV file reads)
- Sign-in/play/sign-out flow works end-to-end
- Scores persist and reload correctly from PostgreSQL
- Dictionary data loads without errors
- Test suite runs to completion (no new failures)
- No connection errors or constraint violations in logs

**Open Questions:**
- Should a special integration test be created for the `db` profile, or do existing tests suffice?

---

### DOCS-001: Mark Phase 3 Complete

**Title:** Update project documentation to reflect Phase 3 cutover completion

**Phase:** 5 (Final, after verification)

- **Modify:**
  - `docs/developer-guide.md`
  - `docs/architecture/db-migration-blueprint.md`
  - `docs/adr/0001-db-migration-readiness.md`

- **Create:** (none — existing docs only)

- **Delete:** (none)

**Description:**
Update documentation to reflect Phase 3 completion:

1. **developer-guide.md:**
   - Update "Configuration reference" section to note `spring.profiles.active=db` is now default
   - Add PostgreSQL setup instructions under "Build and run"
   - Update persistence model section to indicate CSV is fallback only
   - Add note that Phase 3 cutover is complete and production uses PostgreSQL

2. **db-migration-blueprint.md:**
   - Update Phase 3 section to indicate cutover is complete
   - Document the actual cutover procedure that was followed
   - Mark all remaining work as done

3. **0001-db-migration-readiness.md:**
   - Update "Remaining scope" to indicate Phase 3 is complete
   - Add note that production is now on PostgreSQL with CSV as fallback

**Acceptance Criteria:**
- All three files updated with Phase 3 completion status
- PostgreSQL setup procedure clearly documented
- CSV fallback role is documented (not removed, not primary)
- No conflicting or outdated information remains
- Documentation is clear for future developers joining the project

---

## Conflict Matrix

| Task | Conflicts with | Shared files |
|------|---------------|--------------|
| PREP-001 | None | docs/developer-guide.md |
| SETUP-001 | CONFIG-001 | docs/database-setup.md (create only) |
| CONFIG-001 | None | None (read-only) |
| TEST-001 | MIGRATE-001 | data/migration-errors.csv |
| MIGRATE-001 | TEST-001, CUTOVER-001 | PostgreSQL database |
| CUTOVER-001 | VERIFY-001 | src/main/resources/application.properties |
| VERIFY-001 | None (read-only on config) | None (runs against live app) |
| DOCS-001 | All (final phase) | docs/developer-guide.md, docs/architecture/db-migration-blueprint.md, docs/adr/0001-db-migration-readiness.md |

---

## Execution Plan

### Phase 1 (parallel)

| Stream | Task ID | Title |
|--------|---------|-------|
| A | PREP-001 | PostgreSQL Environment Verification & Setup |

**Duration:** ~15–30 minutes (check installed or download + install)

---

### Phase 2 (parallel)

| Stream | Task ID | Title |
|--------|---------|-------|
| A | SETUP-001 | Create PostgreSQL Database and User |
| B | CONFIG-001 | Verify application-db.properties Configuration |

**Dependencies:** PREP-001 must complete (PostgreSQL must be installed)  
**Duration:** ~10 minutes total (SETUP-001: 5 min, CONFIG-001: 5 min in parallel)

---

### Phase 3 (sequential)

| Stream | Task ID | Title |
|--------|---------|-------|
| A | TEST-001 | Execute Dry-Run Migration |

**Dependencies:** SETUP-001 and CONFIG-001 must complete  
**Duration:** ~5–10 minutes (app startup + log review)

---

### Phase 3b (sequential, after TEST-001)

| Stream | Task ID | Title |
|--------|---------|-------|
| A | MIGRATE-001 | Execute Live Migration |

**Dependencies:** TEST-001 must complete and pass  
**Duration:** ~5–10 minutes (app startup + data write + verification)

---

### Phase 4 (parallel, after MIGRATE-001)

| Stream | Task ID | Title |
|--------|---------|-------|
| A | CUTOVER-001 | Switch Default Profile to `db` |
| B | VERIFY-001 | Smoke Test on `db` Profile |

**Dependencies:** MIGRATE-001 must complete  
**Duration:** ~15–20 minutes total (CUTOVER-001: 2 min, VERIFY-001: 15 min in parallel)

---

### Phase 5 (final)

| Stream | Task ID | Title |
|--------|---------|-------|
| A | DOCS-001 | Mark Phase 3 Complete |

**Dependencies:** VERIFY-001 must pass  
**Duration:** ~20–30 minutes (update three docs files)

---

## Developer Assignment Guide

### PREP-001: PostgreSQL Verification (Developer 1)
- **Working Files:** 
  - docs/developer-guide.md (create PostgreSQL setup section)
- **Blocking:** All subsequent database tasks
- **Hands-on:** Check Windows for PostgreSQL installation; download and install if needed
- **Deliverable:** Confirmed PostgreSQL installation with `psql --version` working

### SETUP-001: Database Creation (Developer 1)
- **Working Files:**
  - docs/database-setup.md (new file)
  - PostgreSQL shell (psql)
- **Blocked by:** PREP-001
- **Hands-on:** Execute SQL commands in psql to create user and database
- **Deliverable:** Confirmed database and user exist via `psql -U easyll -d easyll`

### CONFIG-001: Config Verification (Developer 1, parallel with SETUP-001)
- **Working Files:**
  - src/main/resources/application-db.properties (read-only)
- **Blocked by:** PREP-001
- **Hands-on:** Review file content; confirm no changes needed
- **Deliverable:** Written confirmation that config is correct

### TEST-001: Dry-Run Migration (Developer 1)
- **Working Files:**
  - docs/phase-3-testing.md (new file)
  - Gradle/Spring Boot shell commands
- **Blocked by:** SETUP-001, CONFIG-001
- **Hands-on:** Run app with migration enabled in dry-run mode
- **Deliverable:** Logs showing successful dry-run with 0 or acceptable errors

### MIGRATE-001: Live Migration (Developer 1)
- **Working Files:**
  - PostgreSQL database (writes)
  - Gradle/Spring Boot shell commands
- **Blocked by:** TEST-001
- **Hands-on:** Run app with live migration enabled
- **Deliverable:** Confirmed data in PostgreSQL tables (row counts verified via SQL)

### CUTOVER-001: Profile Switch (Developer 1)
- **Working Files:**
  - src/main/resources/application.properties (modify one line)
- **Blocked by:** MIGRATE-001
- **Hands-on:** Edit file, change profile from `csv` to `db`
- **Deliverable:** Commit with profile change

### VERIFY-001: Smoke Tests (Developer 1, parallel with CUTOVER-001)
- **Working Files:**
  - Gradle/Spring Boot shell commands
  - Browser for manual testing
  - Test suite (./gradlew test)
- **Blocked by:** MIGRATE-001 (but runs after CUTOVER-001 is committed)
- **Hands-on:** Start app, test sign-in flow, check scores, run test suite
- **Deliverable:** All smoke tests pass, test suite green

### DOCS-001: Documentation (Developer 1)
- **Working Files:**
  - docs/developer-guide.md (modify multiple sections)
  - docs/architecture/db-migration-blueprint.md (modify Phase 3 section)
  - docs/adr/0001-db-migration-readiness.md (update completion status)
- **Blocked by:** VERIFY-001
- **Hands-on:** Edit three files to mark Phase 3 complete
- **Deliverable:** All docs updated and consistent

---

## Timeline Estimate

| Phase | Duration | Cumulative |
|-------|----------|------------|
| Phase 1: PostgreSQL setup | 15–30 min | 15–30 min |
| Phase 2: Database + config | 10 min | 25–40 min |
| Phase 3a: Dry-run | 5–10 min | 30–50 min |
| Phase 3b: Live migration | 5–10 min | 35–60 min |
| Phase 4: Cutover + smoke test | 15–20 min | 50–80 min |
| Phase 5: Documentation | 20–30 min | 70–110 min |

**Total estimated time:** 70–110 minutes (1–2 hours for full cutover)

---

## Key Assumptions & Open Questions

### Assumptions
1. **PostgreSQL will be installed locally** on Windows developer machine (native install preferred over Docker)
2. **No schema or code changes needed** — Phase 2 has delivered complete Flyway migration and adapters
3. **Test suite will pass on both `csv` and `db` profiles** — Phase 2 parity tests already verify this
4. **CSV data files remain** — not deleted, CSV adapters remain active as fallback

### Open Questions
1. **PostgreSQL Installation:**
   - Is PostgreSQL currently installed on your Windows machine? (Check: `psql --version`)
   - If not installed, will you use the PostgreSQL installer from postgresql.org or Docker?

2. **Migration Errors:**
   - Are there any known data inconsistencies in CSV files that will generate migration errors?
   - Should migration-errors.csv be reviewed before proceeding to live migration?

3. **Rollback Plan:**
   - If live migration reveals issues, should we have a rollback procedure documented?
   - Should we take a PostgreSQL backup after successful migration?

4. **CSV Fallback Retention:**
   - After cutover, should CSV adapters be documented as "fallback only" or can they be removed later?
   - Should there be a documented procedure to revert to CSV profile if needed?

5. **Test Coverage:**
   - Should a dedicated integration test be created for the `db` profile, separate from the `csv` profile tests?
   - Or are existing parity tests (Testcontainers) sufficient?

6. **Docker Consideration:**
   - If PostgreSQL installation fails, should Phase 1 provide a Docker-based fallback with clear docker-compose steps?

---

## Success Criteria (Phase 3 Complete)

1. ✅ PostgreSQL is running and accessible on localhost:5432
2. ✅ Database `easyll` and user `easyll` exist with correct permissions
3. ✅ Dry-run migration executes without actual writes (validated via logs)
4. ✅ Live migration successfully writes all users, pairs, and scores to PostgreSQL
5. ✅ `spring.profiles.active=db` is set in application.properties (and checked in)
6. ✅ App starts successfully with `db` profile without errors
7. ✅ Sign-in, play, and score persistence work end-to-end
8. ✅ Full test suite passes (227 tests, 0 failures)
9. ✅ Documentation updated to reflect Phase 3 completion and PostgreSQL as primary
10. ✅ CSV adapters remain in codebase as fallback (not removed or disabled)

---

## Rollback & Contingency

### If PostgreSQL Setup Fails
- **Fallback:** Revert `spring.profiles.active` back to `csv` and use existing CSV adapters
- **Action:** Check PostgreSQL installation logs; refer to official PostgreSQL documentation
- **Docker Option:** If native install fails, set up PostgreSQL via Docker + docker-compose (see PREP-001)

### If Migration Errors Occur
- **Review:** Check `data/migration-errors.csv` for unresolved references
- **Validate:** Ensure all user IDs and pair IDs are correctly seeded
- **Decision:** If errors are data corruption (not expected), rollback live migration and review CSV files

### If App Fails to Start on `db` Profile
- **Debug:** Check PostgreSQL connection details in application-db.properties
- **Fallback:** Revert profile to `csv` and troubleshoot database connectivity
- **Check:** Verify Flyway migration ran successfully (check `flyway_schema_history` table)

---

## References

- [docs/developer-guide.md](docs/developer-guide.md) — Configuration & build instructions
- [docs/architecture/db-migration-blueprint.md](docs/architecture/db-migration-blueprint.md) — Overall migration design
- [docs/adr/0001-db-migration-readiness.md](docs/adr/0001-db-migration-readiness.md) — Migration readiness decision record
- [src/main/resources/application-db.properties](src/main/resources/application-db.properties) — PostgreSQL config
- [src/main/java/com/yodawife/easyll/migration/CsvToDbMigrationRunner.java](src/main/java/com/yodawife/easyll/migration/CsvToDbMigrationRunner.java) — Migration logic
