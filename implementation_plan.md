# Implementation Plan: CSV Dictionary Upload Feature

## Summary
- **Total tasks:** 8
- **Parallel phases:** 3
- **Maximum parallelism per phase:** 2 tasks (Phase 1)

---

## Task File Scope

### TASK-001: Create CsvDictionaryParser Service
**Title:** Implement CSV parser with validation, normalization, and deduplication

**Modify:**
- (none)

**Create:**
- `src/main/java/com/yodawife/easyll/service/CsvDictionaryParser.java`

**Delete:**
- (none)

**Description:**
New standalone service class that:
- Validates CSV header (must be exactly `ENGLISH,HUNGARIAN,EXAMPLE`)
- Parses CSV rows (expects exactly 3 columns)
- Normalizes words: trim whitespace, capitalize first letter of ENGLISH and HUNGARIAN columns
- Deduplicates within uploaded file (keyed by normalized FROM+TO pair)
- Returns structured result with valid rows and rejection reasons
- Does NOT access the database; all logic is in-memory and deterministic
- Throws or returns clear error for invalid input format

**Acceptance Criteria:**
- Parser correctly rejects invalid headers
- Parser correctly rejects rows with missing ENGLISH or HUNGARIAN
- Parser correctly rejects rows with wrong column count
- Parser normalizes: trims whitespace and capitalizes first letter
- Parser deduplicates identical (FROM, TO) pairs within file, keeping first occurrence
- Parser returns both valid rows and detailed rejection reasons per invalid row

---

### TASK-002: Create CsvDictionaryParserTest Unit Tests
**Title:** Unit tests for CSV parser validation, normalization, and deduplication

**Modify:**
- (none)

**Create:**
- `src/test/java/com/yodawife/easyll/service/CsvDictionaryParserTest.java`

**Delete:**
- (none)

**Description:**
Comprehensive unit test class (Mockito, no database) covering:
- Valid CSV with standard rows → parsed correctly
- Valid CSV with mixed case and whitespace → normalized correctly
- Valid CSV with duplicate (FROM, TO) pairs → deduplicates to first occurrence
- Missing header → clear error
- Wrong header order → clear error
- Missing ENGLISH column → error per row with line number
- Missing HUNGARIAN column → error per row with line number
- Extra/missing columns → error per row with line number
- Empty EXAMPLE column → treated as empty string
- Whitespace-only EXAMPLE → treated as empty string
- Empty file → handled gracefully
- Empty lines → handled appropriately

**Acceptance Criteria:**
- All parser validation scenarios are covered
- Tests verify normalization (trimming, capitalization)
- Tests verify in-file deduplication logic
- Error messages include row context for diagnostics
- Tests are isolated (no database, no Spring context)

---

### TASK-003: Enhance DictionaryEditService with CSV Upload Method
**Title:** Add uploadCsvDictionary method to support bulk word import

**Modify:**
- `src/main/java/com/yodawife/easyll/service/DictionaryEditService.java`

**Create:**
- (none)

**Delete:**
- (none)

**Description:**
Add new public method `uploadCsvDictionary(String languageCode, String csvContent)` that:
- Uses CsvDictionaryParser to parse and validate CSV
- Returns DictionaryOperationResult with summary or failure
- If parsing fails, return Failure with user-visible error message
- If parsing succeeds:
  - Load current language bundle to identify existing words
  - For each valid row: check if (normalized FROM, normalized TO) already exists in bundle
  - Skip duplicates (count them separately)
  - For non-duplicates: generate new WordId (UUID), create Word with globalEnabled=true, insert via repository
  - Execute all inserts under DictionaryWriteLock for language to prevent race conditions
  - Reload dataHealthService cache after successful inserts
  - Audit log the bulk operation (language, imported count, skipped count)
  - Return Success with summary: imported count, skipped count
- Handle transaction failure atomically (no partial writes)
- Log operation with appropriate level (info for success, warn for validation failures)

**Acceptance Criteria:**
- Method accepts CSV content as string
- Invalid CSV is rejected with user-visible error, no database changes
- Valid rows are deduplicated against existing bundle
- New words are created with generated UUIDs and globalEnabled=true
- All inserts for a language use DictionaryWriteLock
- Transaction is atomic (failure rolls back all changes)
- Result summary includes imported and skipped counts
- Audit log entries are created for bulk imports
- No partial imports on failure

