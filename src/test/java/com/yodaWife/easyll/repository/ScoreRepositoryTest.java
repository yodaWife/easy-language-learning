package com.yodawife.easyll.repository;

import com.yodawife.easyll.service.DataHealthService;
import com.yodawife.easyll.service.UserScoreService;
import com.yodawife.easyll.validation.ScoreCsvParser;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScoreRepositoryTest {

    @TempDir
    @Nullable Path tempDir;

    private Path tempDir() {
        return Objects.requireNonNull(tempDir);
    }

    private ScoreRepository buildRepository(Path scoreFile) {
        // Mock DataHealthService to return a degraded snapshot (empty histories)
        DataHealthService mockHealth = mock(DataHealthService.class);
        var mockSnapshot = mock(com.yodawife.easyll.service.DataSnapshot.class);
        when(mockHealth.snapshot()).thenReturn(mockSnapshot);
        when(mockSnapshot.healthy()).thenReturn(false);

        UserScoreService userScoreService = new UserScoreService();

        ScoreCsvParser mockParser = mock(ScoreCsvParser.class);
        when(mockParser.getScoreFilePath()).thenReturn(scoreFile);

        return new ScoreRepository(mockHealth, userScoreService, mockParser);
    }

    @Test
    void flushWritesValidCsvFile() throws Exception {
        Path scoreFile = tempDir().resolve("scores.csv");
        ScoreRepository repo = buildRepository(scoreFile);

        repo.appendAttempt("alice", "Letter", "Betű", "S");
        repo.appendAttempt("alice", "Letter", "Betű", "F");
        repo.flush();

        assertThat(scoreFile).exists();
        List<String> lines = Files.readAllLines(scoreFile);
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst()).isEqualTo("alice;Letter;Betű;S,F");
    }

    @Test
    void flushOverwritesPreviousFile() throws Exception {
        Path scoreFile = tempDir().resolve("scores.csv");
        Files.writeString(scoreFile, "old;data;here;S\n");

        ScoreRepository repo = buildRepository(scoreFile);
        repo.appendAttempt("bob", "Stone", "Kő", "S");
        repo.flush();

        List<String> lines = Files.readAllLines(scoreFile);
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst()).startsWith("bob;Stone;Kő;S");
    }

    @Test
    void flushDoesNotCrashWhenDirectoryMissing() throws Exception {
        // Point to a file in a subdirectory that doesn't exist yet
        Path scoreFile = tempDir().resolve("subdir/scores.csv");
        ScoreRepository repo = buildRepository(scoreFile);

        repo.appendAttempt("alice", "A", "B", "S");
        // Should not throw — creates parent directory automatically
        repo.flush();

        assertThat(scoreFile).exists();
    }

    @Test
    void knownUsersReturnsAllDistinctUsers() {
        Path scoreFile = tempDir().resolve("scores.csv");
        ScoreRepository repo = buildRepository(scoreFile);

        repo.appendAttempt("alice", "A", "B", "S");
        repo.appendAttempt("bob",   "A", "B", "F");
        repo.appendAttempt("alice", "C", "D", "S");

        assertThat(repo.knownUsers()).containsExactly("alice", "bob"); // TreeSet → sorted
    }

    @Test
    void flushReportsRuntimeErrorWhenTargetPathIsDirectory() {
        DataHealthService mockHealth = mock(DataHealthService.class);
        var mockSnapshot = mock(com.yodawife.easyll.service.DataSnapshot.class);
        when(mockHealth.snapshot()).thenReturn(mockSnapshot);
        when(mockSnapshot.healthy()).thenReturn(false);

        UserScoreService userScoreService = new UserScoreService();

        ScoreCsvParser mockParser = mock(ScoreCsvParser.class);
        when(mockParser.getScoreFilePath()).thenReturn(tempDir());

        ScoreRepository repo = new ScoreRepository(mockHealth, userScoreService, mockParser);
        repo.appendAttempt("alice", "A", "B", "S");
        repo.flush();

        verify(mockHealth).reportRuntimeError(org.mockito.ArgumentMatchers.contains("Failed to flush score CSV"));
    }
}
