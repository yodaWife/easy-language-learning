# Easy Language Learning - MVP Development Plan

## 1. Goal
Build a local-first Spring Boot web application for language learning with two modes:
- Flashcards (practice only, no scoring)
- Match game (scored, 30 attempts per session)

The app uses CSV files for:
- Word database (bundled resource)
- User scoring history (persistent file)

## 2. Confirmed Product Decisions
- UI stack: server-rendered with Thymeleaf + HTMX/Alpine.js
- Persistence in MVP: CSV for words and scores
- Language data source: bundled in app resources
- MVP language support: single language pair
- Match interaction: drag card onto target slot
- Match direction rule: FROM is always left, TO is always right, but either side can be draggable
- Session rule: always exactly 30 attempts
- Attempt counting: every submitted match counts (correct or incorrect)
- Word selection in MVP: purely random
- Per-user history format: compact S/F comma-separated values
- Per-user history cap: FIFO max 10 results per (user, from, to)
- Score writes: end of session only
- Mid-session crash behavior: session data can be lost
- Duplicate scoring events: every success and failure is counted
- Duplicate word rows in CSV: validation error
- Validation failure behavior: app stays up, game features disabled, user can click Reload after fixing files
- Authentication in MVP: nickname only (no auth)
- MVP done target: functional local MVP with tests

## 3. Clarified Functional Requirements

### 3.1 Word CSV
Expected format:
- Header row is mandatory and always interpreted as metadata: FROM_LANG;TO_LANG;EXAMPLE
- Data rows represent word entries

Validation rules:
- Exactly 3 semicolon-separated columns per row
- FROM and TO must be non-empty
- EXAMPLE may be empty
- Duplicate (FROM, TO) pairs are forbidden

### 3.2 Welcome Flow
- User lands on home page
- User can optionally type/select nickname
- User chooses learning mode: Flashcards or Match
- If nickname empty: per-game scoring only
- If nickname present: per-game and per-user scoring enabled

### 3.3 Match Mode
- Display 3 rows x 2 columns each round
- FROM and TO headers use language names from CSV header
- Either side may be draggable depending on round setup
- User drags a card onto matching target slot
- Correct match: both cards flash green and disappear from current board
- Incorrect match: cards return to original positions and flash red
- Continue until exactly 30 attempts are made
- Words and ordering are random; repeats are allowed

### 3.4 Flashcards Mode
- Show one FROM word card at a time
- On click/flip show TO and EXAMPLE
- No scoring in this mode

### 3.5 Per-game Scoring
During match session track:
- successes
- failures

Final rate formula:
- successRate = successes / (successes + failures) * 100

Final message mapping:
- 100%: You did it!
- 85% to 99%: Almost!
- below 85%: Let’s practice some more!

### 3.6 Per-user Scoring
- Enabled only when nickname is provided
- For each (user, from, to), store FIFO history of last 10 attempts
- History values use S (success) and F (failure)
- Every attempt appends an event (including repeated same pair in same session)
- New pair creates history entry on first scored attempt

### 3.7 Score CSV
Expected structure per line:
- USER;FROM;TO;HISTORY

HISTORY encoding:
- Comma-separated S/F values, oldest first, max 10
- Example: S,F,F,S,S

Load behavior:
- Score CSV loaded on startup for nickname suggestions and persisted tracking

Write behavior:
- Session-level flush at end of match session

## 4. Proposed Technical Stack

### 4.1 Backend
- Java 26
- Spring Boot 4
- Spring MVC (controllers + thymeleaf views)
- Spring Validation for input constraints
- OpenCSV or Apache Commons CSV for robust CSV parsing/writing

### 4.2 Frontend
- Thymeleaf templates
- HTMX for partial page updates
- Alpine.js for lightweight state and drag/drop orchestration
- SortableJS for reliable drag-and-drop behavior (recommended)
- CSS with broad Unicode-supporting font stack for multilingual rendering

### 4.3 Testing
- JUnit 5 + Spring Boot Test
- MockMvc for web endpoint tests
- Unit tests for CSV parsing/validation/scoring/FIFO logic
- Optional: Playwright smoke tests later (post-MVP)

## 5. Architecture and Modules

Suggested package structure:
- com.yodaWife.easyll.config
- com.yodaWife.easyll.controller
- com.yodaWife.easyll.service
- com.yodaWife.easyll.domain
- com.yodaWife.easyll.repository
- com.yodaWife.easyll.validation
- com.yodaWife.easyll.view

Core components:
- LanguageDataService
  - Loads word CSV
  - Exposes language labels and validated word bank
- ScoreRepository (CSV-backed)
  - Loads/saves USER;FROM;TO;HISTORY
  - Atomic write with temp file + replace
- UserScoreService
  - FIFO append and retrieval
- MatchSessionService
  - Generates boards, validates attempts, tracks session stats
- FlashcardService
  - Random card retrieval
- DataHealthService
  - Tracks validation state and error details
  - Supports runtime reload after file fixes

## 6. Runtime Data Health and Reload Design

### 6.1 Behavior
- On startup, app validates word CSV and score CSV
- If invalid:
  - app remains running
  - home page displays clear error banner
  - game start actions are disabled
  - error details page available

### 6.2 Reload without restart
Add endpoint and UI action:
- POST /admin/data/reload
- Re-runs CSV load + validation
- On success, clears error state and re-enables game actions
- On failure, keeps app in degraded mode with updated diagnostics