---

### TASK-004: Update DictionaryEditServiceTest with Upload Tests
**Title:** Add unit tests for CSV upload method in DictionaryEditService

**Modify:**
- `src/test/java/com/yodawife/easyll/service/DictionaryEditServiceTest.java`

**Create:**
- (none)

**Delete:**
- (none)

**Description:**
Extend existing Mockito unit test class with new test methods covering:
- Successful upload: valid CSV with new words → persisted with generated IDs
- Successful upload: valid CSV with some duplicates → only new words inserted, duplicates skipped
- Failed upload: invalid CSV header → Failure result, no database call
- Failed upload: invalid row format → Failure result with clear error message
- Failed upload: empty file → Failure result, no database call
- Partial duplicates: uploaded file has duplicate pairs within itself → deduplicated, kept first
- Database failure: repository throws exception → transaction rolled back, Failure result returned
- Lock timeout: DictionaryWriteLock times out → Failure result returned
- Audit logging: successful upload creates audit log entry with language, counts
- Language not found: specified language does not exist → Failure result
- New words get generated UUIDs: each inserted word has unique ID
- New words get globalEnabled=true: all inserted words have globalEnabled=true

**Acceptance Criteria:**
- All success and failure paths are tested
- Duplicate detection (both in-file and against existing) is verified
- Generated IDs are unique
- globalEnabled=true is set on all new words
- Audit logging is verified
- Lock and transaction handling is tested
- Mocks verify correct repository calls

---

### TASK-005: Add CSV Upload Endpoint to DictionaryController
**Title:** Add POST /dictionary/upload endpoint for CSV file upload

**Modify:**
- `src/main/java/com/yodawife/easyll/controller/DictionaryController.java`

**Create:**
- (none)

**Delete:**
- (none)

**Description:**
Add new POST endpoint method that:
- Path: `POST /dictionary/upload`
- Accepts multipart/form-data with:
  - `file` (required): MultipartFile with CSV content
  - `languageCode` (required): BCP-47 language code from form
- Validates:
  - File is not empty
  - File size is reasonable (suggest max 10MB based on requirements "typical files")
  - languageCode is not empty
- Reads CSV content from MultipartFile into String
- Calls DictionaryEditService.uploadCsvDictionary(languageCode, csvContent)
- Returns response:
  - Success (200 OK): redirect to /dictionary?languageCode=X with flash success message showing imported/skipped counts
  - Failure (400 Bad Request): redirect to /dictionary?languageCode=X with flash error message
