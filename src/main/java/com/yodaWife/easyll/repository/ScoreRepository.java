package com.yodawife.easyll.repository;

import com.yodawife.easyll.domain.ScoreKey;
import com.yodawife.easyll.domain.UserWordHistory;
import com.yodawife.easyll.service.DataHealthService;
import com.yodawife.easyll.service.DataReloadedEvent;
import com.yodawife.easyll.service.UserScoreService;
import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Repository;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Repository
public class ScoreRepository implements ScoreReadRepository, ScoreWriteRepository {

    private static final Logger log = LoggerFactory.getLogger(ScoreRepository.class);

    private final DataHealthService dataHealthService;
    private final UserScoreService userScoreService;
    private final Path scorePath;

    // Mutable working copy of histories; lazily initialised from snapshot
    private @Nullable Map<ScoreKey, UserWordHistory> histories = null;

    // Tracks what was last loaded from a snapshot, to detect truly in-flight keys
    private @Nullable Map<ScoreKey, UserWordHistory> lastSnapshotHistories = null;

    public ScoreRepository(DataHealthService dataHealthService,
                           UserScoreService userScoreService,
                           @Value("${app.scores.write-path}") String scoreWritePath) {
        this.dataHealthService = dataHealthService;
        this.userScoreService = userScoreService;
        this.scorePath = Path.of(scoreWritePath);
    }

    @PostConstruct
    void validateWritePath() {
        Path parent = scorePath.getParent();

        if (parent != null && !Files.exists(parent)) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                var message = "Score write path parent directory could not be created: " + parent + " — " + e.getMessage();
                log.error(message, e);
                dataHealthService.reportScoreWritePathError(message);
                return;
            }
        }

        if (Files.exists(scorePath)) {
            if (!Files.isWritable(scorePath)) {
                var message = "Score write path exists but is not writable: " + scorePath;
                log.error(message);
                dataHealthService.reportScoreWritePathError(message);
                return;
            }
        } else {
            var effectiveParent = (parent != null) ? parent : Path.of(".");
            if (!Files.isWritable(effectiveParent)) {
                var message = "Score write path parent directory is not writable: " + effectiveParent;
                log.error(message);
                dataHealthService.reportScoreWritePathError(message);
                return;
            }
        }

        log.debug("Score write path validation passed: {}", scorePath);
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
                var snapshotHistories = snapshot.scoreData().histories();
                histories = new HashMap<>(snapshotHistories);
                lastSnapshotHistories = new HashMap<>(snapshotHistories);
            } else {
                histories = new HashMap<>();
                lastSnapshotHistories = new HashMap<>();
            }
        }
    }

    private synchronized Map<ScoreKey, UserWordHistory> histories() {
        ensureInitialised();
        return Objects.requireNonNull(histories);
    }

    /**
     * Append a match attempt result for the given user/word pair.
     *
     * @param userId user identifier (UUID string)
     * @param pairId word-pair identifier (dictionary word ID)
     * @param mode   game mode (e.g. "match", "flashcards")
     * @param result "S" or "F"
     */
    public synchronized void appendAttempt(String userId, String pairId, String mode, String result) {
        userScoreService.append(histories(), new ScoreKey(userId, pairId, mode), result);
    }

    /**
     * Atomically write all current histories to the score CSV file.
     * Uses temp-file + Files.move(ATOMIC_MOVE) to prevent corruption.
     */
    public synchronized void flush() {
        Map<ScoreKey, UserWordHistory> currentHistories = histories();
        Path targetPath = scorePath;
        Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");

        try {
            // Ensure parent directory exists
            Path parent = targetPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (BufferedWriter writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                for (Map.Entry<ScoreKey, UserWordHistory> entry : currentHistories.entrySet()) {
                    ScoreKey key = entry.getKey();
                    UserWordHistory history = entry.getValue();
                    writer.write(escape(key.userId()) + ";" +
                                 escape(key.pairId()) + ";" +
                                 escape(key.mode())   + ";" +
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

    /** Expose current known user IDs for lookup. */
    public synchronized Set<String> knownUsers() {
        var users = new TreeSet<String>();
        histories().keySet().forEach(k -> users.add(k.userId()));
        return Collections.unmodifiableSet(users);
    }

    /**
     * Return a map of pairId → history entries for the given user, across all modes.
     * The list for each pairId is aggregated from all modes (combined view).
     *
     * @param userId the user identifier
     * @return a map of pairId → list of S/F history entries (may be empty if no data for user)
     */
    public synchronized Map<String, List<String>> getHistoriesForUser(String userId) {
        Map<String, List<String>> result = new java.util.LinkedHashMap<>();
        for (var entry : histories().entrySet()) {
            if (entry.getKey().userId().equals(userId)) {
                String pairId = entry.getKey().pairId();
                // Merge entries from multiple modes for the same pairId
                result.merge(pairId, new java.util.ArrayList<>(entry.getValue().entries()),
                        (existing, incoming) -> {
                            existing.addAll(incoming);
                            return existing;
                        });
            }
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    /**
     * Merges the in-memory histories with the freshly reloaded snapshot when a reload event fires.
     * Keys whose in-memory state differs from the new snapshot (i.e. they carry unsaved in-flight
     * updates) are preserved; all other keys pick up the fresh snapshot values.
     *
     * <p>Edge cases:
     * <ul>
     *   <li>If {@code histories} is {@code null} (not yet initialised), initialises cleanly from
     *       the new snapshot.</li>
     *   <li>If the new snapshot is degraded ({@code scoreData == null}), leaves histories
     *       unchanged and logs a warning.</li>
     * </ul>
     *
     * @param event the reload event published by {@link DataHealthService}
     */
    @EventListener
    public synchronized void onDataReloaded(DataReloadedEvent event) {
        var snapshot = dataHealthService.snapshot();
        var scoreData = snapshot.scoreData();

        if (scoreData == null) {
            log.warn("Data reload signalled but snapshot is degraded (scoreData is null); "
                    + "leaving in-memory histories unchanged.");
            return;
        }

        var newSnapshotHistories = scoreData.histories();

        if (histories == null) {
            histories = new HashMap<>(newSnapshotHistories);
            lastSnapshotHistories = new HashMap<>(newSnapshotHistories);
            log.info("Data reload: histories initialised from new snapshot ({} keys).", histories.size());
            return;
        }

        var newHistories = new HashMap<>(newSnapshotHistories);
        int preservedCount = 0;

        for (var entry : histories.entrySet()) {
            var key = entry.getKey();
            var snapshotHistory = newSnapshotHistories.get(key);

            if (snapshotHistory == null) {
                // Key absent from new snapshot — preserve only if it is truly in-flight:
                // either it was never loaded from any snapshot, or it was modified since last load.
                var baseline = lastSnapshotHistories != null ? lastSnapshotHistories.get(key) : null;
                boolean neverPersisted = baseline == null;
                boolean modifiedSinceLoad = baseline != null
                        && !entry.getValue().entries().equals(baseline.entries());
                if (neverPersisted || modifiedSinceLoad) {
                    newHistories.put(key, entry.getValue());
                    preservedCount++;
                }
                // else: was in last snapshot and not modified — new snapshot removed it intentionally, drop it
            } else if (!entry.getValue().entries().equals(snapshotHistory.entries())) {
                // In-memory version differs from new snapshot — keep in-flight updates
                newHistories.put(key, entry.getValue());
                preservedCount++;
            }
        }

        lastSnapshotHistories = new HashMap<>(newSnapshotHistories);
        histories = newHistories;
        log.info("Data reload merged: {} in-flight key(s) preserved from pending state.", preservedCount);
    }
}