Safety notes:
- Reload operation guarded by synchronization lock
- Ongoing requests read consistent snapshots

## 7. HTTP Endpoints and Page Map

Pages:
- GET /
  - Welcome, nickname select/input, mode selection, data health banner
- GET /flashcards
  - Flashcard page
- GET /match
  - Match game page
- GET /match/result
  - Session summary page
- GET /health/data
  - Human-readable data validation status

Actions/API:
- POST /session/start
  - Starts flashcards or match session
- POST /match/attempt
  - Submits a single match attempt
- POST /admin/data/reload
  - Reloads CSV data without restart

## 8. Data Models

Domain objects:
- WordEntry
  - fromWord, toWord, example
- LanguageMetadata
  - fromLanguageName, toLanguageName
- MatchAttempt
  - fromWord, toWord, success, timestamp
- MatchSession
  - sessionId, nickname(optional), attempts, successes, failures
- UserWordKey
  - user, fromWord, toWord
- UserWordHistory
  - key, deque of S/F max 10

## 9. Development Phases

### Phase 0 - Project Foundation
- Add web/template/static dependencies
- Add CSV library dependency
- Create package/module skeleton
- Add base layouts and error page

Exit criteria:
- App serves home page and static assets

### Phase 1 - CSV Loading and Validation
- Implement word CSV parser + validator
- Implement score CSV parser + validator
- Implement DataHealthService
- Show validation state on UI

Exit criteria:
- Valid files load successfully
- Invalid files produce actionable diagnostics and disable gameplay

### Phase 2 - Welcome Flow and Session Bootstrapping
- Nickname select/new input
- Mode selection and session creation
- In-memory session handling for MVP

Exit criteria:
- User can start flashcards or match with/without nickname

### Phase 3 - Flashcards Mode
- Random flashcard retrieval
- Card flip UI with optional example

Exit criteria:
- Flashcards work end-to-end with no scoring writes

### Phase 4 - Match Mode Core
- 3x2 board generation
- Drag/drop matching, success/failure feedback animation hooks
- 30-attempt completion logic

Exit criteria:
- Match mode completes exactly at 30 attempts with correct counters

### Phase 5 - Scoring and Persistence
- Per-game summary and message thresholds
- Per-user S/F FIFO updates
- End-of-session CSV flush

Exit criteria:
- Correct score summary and persisted histories

### Phase 6 - Reload and Hardening
- Implement reload endpoint and button
- Improve diagnostics and recovery UX
- Add defensive I/O and concurrency tests

Exit criteria:
- Broken CSV can be fixed and reloaded without service restart

### Phase 7 - Test Coverage and Polish
- Unit + integration test completion
- UI polish and multilingual font checks
- README operational documentation

Exit criteria:
- MVP acceptance test list passes

## 10. Suggested Dependencies (Gradle)
- org.springframework.boot:spring-boot-starter-web
- org.springframework.boot:spring-boot-starter-thymeleaf
- org.springframework.boot:spring-boot-starter-validation
- com.opencsv:opencsv (or org.apache.commons:commons-csv)
- org.springframework.boot:spring-boot-starter-test

Client-side (from WebJars or static assets):
- htmx
- alpinejs
- sortablejs

## 11. Testing Plan

Unit tests:
- CSV parsing success/failure cases
- Duplicate detection logic
- FIFO history trimming to 10
- Score formula and threshold messaging
- Match attempt accounting (all attempts count)

Integration tests:
- Home flow with valid data
- Degraded mode with invalid data
- Reload endpoint restores healthy state
- Match session ends at 30 attempts
- Score flush at session end

Negative tests:
- malformed CSV rows
- missing headers
- invalid history symbols
- file lock/write failure behavior

## 12. Risks and Mitigations

Risk: CSV corruption on write
- Mitigation: write to temp file then atomic move; backups optional

Risk: Runtime reload race conditions
- Mitigation: synchronized reload and immutable read snapshots

Risk: Drag/drop inconsistency across browsers
- Mitigation: use SortableJS and add compatibility smoke tests

Risk: Ambiguous multilingual rendering
- Mitigation: define broad font fallback stack and language sample test page

Risk: Session data loss on crash (accepted)
- Mitigation: explicitly documented MVP tradeoff

## 13. Open Issues to Track (Non-blocking for MVP)
- Transition path from CSV to database
- Weighted practice selection based on user mistakes
- Optional reset tools for user histories
- Multi-language pair support
- Authentication and user ownership of score history

## 14. MVP Definition of Done
MVP is done when:
- User can choose mode from welcome page
- Flashcards mode works with flips and optional examples
- Match mode supports drag/drop, visual feedback, and exactly 30 attempts
- Per-game scoring formula and result messages are correct
- Optional nickname enables per-user FIFO history tracking
- Score CSV is loaded on startup and saved at session end
- Invalid CSV state is visible, gameplay is disabled, and Reload restores app after file fixes
- Automated tests cover core domain rules and key endpoints

## 15. Immediate Implementation Backlog (ordered)
1. Add dependencies and baseline package structure
2. Implement CSV domain models and validators
3. Implement data health state + diagnostics page
4. Build home page and session start flow
5. Build flashcards page
6. Build match page with drag/drop and attempt submission
7. Implement scoring services and CSV write path
8. Add reload endpoint + UI action
9. Add test suite and finalize README runbook