- No authentication required (endpoint is under /dictionary/** which is permitAll)
- Logs upload attempt (language, filename, result)

**Acceptance Criteria:**
- Endpoint is accessible at POST /dictionary/upload
- Endpoint accepts multipart/form-data with file and languageCode
- CSV content is correctly passed to service
- Result is returned as redirect with flash message (not JSON)
- Success message shows imported and skipped counts
- Error message is user-visible
- No authentication is required
- File size is validated
- Empty file is rejected
- Missing languageCode is rejected

---

### TASK-006: Update DictionaryControllerTest with Upload Endpoint Tests
**Title:** Add integration tests for CSV upload endpoint

**Modify:**
- `src/test/java/com/yodawife/easyll/controller/DictionaryControllerTest.java`

**Create:**
- (none)

**Delete:**
- (none)

**Description:**
Extend existing Testcontainers + Spring Boot integration test class with new test methods covering:
- Anonymous user can upload valid CSV → receives success redirect with message
- Signed-in user can upload valid CSV → receives success redirect with message
- Upload with valid CSV → new words appear in subsequent dictionary query
- Upload with invalid CSV header → receives error redirect with error message
- Upload with missing ENGLISH column → receives error redirect with clear validation message
- Upload with missing languageCode parameter → 400 Bad Request or error message
- Upload with empty file → receives error message
- Upload with oversized file → receives error message
- Successful upload: counts in flash message match actual inserted rows
- Successful upload: imported words have correct normalization (capitalization, trimming)
- Successful upload: imported words are visible to all users (test with different user)
- Successful upload: imported words are eligible for all modes by default
- Successful upload: progress baseline for imported words is 0

**Acceptance Criteria:**
- All upload scenarios (success and failure) are tested
- Tests verify end-to-end flow: upload → persistence → visibility
- Anonymous and authenticated access are both tested
- Normalization is verified in database
- Mode eligibility and progress defaults are verified
- Error messages are user-visible and descriptive
- Tests use actual Testcontainers PostgreSQL

---

### TASK-007: Update Dictionary Template for Upload UI
**Title:** Add CSV upload control and result messaging to dictionary.html template

**Modify:**
- `src/main/resources/templates/dictionary.html`

**Create:**
- (none)

**Delete:**
- (none)

**Description:**
Modify Thymeleaf template to:
- Add upload form/button in toolbar area (near existing "+ Add" button)
  - File input: accept="text/csv" or `.csv` files
  - Hidden input: languageCode (populated from JavaScript, same as search context)
  - Submit button: "Upload CSV"
  - Form method: POST, action: "/dictionary/upload", enctype: "multipart/form-data"
- Add result banner area:
  - Display flash message with class `.banner-success` if upload succeeded (green background)
  - Display flash message with class `.banner-error` if upload failed (red background)
  - Include counts in success message: "Imported X words, skipped Y duplicates"
- Use Alpine.js or HTMX to:
  - Populate hidden languageCode from current language selector
  - Show/hide form on button click (or use native file input)
  - Optionally: disable submit while uploading
- Ensure upload result appears inline on the dictionary page (not page reload)

**Acceptance Criteria:**
- File input accepts CSV files
- Upload form is visible and accessible
- Hidden languageCode field is populated correctly from current language context
- Success message displays imported and skipped counts
- Error message displays validation or failure reason
- Banner styling is clear and consistent with existing design
- Upload form is functional on both anonymous and authenticated sessions
- Form submission shows upload result inline without full page reload

---

### TASK-008: Update CSS for Upload Result Styling
**Title:** Add banner styling for upload success and error messages

**Modify:**
- `src/main/resources/static/css/app.css`

**Create:**
- (none)

**Delete:**
- (none)

**Description:**
Add CSS classes to app.css:
- `.banner-success`: green background, dark text, padding, margin, border (matches or complements existing `.banner-error` and `.banner-warning`)
- Styling should match existing banner styles in terms of spacing, font, and visual hierarchy
- Ensure .banner-success is visually distinct from errors and warnings
- All styles should use consistent color scheme (suggest green like #28a745 or similar from Bootstrap convention)
- Add any additional utility classes if needed for upload-specific styling (e.g., result counts)

**Acceptance Criteria:**
- `.banner-success` class exists and is properly styled
- Success banners appear with appropriate green coloring
- Styling is consistent with existing `.banner-error` and `.banner-warning`
- Text is readable and properly padded
- Styling works in both light and dark modes (if applicable)

---

## Conflict Matrix

| Task | Conflicts with | Shared files |
|------|---|---|
| TASK-001 | (none) | — |
| TASK-002 | (none) | — |
| TASK-003 | TASK-004 | `src/main/java/com/yodawife/easyll/service/DictionaryEditService.java` |
| TASK-004 | TASK-003, TASK-005, TASK-006 | `src/main/java/com/yodawife/easyll/service/DictionaryEditService.java` (test coverage), DictionaryEditService behavior |
| TASK-005 | TASK-006 | `src/main/java/com/yodawife/easyll/controller/DictionaryController.java` |
| TASK-006 | TASK-005 | `src/main/java/com/yodawife/easyll/controller/DictionaryController.java` (indirectly via endpoint) |
| TASK-007 | (none) | — |
| TASK-008 | (none) | — |

**Notes:**
- TASK-003 and TASK-004 must be sequenced: service code first, then tests depend on the implementation.
- TASK-005 and TASK-006 must be sequenced: endpoint implementation first, then integration tests verify it.
- TASK-001 and TASK-002 have no conflicts; TASK-003+ depends on TASK-001 being complete (CsvDictionaryParser must exist).

---

## Execution Plan

### Phase 1 (parallel, no dependencies)
| Stream | Task ID | Title |
|--------|---------|-------|
| A | TASK-001 | Create CsvDictionaryParser Service |
| B | TASK-002 | Create CsvDictionaryParserTest Unit Tests |

**Parallel Safety:** Streams A and B create independent, new files with no file-level conflicts. Both can be developed and tested simultaneously.

---

### Phase 2 (parallel, depends on Phase 1)
| Stream | Task ID | Title |
|--------|---------|-------|
| A | TASK-003 | Enhance DictionaryEditService with CSV Upload Method |
| B | TASK-004 | Update DictionaryEditServiceTest with Upload Tests |

**Parallel Safety:** 
- Both tasks modify the same source file (DictionaryEditService), so they must coordinate.
- **Recommendation:** Stream A implements the service method first; Stream B writes tests immediately after. OR: Coordinate to avoid merge conflicts (e.g., Stream A adds the method, Stream B immediately adds corresponding tests in a single review cycle).
- Alternatively, if strict isolation is required: mark TASK-004 as sequential after TASK-003 (one developer implements service, second writes tests).
- For this implementation: **recommend sequential execution** (TASK-003 then TASK-004) to avoid conflicts on the same source file.

---

### Phase 3 (parallel, depends on Phase 2)
| Stream | Task ID | Title |
|--------|---------|-------|
| A | TASK-005 | Add CSV Upload Endpoint to DictionaryController |
| B | TASK-006 | Update DictionaryControllerTest with Upload Endpoint Tests |
| C | TASK-007 | Update Dictionary Template for Upload UI |
| D | TASK-008 | Update CSS for Upload Result Styling |

**Parallel Safety:**
- TASK-005 and TASK-006 share DictionaryController source file (recommend sequential: implement endpoint, then write tests).
- TASK-007 and TASK-008 have no file conflicts with TASK-005/006 or with each other (template and CSS are independent).
- **Recommendation for maximum parallelism:**
  - **Stream A+B (sequential):** TASK-005 (implement endpoint), then TASK-006 (write integration tests)
  - **Stream C (parallel):** TASK-007 (update template) — can start immediately after Phase 2 completes
  - **Stream D (parallel):** TASK-008 (update CSS) — can start immediately after Phase 2 completes
- Streams C and D can run in parallel with A+B (no shared files).

---

## Developer Assignment Guide

### Phase 1: Foundation (2 parallel developers)

**Developer 1 (TASK-001):**
- **Primary files:**
  - Create: `src/main/java/com/yodawife/easyll/service/CsvDictionaryParser.java`
- **Dependencies:** None
- **Blocking:** None
- **Unblocks:** TASK-003, TASK-004 (parser is used by both)

**Developer 2 (TASK-002):**
- **Primary files:**
  - Create: `src/test/java/com/yodawife/easyll/service/CsvDictionaryParserTest.java`
- **Dependencies:** TASK-001 (CsvDictionaryParser class exists)
- **Blocking:** None
- **Unblocks:** (testing only, no production code)

---

### Phase 2: Service Layer (2 sequential developers, or 1 developer both tasks)

**Developer 1 (TASK-003):**
- **Primary files:**
  - Modify: `src/main/java/com/yodawife/easyll/service/DictionaryEditService.java`
- **Dependencies:** 
  - TASK-001 (CsvDictionaryParser must exist)
  - Phase 1 complete
- **Blocking:** TASK-004
- **Unblocks:** TASK-005, TASK-006 (service method must exist before endpoint tests)

**Developer 2 (TASK-004):**
- **Primary files:**
  - Modify: `src/test/java/com/yodawife/easyll/service/DictionaryEditServiceTest.java`
- **Dependencies:**
  - TASK-003 (DictionaryEditService.uploadCsvDictionary must exist)
  - Phase 1 complete
- **Blocking:** TASK-005, TASK-006 (controller code will depend on verified service behavior)
- **Unblocks:** None (tests only)

---

### Phase 3: Presentation Layer (up to 4 parallel developers, with sequencing notes)

**Developer 1 (TASK-005):**
- **Primary files:**
  - Modify: `src/main/java/com/yodawife/easyll/controller/DictionaryController.java`
- **Dependencies:**
  - TASK-003 (DictionaryEditService.uploadCsvDictionary must exist)
  - Phase 2 complete
- **Blocking:** TASK-006
- **Unblocks:** TASK-006 (integration tests verify this endpoint)

**Developer 2 (TASK-006):**
- **Primary files:**
  - Modify: `src/test/java/com/yodawife/easyll/controller/DictionaryControllerTest.java`
- **Dependencies:**
  - TASK-005 (POST /dictionary/upload endpoint must exist)
  - Phase 2 complete
- **Blocking:** None (test verification of TASK-005)
- **Unblocks:** None

**Developer 3 (TASK-007):**
- **Primary files:**
  - Modify: `src/main/resources/templates/dictionary.html`
- **Dependencies:**
  - Phase 2 complete (for reference to service/controller behavior)
- **Blocking:** None (independent template changes)
- **Parallel with:** TASK-005, TASK-006, TASK-008 (no file conflicts)
- **Unblocks:** None

**Developer 4 (TASK-008):**
- **Primary files:**
  - Modify: `src/main/resources/static/css/app.css`
- **Dependencies:**
  - Phase 2 complete (optional, for context)
- **Blocking:** None (independent CSS changes)
- **Parallel with:** TASK-005, TASK-006, TASK-007 (no file conflicts)
- **Unblocks:** None

---

## Assumptions

1. **CsvDictionaryParser is stateless:** The parser is a utility service with no state; multiple concurrent calls are safe.
2. **Deduplication key:** Deduplication uses normalized (FROM, TO) pairs; EXAMPLE column is ignored for duplicates.
3. **Flash message support:** Thymeleaf templates and Spring session support flash attributes for post-redirect-get pattern.
4. **Multipart upload limit:** Assume application.properties already has a reasonable multipart size limit (e.g., 10MB); no new config required.
5. **CSS color convention:** Assume project uses Bootstrap-like color scheme (#28a745 for success, #dc3545 for error, #ffc107 for warning).
6. **No new database migration:** Progress baseline 0 is natural default (no score_progress row means 0); mode eligibility defaults to enabled (no mode_eligibility row means enabled=true).
7. **Anonymous and authenticated users:** Both access levels are already permitted at `/dictionary/**`; no security changes needed.
8. **Existing DictionaryWriteLock handles concurrency:** Lock is already used by other edit operations; bulk upload uses the same lock pattern.
9. **Audit logging interface is stable:** DictionaryAuditLogService supports batch/bulk operations; no changes required there.
10. **Template rendering:** HTMX or native form submission is acceptable; result appears inline (no full page reload after submit is nice-to-have, not required).

---

## Notes for Implementation Teams

### Code Quality & Consistency
- Follow existing code style: use `var` for local variables, use sealed interfaces for results, use records for immutable data.
- Apply NullAway annotations (JSpecify mode) to all new public methods.
- Error Prone checks should pass (no new warnings).
- Maintain 90%+ test coverage for new code.

### Error Handling
- All user-facing error messages should be clear and actionable (e.g., "Line 5: Missing HUNGARIAN column" rather than "Invalid CSV").
- Server-side validation of CSV format is mandatory; do not rely on client-side checks.
- Transaction atomicity: if any insert fails, roll back all inserts for that upload attempt.

### Testing Strategy
- **Unit tests** (TASK-002, TASK-004): Fast, mocked dependencies, deterministic.
- **Integration tests** (TASK-006): Use Testcontainers + PostgreSQL, test real database inserts, verify deduplication against existing words.
- **No end-to-end browser tests required** for this iteration.

### Logging
- Log successful uploads at INFO level: "Uploaded X words to language CODE: Y imported, Z skipped"
- Log validation failures at WARN level (malformed CSV, duplicates).
- Log database failures at ERROR level.
- Include language code, file name (if available), and counts in all log messages.

### Performance Considerations
- CSV parsing is O(rows) and performed in-memory; no database lookups during parse.
- Deduplication against existing bundle is O(rows * words), acceptable for typical dictionary sizes.
- All inserts for a language are batched under one lock acquisition; minimize lock hold time.
- No pagination or streaming required; assume typical CSV files are < 10K rows.

### Future Enhancements (Out of Scope)
- Support for other CSV column orders (e.g., allow reordering).
- Background/async import.
- Rollback or undo for uploaded batches.
- Import from URL or clipboard.
- Export dictionary to CSV.
