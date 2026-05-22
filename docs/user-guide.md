# User Guide

Easy Language Learning is a vocabulary practice app that runs locally in your browser. You bring a word list; it handles the rest.

## Table of Contents

- [Running the app](#running-the-app)
- [Home page](#home-page)
- [Flashcards mode](#flashcards-mode)
- [Match mode](#match-mode)
- [Scoring](#scoring)
- [Custom word lists](#custom-word-lists)
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

When you open the app you see the home page with two inputs:

1. **Nickname (optional)** — type your name or choose from the list of known users. Providing a nickname enables per-user score tracking. You can play without one.
2. **Mode** — choose **Flashcards** or **Match**, then click **Start**.

> [!NOTE]
> If the word data is invalid or could not be loaded, the Start button is disabled. See [Data health and reload](#data-health-and-reload).

---

## Flashcards mode

1. Select **Flashcards** on the home page and click **Start**.
2. A card is displayed showing a word in your source language.
3. **Click the card** to flip it — the target-language translation and an optional usage example appear on the back.
4. Click **Next** to load a new random card.

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

> [!NOTE]
> Playing without a nickname still gives you the per-session result. Your attempts are simply not saved between sessions.

---

## Custom word lists

By default the app ships with a built-in word list. You can supply your own.

### File format

Semicolon-delimited CSV, UTF-8 encoding:

```text
FROM_LANG;TO_LANG;EXAMPLE
Letter;Betű;
Stone;Kő;A stone is heavy.
```

| Column | Required | Notes |
| --- | --- | --- |
| Column 1 (FROM) | Yes | Word in your source language |
| Column 2 (TO) | Yes | Translation in the target language |
| Column 3 (EXAMPLE) | No | Usage example; leave blank but keep the `;` delimiter |

**Row 1** is the header row — its column names become the language labels displayed in the UI.

**Validation rules applied at load time:**
- Exactly 3 columns per row.
- FROM and TO cannot be blank.
- Duplicate (FROM, TO) pairs are rejected.

### Pointing the app at your file

Edit `src/main/resources/application.properties` and set:

```properties
app.words.source=file:/absolute/path/to/your/words.csv
```

Then either restart the app or use the reload button (see below) to pick up the change.

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
- The default word source is a bundled file; you need to update `app.words.source` to use a custom list.
