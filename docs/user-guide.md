# User Guide

Easy Language Learning is a vocabulary practice app that runs locally in your browser. You bring a word list; it handles the rest.

## Table of Contents

- [Running the app](#running-the-app)
- [Home page](#home-page)
- [Account and player selection](#account-and-player-selection)
- [Mobile support](#mobile-support)
- [Dictionary management](#dictionary-management)
- [Flashcards mode](#flashcards-mode)
- [Match mode](#match-mode)
- [Scoring](#scoring)
- [Dictionary storage](#dictionary-storage)
- [Data health and reload](#data-health-and-reload)
- [Known limitations](#known-limitations)

---

## Running the app

**Prerequisites:**

- Java 26 installed
- PostgreSQL running locally (default app profile is `db`)

Default local DB settings:

- database: `easyll`
- username: `easyll`
- password: `easyll`
- url: `jdbc:postgresql://localhost:5432/easyll`

If your local database uses different values, set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` before starting.

```sh
# Windows
gradlew.bat bootRun

# Linux / macOS
./gradlew bootRun
```

Open [http://localhost:8080](http://localhost:8080) in your browser.

---

## Home page

When you open the app you see the home page with:

1. **Language** — choose the active dictionary language.
2. **Mode** — choose **Flashcards** or **Match**, then click **Start**.
3. **Account status** in the top-right menu — shows either your signed-in player name or **Guest**.

> [!NOTE]
> If the word data is invalid or could not be loaded, the Start button is disabled. See [Data health and reload](#data-health-and-reload).

On mobile, the home layout is optimized for narrow screens: mode cards stack vertically and remain easy to tap.

---

## Account and player selection

Use the person icon in the top-right menu (next to the dictionary icon) to open the account panel.

### Sign in

1. Click the person icon.
2. Choose an existing player from the list, or type a new name in the input.
3. Submit to sign in.

If the typed name does not exist yet, the app creates a new player automatically and signs in.

### Sign out

1. Click the person icon.
2. Click **Sign out**.

### Guest mode vs signed-in mode

- **Guest mode**: all game features work without signing in; match results are available for the active session only.
- **Signed-in mode**: your match attempt history is persisted and available across browser sessions.

The active account stays selected across page reloads while the browser session is still active.

---

## Mobile support

The app supports responsive layouts with a primary breakpoint at `576px`.

- Compact mobile header with safe-area support for modern phones.
- Home uses a mobile-only house icon button for quick navigation.
- Flashcards expand to a touch-friendly full-width card.
- Match keeps two columns visible with compact spacing, language labels, sticky progress, and a tap hint.
- Dictionary switches to a card-style row layout for easier reading and editing.

---

## Dictionary management

Open `/dictionary` to manage the active language dictionary.

You can:

1. Use the unified toolbar to select language, search (with the search icon), set page size, and add a new entry.
2. Search by FROM, TO, or EXAMPLE text.
3. Sort by FROM or TO directly from table headers (ascending, descending, or unsorted).
4. Toggle a word globally and per mode using compact ON/OFF switches.
5. Edit a row using the actions column (pencil icon).
6. Browse with numbered pagination pills (with ellipsis for longer ranges).

When you are signed in, the dictionary table includes an additional **PROGRESS** column with your success percentage per word pair. In guest mode, this column is hidden.

When no rows are available, the page shows a dedicated empty state for either an empty dictionary or no search results.

Changes are persisted to the application data store and used by game modes immediately.

---

## Flashcards mode

1. Select **Flashcards** on the home page and click **Start**.
2. A card is displayed showing a word in your source language.
3. **Click the card** to flip it — the target-language translation and an optional usage example appear on the back.
4. Click **Next** to load a new random card.

Only words enabled for the selected language and `flashcards` mode are shown.

Flashcards is a pure practice mode — no scoring is tracked.

---

## Match mode

### How it works

1. Select **Match** on the home page (as Guest or signed in) and click **Start**.
2. A board with two columns is shown:
   - **Left column** — words in your source language.
   - **Right column** — shuffled words in the target language.
3. **Match words by drag-and-drop or tap interaction (mobile-friendly).**
   - Correct match: the pair flashes green and disappears from the board.
   - Incorrect match: the pair flashes red and returns to its original position.
4. Once all pairs on the board are matched, a new board is generated automatically.
5. The game ends when you reach **30 successful matches** total.

> [!TIP]
> Matching works in both directions — you can drag from either column.

Only words enabled for the selected language and `match` mode are used.

### Live counter

The page shows a running count of **successes** and **failures** for the current session. This resets when you start a new session.

---

## Scoring

### Per-session scoring

Every match attempt is counted. When you reach 30 successes the session ends and you see a result page:

| Success rate | Message |
| --- | --- |
| 100% | You did it! |
| 85–99% | Almost! |
| Below 85% | Let's practice some more! |

### Guest and signed-in scoring

- **Guest**: attempts affect only the live session counters and end-of-session result.
- **Signed-in**: each attempt is saved to persistent score history.

For signed-in users, the app records **S** (success) or **F** (failure) for every attempt, keeping the **last 12 entries per word pair**. This history powers long-term progress.

### Progress column

The dictionary **PROGRESS** column is visible only for signed-in users and shows the percentage of correct attempts based on the stored history window.

By default, score history is stored in the PostgreSQL database.

> [!NOTE]
> Playing as Guest still gives you the per-session result. Your attempts are simply not saved between sessions.

---

## Dictionary storage

Dictionary entries are stored in PostgreSQL.

- Base words are stored in `dictionary_pair`.
- Per-mode overrides are stored in `mode_eligibility`.
- Missing mode override means the word is enabled for that mode by default.

---

## Data health and reload

The app validates dictionary and score data access on startup and on every reload.

- **Healthy** — gameplay is enabled.
- **Degraded** — one or more data checks failed. A banner on the home page describes the problem.

### Health diagnostics page

Visit `/health/data` for a full list of parse errors with details.

### Reloading without a restart

If you need to refresh data after maintenance operations, you can reload without restarting:

1. Go to `/health/data`.
2. Click the **Reload data** button.

Or send the request manually:

```http
POST /admin/data/reload
```

> [!IMPORTANT]
> The reload endpoint requires admin credentials (HTTP Basic Auth). The default credentials are set in `application.properties` under `spring.security.user.name` and `spring.security.user.password`. Change them before running on a shared network.

---

## Known limitations

- Sessions are held in memory — restarting the app clears all active sessions.
- Gameplay account selection has no password-based authentication.
- Score history is capped to the last 12 attempts per word pair per user.
- PostgreSQL availability is required for dictionary and score persistence.
