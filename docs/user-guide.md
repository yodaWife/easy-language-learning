# User Guide

Easy Language Learning is a vocabulary practice app that runs locally in your browser. You bring a word list; it handles the rest.

## Table of Contents

- [Running the app](#running-the-app)
- [Home page](#home-page)
- [Dictionary management](#dictionary-management)
- [Flashcards mode](#flashcards-mode)
- [Match mode](#match-mode)
- [Scoring](#scoring)
- [Dictionary files](#dictionary-files)
- [Data health and reload](#data-health-and-reload)
- [Known limitations](#known-limitations)

---

## Running the app

**Prerequisite:** Java 26 installed.

```sh
# Windows
gradlew.bat bootRun

# Linux / macOS
./gradlew bootRun
```

Open [http://localhost:8080](http://localhost:8080) in your browser.

---

## Home page

When you open the app you see the home page with three inputs:

1. **Nickname (optional)** — type your name or choose from the list of known users. Providing a nickname enables per-user score tracking. You can play without one.
2. **Language** — choose the active dictionary language.
3. **Mode** — choose **Flashcards** or **Match**, then click **Start**.

> [!NOTE]
> If the word data is invalid or could not be loaded, the Start button is disabled. See [Data health and reload](#data-health-and-reload).

---

## Dictionary management

Open `/dictionary` to manage the active language dictionary.

You can:

1. Select language.
2. Search by FROM, TO, or EXAMPLE text.
3. Change page size and browse pages.
4. Sort by FROM or TO directly from table headers.
5. Toggle a word globally.
6. Toggle a word per mode (for example flashcards enabled, match disabled).

Changes are persisted to dictionary CSV files and used by game modes immediately.

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

1. Select **Match** on the home page (with or without a nickname) and click **Start**.
2. A board with two columns is shown:
   - **Left column** — words in your source language.
   - **Right column** — shuffled words in the target language.
3. **Drag a word from one column and drop it on its pair in the other column.**
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

### Per-user scoring

If you provided a nickname, each attempt is saved to a persistent score file. The app records **S** (success) or **F** (failure) for every attempt, keeping the **last 10 entries per word pair**. This history is used to track your long-term progress.

By default, score history is stored at `data/scores/scores.csv`.

> [!NOTE]
> Playing without a nickname still gives you the per-session result. Your attempts are simply not saved between sessions.

---

## Dictionary files

Dictionary data is loaded from folders under `data/dictionaries`.

Example layout:

```text
data/dictionaries/
   hun/
      words.csv
      mode-eligibility.csv
   pl/
      words.csv
      mode-eligibility.csv
```

### words.csv format

```text
WORD_ID;FROM;TO;EXAMPLE;GLOBAL_ENABLED
w1;dog;pies;The dog runs.;true
```

### mode-eligibility.csv format

```text
WORD_ID;MODE;ENABLED
w1;flashcards;true
w1;match;false
```

If a WORD_ID/MODE pair is missing in `mode-eligibility.csv`, it defaults to enabled.

---

## Data health and reload

The app validates both CSV files on startup and on every reload.

- **Healthy** — gameplay is enabled.
- **Degraded** — one or both files failed validation. A banner on the home page describes the problem.

### Health diagnostics page

Visit `/health/data` for a full list of parse errors with details.

### Reloading without a restart

If you edited a CSV file, you can reload it without restarting:

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
- No login or authentication for gameplay (nickname-only identity).
- Score history is capped to the last 10 attempts per word pair per user.
- Dictionary edits require file write permissions under `data/dictionaries`.
