package com.yodawife.easyll.repository;

import com.yodawife.easyll.domain.ScoreDataBundle;
import com.yodawife.easyll.domain.ScoreKey;
import com.yodawife.easyll.domain.UserWordHistory;
import com.yodawife.easyll.service.DataHealthService;
import com.yodawife.easyll.service.DataReloadedEvent;
import com.yodawife.easyll.service.DataSnapshot;
import com.yodawife.easyll.service.UserScoreService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

        return new ScoreRepository(mockHealth, userScoreService, scoreFile.toString());
    }

    @Test
    void flushWritesValidCsvFile() throws Exception {
        Path scoreFile = tempDir().resolve("scores.csv");
        ScoreRepository repo = buildRepository(scoreFile);

        repo.appendAttempt("user-1", "pair-abc", "match", "S");
        repo.appendAttempt("user-1", "pair-abc", "match", "F");
        repo.flush();

        assertThat(scoreFile).exists();
        List<String> lines = Files.readAllLines(scoreFile);
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst()).isEqualTo("user-1;pair-abc;match;S,F");
    }

    @Test
    void flushOverwritesPreviousFile() throws Exception {
        Path scoreFile = tempDir().resolve("scores.csv");
        Files.writeString(scoreFile, "old;data;here;S\n");

        ScoreRepository repo = buildRepository(scoreFile);
        repo.appendAttempt("user-1", "pair-abc", "match", "S");
        repo.flush();

        List<String> lines = Files.readAllLines(scoreFile);
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst()).startsWith("user-1;pair-abc;match;S");
    }

    @Test
    void flushDoesNotCrashWhenDirectoryMissing() throws Exception {
        // Point to a file in a subdirectory that doesn't exist yet
        Path scoreFile = tempDir().resolve("subdir/scores.csv");
        ScoreRepository repo = buildRepository(scoreFile);

        repo.appendAttempt("user-1", "pair-abc", "match", "S");
        // Should not throw — creates parent directory automatically
        repo.flush();

        assertThat(scoreFile).exists();
    }

    @Test
    void knownUsersReturnsAllDistinctUsers() {
        Path scoreFile = tempDir().resolve("scores.csv");
        ScoreRepository repo = buildRepository(scoreFile);

        repo.appendAttempt("alice", "pair-abc", "match", "S");
        repo.appendAttempt("bob",   "pair-abc", "match", "F");
        repo.appendAttempt("alice", "pair-xyz", "match", "S");

        assertThat(repo.knownUsers()).containsExactly("alice", "bob"); // TreeSet → sorted
    }

    @Test
    void flushReportsRuntimeErrorWhenTargetPathIsDirectory() {
        DataHealthService mockHealth = mock(DataHealthService.class);
        var mockSnapshot = mock(DataSnapshot.class);
        when(mockHealth.snapshot()).thenReturn(mockSnapshot);
        when(mockSnapshot.healthy()).thenReturn(false);

        UserScoreService userScoreService = new UserScoreService();

        ScoreRepository repo = new ScoreRepository(mockHealth, userScoreService, tempDir().toString());
        repo.appendAttempt("user-1", "pair-abc", "match", "S");
        repo.flush();

        verify(mockHealth).reportRuntimeError(org.mockito.ArgumentMatchers.contains("Failed to flush score CSV"));
    }

    @Test
    @DisplayName("onDataReloaded drops keys absent from new snapshot and picks up new snapshot keys")
    void onDataReloadedDropsKeysAbsentFromNewSnapshot() {
        // Arrange - initial snapshot has "alice"
        var mockHealth = mock(DataHealthService.class);
        var snapshotBefore = mock(DataSnapshot.class);
        var scoreDataBefore = mock(ScoreDataBundle.class);
        when(snapshotBefore.healthy()).thenReturn(true);
        when(snapshotBefore.scoreData()).thenReturn(scoreDataBefore);
        var aliceKey = new ScoreKey("alice", "pair-abc", "match");
        when(scoreDataBefore.histories()).thenReturn(Map.of(aliceKey, new UserWordHistory(List.of("S"))));
        when(mockHealth.snapshot()).thenReturn(snapshotBefore);

        var repo = new ScoreRepository(mockHealth, new UserScoreService(), tempDir().resolve("scores.csv").toString());

        // Trigger initialisation with "alice"
        assertThat(repo.knownUsers()).containsExactly("alice");

        // Switch mock to return a new snapshot with "newuser" only (alice absent)
        var snapshotAfter = mock(DataSnapshot.class);
        var scoreDataAfter = mock(ScoreDataBundle.class);
        when(snapshotAfter.healthy()).thenReturn(true);
        when(snapshotAfter.scoreData()).thenReturn(scoreDataAfter);
        var newUserKey = new ScoreKey("newuser", "pair-xyz", "match");
        when(scoreDataAfter.histories()).thenReturn(Map.of(newUserKey, new UserWordHistory(List.of("F"))));
        when(mockHealth.snapshot()).thenReturn(snapshotAfter);

        // Act - fire the reload event
        repo.onDataReloaded(new DataReloadedEvent(this));

        // Assert - alice is dropped (absent from new snapshot, loaded from disk and not modified)
        // and newuser is picked up from the new snapshot
        assertThat(repo.knownUsers()).containsExactly("newuser");
        assertThat(repo.knownUsers()).doesNotContain("alice");
    }

    @Test
    @DisplayName("onDataReloaded preserves in-flight key absent from new snapshot")
    void onDataReloadedPreservesInFlightKeyAbsentFromNewSnapshot() {
        // Arrange - start with degraded snapshot (empty histories), add a key only via appendAttempt
        var mockHealth = mock(DataHealthService.class);
        var initialSnapshot = mock(DataSnapshot.class);
        when(initialSnapshot.healthy()).thenReturn(false);
        when(mockHealth.snapshot()).thenReturn(initialSnapshot);

        var repo = new ScoreRepository(mockHealth, new UserScoreService(), tempDir().resolve("scores.csv").toString());
        repo.knownUsers(); // trigger ensureInitialised → empty histories

        // In-flight entry for "charlie" — never in any snapshot
        repo.appendAttempt("charlie", "pair-abc", "match", "S");
        assertThat(repo.knownUsers()).containsExactly("charlie");

        // New snapshot also does NOT include charlie
        var reloadSnapshot = mock(DataSnapshot.class);
        var reloadScoreData = mock(ScoreDataBundle.class);
        when(reloadSnapshot.scoreData()).thenReturn(reloadScoreData);
        when(reloadScoreData.histories()).thenReturn(Map.of());
        when(mockHealth.snapshot()).thenReturn(reloadSnapshot);

        // Act
        repo.onDataReloaded(new DataReloadedEvent(this));

        // Assert - charlie's in-flight entry is preserved after reload
        assertThat(repo.knownUsers()).containsExactly("charlie");
    }

    @Test
    @DisplayName("onDataReloaded preserves in-flight key whose entries differ from the new snapshot")
    void onDataReloadedPreservesInFlightKey() throws Exception {
        // Arrange - initial snapshot has alice:[S]
        var mockHealth = mock(DataHealthService.class);
        var initialSnapshot = mock(DataSnapshot.class);
        var initialScoreData = mock(ScoreDataBundle.class);
        when(initialSnapshot.healthy()).thenReturn(true);
        when(initialSnapshot.scoreData()).thenReturn(initialScoreData);
        var aliceKey = new ScoreKey("alice", "pair-abc", "match");
        when(initialScoreData.histories()).thenReturn(Map.of(aliceKey, new UserWordHistory(List.of("S"))));
        when(mockHealth.snapshot()).thenReturn(initialSnapshot);

        var scoreFile = tempDir().resolve("scores.csv");
        var repo = new ScoreRepository(mockHealth, new UserScoreService(), scoreFile.toString());
        repo.knownUsers(); // trigger ensureInitialised → alice:[S]

        // In-flight update: append "F" for alice → alice:[S,F] in memory, not yet flushed
        repo.appendAttempt("alice", "pair-abc", "match", "F");

        // New snapshot still has alice:[S] (not yet reflecting the unflushed update)
        var reloadSnapshot = mock(DataSnapshot.class);
        var reloadScoreData = mock(ScoreDataBundle.class);
        when(reloadSnapshot.scoreData()).thenReturn(reloadScoreData);
        when(reloadScoreData.histories()).thenReturn(Map.of(aliceKey, new UserWordHistory(List.of("S"))));
        when(mockHealth.snapshot()).thenReturn(reloadSnapshot);

        // Act
        repo.onDataReloaded(new DataReloadedEvent(this));

        // Assert - alice's in-flight [S,F] is preserved (differs from snapshot [S])
        repo.flush();
        var lines = Files.readAllLines(scoreFile);
        assertThat(lines).anyMatch(line -> line.equals("alice;pair-abc;match;S,F"));
    }

    @Test
    @DisplayName("onDataReloaded picks up new key introduced by the new snapshot")
    void onDataReloadedPicksUpNewSnapshotKey() {
        // Arrange - start with empty in-memory (degraded initial state)
        var mockHealth = mock(DataHealthService.class);
        var initialSnapshot = mock(DataSnapshot.class);
        when(initialSnapshot.healthy()).thenReturn(false);
        when(mockHealth.snapshot()).thenReturn(initialSnapshot);

        var repo = new ScoreRepository(mockHealth, new UserScoreService(), tempDir().resolve("scores.csv").toString());
        repo.knownUsers(); // trigger ensureInitialised → empty histories
        assertThat(repo.knownUsers()).isEmpty();

        // New snapshot introduces bob:[F]
        var reloadSnapshot = mock(DataSnapshot.class);
        var reloadScoreData = mock(ScoreDataBundle.class);
        var bobKey = new ScoreKey("bob", "pair-abc", "match");
        when(reloadSnapshot.scoreData()).thenReturn(reloadScoreData);
        when(reloadScoreData.histories()).thenReturn(Map.of(bobKey, new UserWordHistory(List.of("F"))));
        when(mockHealth.snapshot()).thenReturn(reloadSnapshot);

        // Act
        repo.onDataReloaded(new DataReloadedEvent(this));

        // Assert - bob from the new snapshot is now available
        assertThat(repo.knownUsers()).containsExactly("bob");
    }

    @Test
    @DisplayName("onDataReloaded initialises from snapshot when histories is null")
    void onDataReloadedInitialisesFromSnapshotWhenHistoriesNull() {
        // Arrange - create repo but do NOT call any method that would trigger ensureInitialised
        var mockHealth = mock(DataHealthService.class);
        var snapshot = mock(DataSnapshot.class);
        var scoreData = mock(ScoreDataBundle.class);
        var aliceKey = new ScoreKey("alice", "pair-abc", "match");
        when(snapshot.scoreData()).thenReturn(scoreData);
        when(scoreData.histories()).thenReturn(Map.of(aliceKey, new UserWordHistory(List.of("S"))));
        when(mockHealth.snapshot()).thenReturn(snapshot);

        var repo = new ScoreRepository(mockHealth, new UserScoreService(), tempDir().resolve("scores.csv").toString());
        // histories is still null at this point (no method called yet)

        // Act
        repo.onDataReloaded(new DataReloadedEvent(this));

        // Assert - alice is available after clean init from snapshot
        assertThat(repo.knownUsers()).containsExactly("alice");
    }

    @Test
    @DisplayName("onDataReloaded leaves histories unchanged when new snapshot is degraded (scoreData is null)")
    void onDataReloadedLeavesHistoriesUnchangedWhenSnapshotDegraded() {
        // Arrange - init with alice
        var mockHealth = mock(DataHealthService.class);
        var initialSnapshot = mock(DataSnapshot.class);
        var initialScoreData = mock(ScoreDataBundle.class);
        when(initialSnapshot.healthy()).thenReturn(true);
        when(initialSnapshot.scoreData()).thenReturn(initialScoreData);
        var aliceKey = new ScoreKey("alice", "pair-abc", "match");
        when(initialScoreData.histories()).thenReturn(Map.of(aliceKey, new UserWordHistory(List.of("S"))));
        when(mockHealth.snapshot()).thenReturn(initialSnapshot);

        var repo = new ScoreRepository(mockHealth, new UserScoreService(), tempDir().resolve("scores.csv").toString());
        repo.knownUsers(); // trigger ensureInitialised

        // New snapshot is degraded (scoreData = null)
        var degradedSnapshot = mock(DataSnapshot.class);
        when(degradedSnapshot.scoreData()).thenReturn(null);
        when(mockHealth.snapshot()).thenReturn(degradedSnapshot);

        // Act
        repo.onDataReloaded(new DataReloadedEvent(this));

        // Assert - alice still present; histories were not modified
        assertThat(repo.knownUsers()).containsExactly("alice");
    }

    @Test
    @DisplayName("validateWritePath passes silently when score file exists and is writable")
    void validateWritePath_passesWhenFileIsWritable() throws Exception {
        var scoreFile = tempDir().resolve("scores.csv");
        Files.createFile(scoreFile);

        var mockHealth = mock(DataHealthService.class);
        var mockSnapshot = mock(DataSnapshot.class);
        when(mockHealth.snapshot()).thenReturn(mockSnapshot);
        when(mockSnapshot.healthy()).thenReturn(false);

        var repo = new ScoreRepository(mockHealth, new UserScoreService(), scoreFile.toString());

        repo.validateWritePath();

        verify(mockHealth, never()).reportRuntimeError(anyString());
    }

    @Test
    @DisplayName("validateWritePath reports error when score file exists but is not writable")
    void validateWritePath_reportsError_whenFileExistsButNotWritable() throws Exception {
        var scoreFile = tempDir().resolve("scores.csv");
        Files.createFile(scoreFile);
        scoreFile.toFile().setReadOnly();

        var mockHealth = mock(DataHealthService.class);
        var mockSnapshot = mock(DataSnapshot.class);
        when(mockHealth.snapshot()).thenReturn(mockSnapshot);
        when(mockSnapshot.healthy()).thenReturn(false);

        var repo = new ScoreRepository(mockHealth, new UserScoreService(), scoreFile.toString());

        repo.validateWritePath();

        verify(mockHealth).reportScoreWritePathError(contains("not writable"));
    }

    @Test
    @DisplayName("validateWritePath reports error when parent directory cannot be created")
    void validateWritePath_reportsError_whenParentDirectoryCreationFails() throws Exception {
        // Place a regular file at the path that would need to become a directory component,
        // so Files.createDirectories() throws because a non-directory blocks the path.
        var blockingFile = tempDir().resolve("blocking");
        Files.createFile(blockingFile);
        var scoreFile = blockingFile.resolve("subdir").resolve("scores.csv");

        var mockHealth = mock(DataHealthService.class);
        var mockSnapshot = mock(DataSnapshot.class);
        when(mockHealth.snapshot()).thenReturn(mockSnapshot);
        when(mockSnapshot.healthy()).thenReturn(false);

        var repo = new ScoreRepository(mockHealth, new UserScoreService(), scoreFile.toString());

        repo.validateWritePath();

        verify(mockHealth).reportScoreWritePathError(contains("could not be created"));
    }
}
