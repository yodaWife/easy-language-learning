package com.yodawife.easyll.repository;

import com.yodawife.easyll.domain.UserWordHistory;
import com.yodawife.easyll.domain.UserWordKey;
import com.yodawife.easyll.service.DataHealthService;
import com.yodawife.easyll.service.UserScoreService;
import com.yodawife.easyll.validation.ScoreCsvParser;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Repository
public class ScoreRepository {

    private static final Logger log = LoggerFactory.getLogger(ScoreRepository.class);

    private final DataHealthService dataHealthService;
    private final UserScoreService userScoreService;
    private final ScoreCsvParser scoreCsvParser;

    // Mutable working copy of histories; lazily initialised from snapshot
    private @Nullable Map<UserWordKey, UserWordHistory> histories = null;

    public ScoreRepository(DataHealthService dataHealthService,
                           UserScoreService userScoreService,
                           ScoreCsvParser scoreCsvParser) {
        this.dataHealthService = dataHealthService;
        this.userScoreService = userScoreService;
        this.scoreCsvParser = scoreCsvParser;
    }

    /**
     * Ensure in-memory histories are initialised from the latest healthy snapshot.
     * Called lazily so that it picks up a post-reload snapshot if the app was initially degraded.
     */
    private synchronized void ensureInitialised() {
        if (histories == null) {
            var snapshot = dataHealthService.snapshot();
            if (snapshot.healthy() && snapshot.scoreData() != null) {
                // Defensive mutable copy so we can modify it
                histories = new HashMap<>(snapshot.scoreData().histories());
            } else {
                histories = new HashMap<>();
            }
        }
    }

    private synchronized Map<UserWordKey, UserWordHistory> histories() {
        ensureInitialised();
        return Objects.requireNonNull(histories);
    }

    /**
     * Append a match attempt result for the given user/word pair.
     *
     * @param user     nickname (must not be null)
     * @param fromWord FROM word
     * @param toWord   TO word
     * @param result   "S" or "F"
     */
    public synchronized void appendAttempt(String user, String fromWord, String toWord, String result) {
        userScoreService.append(histories(), user, fromWord, toWord, result);
    }

    /**
     * Atomically write all current histories to the score CSV file.
     * Uses temp-file + Files.move(ATOMIC_MOVE) to prevent corruption.
     */
    public synchronized void flush() {
        Map<UserWordKey, UserWordHistory> currentHistories = histories();
        Path targetPath = scoreCsvParser.getScoreFilePath();
        Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");

        try {
            // Ensure parent directory exists
            Path parent = targetPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (BufferedWriter writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                for (Map.Entry<UserWordKey, UserWordHistory> entry : currentHistories.entrySet()) {
                    UserWordKey key = entry.getKey();
                    UserWordHistory history = entry.getValue();
                    writer.write(escape(key.user()) + ";" +
                                 escape(key.fromWord()) + ";" +
                                 escape(key.toWord()) + ";" +
                                 history.encoded());
                    writer.newLine();
                }
            }

            Files.move(tempPath, targetPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            log.info("Score CSV flushed successfully to {}", targetPath);

        } catch (IOException e) {
            log.error("Failed to flush score CSV to {}: {}", targetPath, e.getMessage(), e);
            dataHealthService.reportRuntimeError("Failed to flush score CSV: " + e.getMessage());
            // Clean up temp file if it was created
            try { Files.deleteIfExists(tempPath); } catch (IOException ignored) {}
        }
    }

    /** Escape a field value to avoid breaking semicolon-delimited format. */
    private String escape(String value) {
        return value == null ? "" : value.replace(";", "_");
    }

    /** Expose current known user nicknames for autocomplete. */
    public synchronized java.util.Set<String> knownUsers() {
        Map<UserWordKey, UserWordHistory> currentHistories = histories();
        var users = new java.util.TreeSet<String>();
        currentHistories.keySet().forEach(k -> users.add(k.user()));
        return java.util.Collections.unmodifiableSet(users);
    }
}
